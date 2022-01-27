package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.HexStringUtil.bytesToHex;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.util.Functional;
import net.dempsy.vfs.Bz2FileSystem;
import net.dempsy.vfs.GzFileSystem;
import net.dempsy.vfs.TarFileSystem;
import net.dempsy.vfs.Vfs;
import net.dempsy.vfs.XzFileSystem;
import net.dempsy.vfs.ZCompressedFileSystem;
import net.dempsy.vfs.ZipFileSystem;

public class Md5File {
    public static Logger LOGGER = LoggerFactory.getLogger(Md5File.class);

    public static Vfs vfs = uncheck(() -> getVfs());

    public static Vfs getVfs() throws IOException {
        return new Vfs(
            new TarFileSystem(),
            new GzFileSystem(),
            new ZipFileSystem(),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem()

        );
    }

    public static List<FileRecord> readFileRecords(final String... fileNames) throws IOException {

        final ObjectMapper om = JsonUtils.makeStandardObjectMapper();

        final List<FileRecord> ret = new ArrayList<>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            final File file = new File(fileName);
            if(file.exists()) {
                try(BufferedReader br = new BufferedReader(new FileReader(file));) {
                    for(String line = br.readLine(); line != null; line = br.readLine())
                        ret.add(om.readValue(line, FileRecord.class));
                }
            } else {
                LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", fileName);
            }
        })));
        return ret;
    }

    /**
     * Read an md5 file and return a lookup of the md5 code by filename path.
     */
    public static Map<String, String> readMd5FileLookup(final String... fileNames) throws IOException {

        final Map<String, String> file2Md5 = new HashMap<String, String>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            if(fileName != null) {
                final File file = new File(fileName);
                if(file.exists()) {
                    try(BufferedReader br = new BufferedReader(new FileReader(file));) {
                        for(String line = br.readLine(); line != null; line = br.readLine()) {
                            final String[] entry = line.split("\\|\\|");
                            if(entry.length != 2)
                                throw new RuntimeException("An md5 file entry must have 2 values separated by a \"||\". The file " + fileName +
                                    " appears to have an entry of the form:" + line);
                            file2Md5.put(entry[1], entry[0]);
                        }
                    }
                }
            }
        })));
        return file2Md5.isEmpty() ? null : file2Md5;
    }

    /**
     * Read and md5 file and return a lookup by md5 hash code to the list of files that hash
     * to that code.
     */
    public static Map<String, List<String>> readMd5File(final String... fileNames) throws IOException {
        final Map<String, List<String>> md5map = new HashMap<>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            final File file = new File(fileName);
            if(!file.exists())
                throw new FileNotFoundException("MD5 file " + fileName + " doesn't exist.");

            try(BufferedReader br = new BufferedReader(new FileReader(file));) {
                for(String line = br.readLine(); line != null; line = br.readLine()) {
                    final String[] entry = line.split("\\|\\|");
                    if(entry.length != 2)
                        throw new RuntimeException("An md5 file entry must have 2 values separated by a \"||\". The file " + fileName +
                            " appears to have an entry of the form:" + line);
                    final String key = entry[0];
                    List<String> filesWithMd5 = md5map.get(key);
                    if(filesWithMd5 == null) {
                        filesWithMd5 = new ArrayList<>(2);
                        md5map.put(key, filesWithMd5);
                    }
                    filesWithMd5.add(entry[1]);
                }
            }
        })));
        return md5map;
    }

    public static void makeMd5File(final String md5FileToWrite, final String[] md5FilesToRead, final FileSpec[] directoriesToScan,
        final String failedFile, final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered) throws IOException {
        final Map<String, FileRecord> file2FileRecords = readFileRecords(Stream.concat(
            Stream.of(md5FileToWrite),
            Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new))
                .stream()
                .collect(Collectors.toMap(fs -> fs.path, fs -> fs));

        final File md5File = new File(md5FileToWrite);
        try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile)))
            : new PrintWriter(System.err);
            PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

            final long startTime = System.currentTimeMillis();
            // pass to calc md5
            recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck(() -> {
                doMd5(md5os, d, file2FileRecords, deleteEmtyDirs, md5FileFilter, lineBuffered);
            })));

            System.out.println("Finished Clean: " + (System.currentTimeMillis() - startTime) + " millis");
        }
    }

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getSimpleName() + " path/to/config.json");
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);
            System.out.println((c.avoidMemMap ? "" : "NOT ") + "avoiding memory mapping.");
            MD5.avoidMemMap(c.avoidMemMap);
            makeMd5File(c.md5FileToWrite, c.md5FilesToRead,

                Arrays.stream(c.directoriesToScan)
                    .map(fr -> fr.uri())
                    .map(u -> uncheck(() -> vfs.toPath(u)))
                    .map(p -> new FileSpec(p))
                    .toArray(FileSpec[]::new)

                , c.failedFile, c.deleteEmptyDirs, c.md5FileFilter(),
                c.md5FileWriteLineBuffered);
        }
    }

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    private static void doMd5(final PrintWriter md5os, final FileSpec fSpec, final Map<String, FileRecord> existing, final boolean deleteEmtyDirs,
        final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered) throws IOException {

        final BasicFileAttributes attr = fSpec.getAttr();
        if(attr != null && attr.isSymbolicLink()) {
            System.out.println("SKIPPING: Symbolic link: " + fSpec.uri());
            return;
        }

        if(!fSpec.exists())
            throw new FileNotFoundException("File " + fSpec + " doesn't exist.");

        if(!md5FileFilter.test(fSpec)) {
            System.out.println("SKIPPING: " + fSpec.uri() + "(" + fSpec.mimeType("UNKNOWN") + ")");
            return;
        }

        if(fSpec.isRecursable()) {
            System.out.println("LISTING : " + fSpec.uri());

            final FileSpec[] dirContents = fSpec.listSorted(vfs);
            if(dirContents == null || dirContents.length == 0) {
                if(deleteEmtyDirs) {
                    System.out.println("Empty directory: \"" + fSpec + "\"");
                    fSpec.delete();
                }
            } else {
                Functional.<IOException>recheck(
                    () -> Arrays.stream(dirContents)
                        .forEach(f -> uncheck(() -> doMd5(md5os, f, existing, deleteEmtyDirs, md5FileFilter, lineBuffered))));
            }
        } else if(attr == null || !attr.isOther()) {
            final FileRecord existingFr = Optional.ofNullable(existing).map(e -> e.get(fSpec.uri().toString())).orElse(null);

            final FileRecord fr;
            if(existingFr != null && existingFr.isComplete()) {
                System.out.println("COPYING : " + fSpec.uri());
                fr = existingFr;
            } else {
                System.out.println("SCANNING: " + fSpec.uri());
                final String md5 = (existingFr != null && existingFr.md5 != null) ? existingFr.md5 : bytesToHex(MD5.hash(fSpec));
                final String mime = (existingFr != null && existingFr.mime != null) ? existingFr.mime : fSpec.mimeType("UNKNOWN");
                fr = new FileRecord(fSpec.uri().toString(), fSpec.size(), mime, fSpec.lastModifiedTime(), md5);
            }
            md5os.println(om.writeValueAsString(fr));

            if(lineBuffered)
                md5os.flush();
        } else {
            if(attr.isOther())
                System.out.println("SKIPPING: Unknown file type (pipe, socket, device, etc.): " + fSpec.uri());
            else
                throw new IOException("Illegal file type: " + fSpec);
        }
    }

    // private static void printHash(final PrintWriter out, final FileSpec fSpec) throws IOException {
    // printHash(out, MD5.hash(fSpec), fSpec);
    // }
    //
    // private static void printHash(final PrintWriter out, final byte[] hash, final FileSpec fSpec) throws IOException {
    // out.println(HexStringUtil.bytesToHex(hash) + "||" + fSpec.path);
    // }
}
