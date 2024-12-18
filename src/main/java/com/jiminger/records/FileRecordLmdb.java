package com.jiminger.records;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.HexStringUtil.hexToBytes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.IllegalSelectorException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jiminger.utils.MD5;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.kryo.Registration;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class FileRecordLmdb<K> extends JsonFileLmdb<K, FileRecord> implements FileRecordDb<K> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecordLmdb.class);
    public static final UriIndex uriIndex = new UriIndex();

    public static class UriIndex implements Index<URI, FileRecord> {
        @Override
        public byte[] toKey(final URI uri) {
            return MD5.hash(uri.toString(), StandardCharsets.UTF_8);
        }

        @Override
        public URI extractKey(final FileRecord fr) {
            return fr.uri();
        }

        @Override
        public FileRecord[] select(final FileRecord[] existingRecords, final FileRecord newRecord) {
            if(existingRecords.length > 1 || existingRecords.length == 0)
                throw new IllegalSelectorException(); // IMPOSSIBLE
            final var existingRecord = existingRecords[0];
            if(!existingRecord.uri().equals(newRecord.uri()))
                throw new IllegalStateException("YOW! A hash collision!");
            if(!existingRecord.md5().equals(newRecord.md5()))
                throw new IllegalStateException("Entry " + existingRecord + " has a different md5 than " + newRecord);

            if(ImageDetails.hasHistogram(existingRecord))
                return existingRecords;
            else
                return new FileRecord[] {newRecord};
        }
    }

    public static class Md5Index implements Index<String, FileRecord> {
        @Override
        public byte[] toKey(final String key) {
            return hexToBytes(key);
        }

        @Override
        public String extractKey(final FileRecord fr) {
            return fr.md5();
        }

        @Override
        public FileRecord[] select(final FileRecord[] existingRecords, final FileRecord newRecord) {
            if(existingRecords.length == 0)
                throw new IllegalSelectorException(); // IMPOSSIBLE
            final Map<URI, FileRecord> lookup = Arrays.stream(existingRecords).collect(Collectors.toMap(fr -> fr.uri(), fr -> fr));

            final FileRecord existingRecord = lookup.get(newRecord.uri());
            if(existingRecord != null) {
                if(!existingRecord.md5().equals(newRecord.md5()))
                    throw new IllegalStateException("Entry " + existingRecord + " has a different md5 than " + newRecord);

                if(ImageDetails.hasHistogram(existingRecord))
                    return existingRecords;

                // otherwise we're going to replace the existing record
                return Arrays.stream(existingRecords)
                    .map(fr -> fr == existingRecord ? newRecord : fr)
                    .toArray(FileRecord[]::new);
            }
            return Stream.concat(Arrays.stream(existingRecords), Stream.of(newRecord)).toArray(FileRecord[]::new);
        }
    }

    private FileRecordLmdb(final Index<K, FileRecord> index, final long dbSize, final Stream<FileRecord> frStream) throws IOException {
        super(FileRecord.class, index, dbSize, frStream, new Registration(ImageDetails.class.getName()));
    }

    public static FileRecordLmdb<URI> makeFileRecordsManager(final String md5FileToWrite, final String[] md5FilesToRead) throws IOException {
        return makeFileRecordsManager(uriIndex, md5FileToWrite, md5FilesToRead);
    }

    public static <K1> FileRecordLmdb<K1> makeFileRecordsManager(final Index<K1, FileRecord> index, final String md5FileToWrite, final String[] md5FilesToRead)
        throws IOException {

        final var md5FileToWriteFile = md5FileToWrite == null ? null : new File(md5FileToWrite);
        final boolean md5FileToWriteExists = md5FileToWrite == null ? false : md5FileToWriteFile.exists();

        final String md5FileToWriteCopy;
        if(md5FileToWriteExists) {
            final Path md5Path;
            try(Vfs vfs = new Vfs();) {
                md5Path = vfs.toPath(uncheck(() -> md5FileToWriteFile.toURI()));
            }

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

        final Stream<String> fileNamesS = (md5FileToWriteExists
            ? Stream.concat(Stream.of(md5FileToWriteCopy), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
            : Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])));

        final var fileNames = fileNamesS.toArray(String[]::new);

        final long totalBytesInCurrentFiles = Arrays.stream(fileNames)
            .map(fileName -> new File(fileName))
            .peek(f -> {
                if(!f.exists()) {
                    LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", f);
                    throw new IllegalArgumentException("The file \"" + f + "\" doesn't exist. Can't load specs from it. Please update the config.");
                }
            })
            .mapToLong(file -> file.length())
            .sum();

        // we'll just double the size to account for the keys and map structure
        final long dbSize = totalBytesInCurrentFiles << 1;

        return makeFileRecordsManager(index, dbSize, FileRecord.readFileRecordsAsStream(fileNames));
    }

    public static <K1> FileRecordLmdb<K1> makeFileRecordsManager(final Index<K1, FileRecord> index, final long dbSize, final Stream<FileRecord> frStream)
        throws IOException {
        return new FileRecordLmdb<K1>(index, dbSize, frStream);
    }

}
