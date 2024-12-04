package com.jiminger.records;

import static com.jiminger.utils.Utils.isParentUri;
import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.utils.FileAccess;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;

public record FileRecord(URI uri, long size, String mime, long lastModifiedTime, String md5, Map<String, Object> additional) {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecord.class);

    public static final int DEFAULT_MAX_OPEN = 2;

    public static final long UNINITIALIZED = Long.MIN_VALUE;
    private static final ObjectMapper om = FileRecord.makeStandardObjectMapper();

    public FileRecord {
        if(uri == null)
            throw new NullPointerException("Cannot create a " + FileRecord.class.getSimpleName() + " without a uri.");
    }

    @JsonIgnore
    public boolean isComplete() {
        return !(size == UNINITIALIZED
            || lastModifiedTime == UNINITIALIZED
            || mime == null
            || md5 == null);
    }

    public Map<String, Object> copyAdditionalWith(final String key, final Object val) {
        return chain(new HashMap<>(Optional.ofNullable(additional).orElse(new HashMap<>())), m -> m.put(key, val));
    }

    public FileSpec fileSpec(final Vfs vfs) throws IOException {
        return new FileSpec(vfs.toPath(uri()));
    }

    public FileAccess fileAccess(final Vfs vfs, final boolean avoidMemMap) throws IOException {
        return new FileAccess(fileSpec(vfs), avoidMemMap);
    }

    public static Manager makeFileRecordsManager(final Vfs vfs, final String md5FileToWrite, final String[] md5FilesToRead) throws IOException {
        return makeFileRecordsManager(vfs, md5FileToWrite, md5FilesToRead, DEFAULT_MAX_OPEN);
    }

    public static Manager makeFileRecordsManager(final Vfs vfs, final String md5FileToWrite, final String[] md5FilesToRead, final int maxOpen)
        throws IOException {
        final var md5FileToWriteFile = new File(md5FileToWrite);
        final boolean md5FileToWriteExists = md5FileToWrite == null ? false : md5FileToWriteFile.exists();

        final String md5FileToWriteCopy;
        if(md5FileToWriteExists) {
            final var md5Path = vfs.toPath(uncheck(() -> md5FileToWriteFile.toURI()));
            try(var is = new BufferedInputStream(md5Path.read());) {
                final var tmpFilePath = Files.createTempFile("tmp-md5Write", ".txt", new FileAttribute[0]);
                final File tmpFile = tmpFilePath.toFile();
                tmpFile.deleteOnExit();

                System.out.println("Copying " + md5FileToWrite + " to " + tmpFile.getAbsolutePath());
                try(var os = new BufferedOutputStream(new FileOutputStream(tmpFile));) {
                    IOUtils.copy(is, os);
                }
                md5FileToWriteCopy = tmpFile.getAbsolutePath();
            }
        } else
            md5FileToWriteCopy = md5FileToWrite;

        return new Manager(vfs,
            (md5FileToWriteExists
                ? Stream.concat(Stream.of(md5FileToWriteCopy), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
                : Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))),
            maxOpen

        );
    }

    public static class Manager implements Closeable {

        private final List<IndexedFile> indexedFiles;
        private final LinkedList<FileAccess> faDeque = new LinkedList<>();

        private Manager(final Vfs vfs, final Stream<String> fileNames, final int maxOpen) throws IOException {
            final List<IndexedFile> idxFiles = fileNames
                .map(fileName -> new File(fileName))
                .peek(f -> {
                    if(!f.exists()) {
                        LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", f);
                        throw new IllegalArgumentException("The file \"" + f + "\" doesn't exist. Can't load specs from it. Please update the config.");
                    }
                })
                .peek(file -> LOGGER.info("Mapping {} ...", file))
                .map(file -> file.toURI())
                .map(u -> new IndexedFile(u, vfs))
                .collect(Collectors.toList());

            indexedFiles = new ArrayList<>();
            long count = 0;
            for(final var idxFile: idxFiles) {
                count += idxFile.load(this, count);
                indexedFiles.add(idxFile);
            }
        }

        @Override
        public void close() throws IOException {
            final List<Exception> e = new ArrayList<>();

            faDeque.forEach(i -> {
                try {
                    i.close();
                } catch(IOException | RuntimeException e1) {
                    e.add(e1);
                }
            });

            if(e.size() > 0) {
                final var toThrow = e.get(0);

                for(int i = 1; i < e.size(); i++)
                    toThrow.addSuppressed(e.get(i));

                if(toThrow instanceof IOException)
                    throw(IOException)toThrow;
                else
                    throw(RuntimeException)toThrow;
            }
        }

        public Pair<Index, FileRecord> findIndexAndRecord(final URI uri) throws IOException {
            final Index entry = indexedFiles.stream()
                .map(i -> i.get(uri))
                .filter(i -> i != null)
                .findAny()
                .orElse(null);

            if(entry == null) {
                return null; // Key not found
            }

            IndexedFile toUse = null;
            for(final Iterator<FileAccess> iter = faDeque.iterator(); iter.hasNext();) {
                final var curFa = iter.next();
                if(curFa == entry.idxFile().curX) { // this is the one
                    iter.remove();
                    faDeque.addFirst(curFa);
                    toUse = entry.idxFile();
                    break;
                }
            }

            if(toUse == null) { // then we have nothing cached.
                faDeque.addFirst(entry.idxFile.with());
                if(faDeque.size() > 2) {
                    faDeque.removeLast().close();
                }
            }

            return Pair.of(entry, getMyRecordFromIndexEntry(entry));

        }

        public FileRecord find(final URI uri) throws IOException {
            return Optional.ofNullable(findIndexAndRecord(uri)).map(p -> p.getRight()).orElse(null);
        }

        public boolean remove(final URI uri) {
            final var idx = indexedFiles.stream()
                .map(i -> i.get(uri))
                .filter(i -> i != null)
                .findAny()
                .orElse(null);
            if(idx == null)
                return false;
            return idx.idxFile.remove(uri);
        }

        public Stream<FileRecord> stream() {
            return indexedFiles.stream()
                .flatMap(idxFile -> {
                    // open the fa
                    final var fa = idxFile.with();

                    return idxFile.offsets.values().stream()
                        .mapToLong(l -> l)
                        .mapToObj(off -> new Index(idxFile, off))
                        // close the fa
                        .onClose(() -> uncheck(() -> fa.close()));
                })
                .map(index -> uncheck(() -> getMyRecordFromIndexEntry(index)))

            ;
        }

        public long size() {
            return indexedFiles.stream()
                .mapToLong(i -> i.size())
                .sum();
        }

        private static Index validateAndSelect(final Index existingEntry, final FileRecord existingRecord, final Index newEntry, final FileRecord newRecord) {
            if(!existingRecord.md5().equals(newRecord.md5()))
                throw new IllegalStateException(
                    "Entry " + existingRecord + " from " + existingEntry + " has a different md5 than " + newRecord + " from " + newEntry);

            if(ImageDetails.hasHistogram(existingRecord))
                return existingEntry;
            else
                return newEntry;
        }

        private static record FileLine(String line, long nextLineOffset) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(Include.NON_EMPTY)
        private static record Minimal(URI uri, String md5) {}

        private static URI getKeyFromJsonLine(final String line) {
            final var m = uncheck(() -> om.readValue(line, Minimal.class));
            return m.uri();
        }

        private static FileRecord parseLine(final String line) {
            return uncheck(() -> om.readValue(line, FileRecord.class));
        }

        private static FileRecord getMyRecordFromIndexEntry(final Index entry) throws IOException {
            try(var fa = entry.idxFile.with();) {
                final MegaByteBuffer megaBuffer = fa.mapFile();
                final long offset = entry.offset();

                final FileLine fileLine = readLineFromMegaBuffer(megaBuffer, offset);
                if(fileLine == null) {
                    // Handle error appropriately, perhaps throw an exception or return null
                    return null;
                }

                return parseLine(fileLine.line());
            }
        }

        private static FileLine readLineFromMegaBuffer(final MegaByteBuffer megaBuffer, final long offset) {
            final long fileSize = megaBuffer.capacity();

            if(offset < 0 || offset >= fileSize) {
                return null; // Invalid offset
            }

            final ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();

            long position = offset;
            while(position < fileSize) {
                final byte b = megaBuffer.get(position);

                if(b == '\n') {
                    position++; // Move past the newline character
                    break; // LF detected
                } else if(b == '\r') {
                    position++; // Move past the carriage return
                    // Check for CRLF
                    if(position < fileSize && megaBuffer.get(position) == '\n') {
                        position++; // Move past the newline character
                    }
                    break; // CR or CRLF detected
                } else {
                    lineBytes.write(b);
                    position++;
                }
            }

            final String line = new String(lineBytes.toByteArray(), StandardCharsets.UTF_8);
            return new FileLine(line, position);
        }

        private static record Index(IndexedFile idxFile, long offset) {}

        private static class IndexedFile {
            public final URI uri;
            public final Vfs vfs;
            private FileAccess curX;
            private int openCount = 0;
            public final Map<URI, Long> offsets;

            public IndexedFile(final URI uri, final Vfs vfs) {
                this.uri = uri;
                this.vfs = vfs;
                offsets = new HashMap<>();
                curX = null;
            }

            public Index get(final URI uri) {
                return Optional.ofNullable(offsets.get(uri)).map(l -> new Index(this, l.longValue())).orElse(null);
            }

            public boolean remove(final URI key) {
                return offsets.remove(key) != null;
            }

            public long size() {
                return offsets.size();
            }

            public FileAccess with() {
                openCount++;
                if(curX == null) {
                    curX = new FileAccess(new FileSpec(uncheck(() -> vfs.toPath(uri))), false) {
                        MegaByteBuffer curMbb = null;

                        @Override
                        public MegaByteBuffer mapFile() throws IOException {
                            if(curMbb == null)
                                curMbb = super.mapFile();
                            return curMbb;
                        }

                        @Override
                        public void close() throws IOException {
                            openCount--;
                            if(openCount <= 0) {
                                openCount = 0;
                                curX = null;
                                super.close();
                            }
                        }
                    };
                }
                return curX;
            }

            public long load(final Manager others, long count) throws IOException {
                try(final var fa = with();) {
                    final MegaByteBuffer megaBuffer = curX.mapFile();
                    final long fileSize = megaBuffer.capacity();
                    long offset = 0L;

                    while(offset < fileSize) {
                        final FileLine fileLine = readLineFromMegaBuffer(megaBuffer, offset);

                        if(fileLine == null || fileLine.line().isEmpty()) {
                            offset = (fileLine != null) ? fileLine.nextLineOffset() : fileSize;
                            continue; // Skip empty lines or end if null
                        }

                        final String line = fileLine.line();
                        final long nextLineOffset = fileLine.nextLineOffset();

                        // Extract key from the JSON line
                        final var key = getKeyFromJsonLine(line);

                        if(key != null) {
                            count++;

                            final Pair<Index, FileRecord> existing;
                            {
                                final var tmp = this.get(key);
                                if(tmp != null)
                                    existing = Pair.of(tmp, getMyRecordFromIndexEntry(tmp));
                                else
                                    existing = others.findIndexAndRecord(key);
                            }

                            final Index newEntry = new Index(this, offset);

                            if(existing != null) {
                                // Key collision detected
                                // Read MyRecord for both entries
                                final FileRecord existingRecord = existing.getRight();
                                final FileRecord newRecord = parseLine(line);

                                // Use validateAndSelect to decide which entry to keep
                                final Index selectedEntry = validateAndSelect(existing.getLeft(), existingRecord, newEntry, newRecord);

                                if(selectedEntry == newEntry) {
                                    // need to remove first since THIS might actually be the selectedEntry.idxFile
                                    existing.getLeft().idxFile.remove(key);
                                    offsets.put(key, offset);
                                } // otherwise we just don't replace the previous one.
                            } else
                                offsets.put(key, offset);

                        }

                        offset = nextLineOffset;
                    }
                }
                return count;
            }
        }
    }

    public static Stream<FileRecord> readFileRecordsAsStream(final String[] fileNames) {
        final Closer c = new Closer();

        return Arrays.stream(fileNames)
            .map(fileName -> new File(fileName))
            .peek(f -> {
                if(!f.exists()) {
                    LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", f);
                    throw new IllegalArgumentException("The file \"" + f + "\" doesn't exist. Can't load specs from it. Please update the config.");
                }
            })
            .peek(file -> LOGGER.info("Reading {} ...", file))
            .map(file -> new BufferedReader(uncheck(() -> new FileReader(file))))
            .peek(br -> c.add(br))
            .flatMap(br -> br.lines())
            .map(l -> fix(uncheck(() -> om.readValue(l, FileRecord.class))))
            .onClose(() -> c.close());
    }

    public static Stream<FileRecord> readFileRecordsAsStream(final String md5FileToWrite, final String[] md5FilesToRead) {
        final boolean md5FileToWriteExists = md5FileToWrite == null ? false : new File(md5FileToWrite).exists();

        return readFileRecordsAsStream(
            (md5FileToWriteExists
                ? Stream.concat(Stream.of(md5FileToWrite), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
                : Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
                    .toArray(String[]::new));
    }

//    public static List<FileRecord> readFileRecords(final String[] fileNames, final Predicate<FileRecord> filter) throws IOException {
//
//        final List<FileRecord> ret = new ArrayList<>();
//        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
//            final File file = new File(fileName);
//            if(file.exists()) {
//                LOGGER.info("Reading {} ...", file);
//                try(BufferedReader br = new BufferedReader(new FileReader(file));) {
//                    for(String line = br.readLine(); line != null; line = br.readLine()) {
//                        final var fr = fix(om.readValue(line, FileRecord.class));
//                        if(filter == null || filter.test(fr))
//                            ret.add(fr);
//                    }
//                }
//            } else {
//                LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", fileName);
//                throw new IllegalArgumentException("The file \"" + fileName + "\" doesn't exist. Can't load specs from it. Please update the config.");
//            }
//        })));
//        return ret;
//    }
//
//    public static List<FileRecord> readFileRecords(final String[] fileNames) throws IOException {
//        return readFileRecords(fileNames, null);
//    }
//
//    public static List<FileRecord> readFileRecords(final String md5FileToWrite, final String[] md5FilesToRead, final Predicate<FileRecord> filter)
//        throws IOException {
//        final boolean md5FileToWriteExists = md5FileToWrite == null ? false : new File(md5FileToWrite).exists();
//
//        return readFileRecords(
//            (md5FileToWriteExists
//                ? Stream.concat(Stream.of(md5FileToWrite), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
//                : Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
//                    .toArray(String[]::new),
//
//            filter
//
//        );
//    }
//
//    public static List<FileRecord> readFileRecords(final String md5FileToWrite, final String[] md5FilesToRead) throws IOException {
//        return readFileRecords(md5FileToWrite, md5FilesToRead, null);
//    }
//
//    public static Map<URI, FileRecord> mergedFileRecords(final String md5FileToWrite, final String[] md5FilesToRead, final Predicate<FileRecord> filter)
//        throws IOException {
//
//        return readFileRecords(md5FileToWrite, md5FilesToRead, filter).stream()
//            .collect(Collectors.toMap(fs -> fs.uri(), fs -> fs, (fr1, fr2) -> {
//                // if(fr1.equals(fr2))
//                if(fr1.md5().equals(fr2.md5()))
//                    return fr2.additional() != null ? fr2 : fr1;
//                throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
//            }));
//
//    }
//
//    public static Map<URI, FileRecord> mergedFileRecords(final String md5FileToWrite, final String[] md5FilesToRead) throws IOException {
//        return mergedFileRecords(md5FileToWrite, md5FilesToRead, null);
//    }

    public static Map<String, List<FileRecord>> groupByMd5(final Collection<FileRecord> toGroup) {
        final Map<String, List<FileRecord>> ret = new HashMap<>((int)Math.ceil(toGroup.size() / 0.74));
        toGroup.forEach(fr -> ret.compute(fr.md5(), (k, v) -> {
            final List<FileRecord> cur = (v == null) ? new ArrayList<>() : v;
            cur.add(fr);
            return cur;
        }));
        return ret;
    }

    public static List<FileRecord> childrenOf(final URI parentURI, final Manager among) {
        return among.stream()
            .filter(cur -> isParentUri(parentURI, cur.uri()))
            .collect(Collectors.toList());
    }

    public static ObjectMapper makeStandardObjectMapper() {
        ObjectMapper objectMapper;

        objectMapper = JsonUtils.makeStandardObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return objectMapper;
    }

    private static FileRecord fix(final FileRecord fr) {
        if(fr.additional != null)
            return ImageDetails.fix(fr);
        return fr;
    }
}
