package com.jiminger.records;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static org.lmdbjava.DbiFlags.MDB_CREATE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.jiminger.utils.MD5;

import org.apache.commons.io.IOUtils;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.kryo.KryoOptimizer;
import net.dempsy.serialization.kryo.KryoSerializer;
import net.dempsy.serialization.kryo.Registration;
import net.dempsy.util.Functional;
import net.dempsy.vfs.Vfs;

public class FileRecordLmdb implements FileRecordDb {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecordLmdb.class);

    private final File dbFile;
    final Env<ByteBuffer> env;

    public static final String DB_NAME = "main";
    private final KryoSerializer serializer;
    private final Dbi<ByteBuffer> db;
    private Txn<ByteBuffer> readTxn;

    private FileRecordLmdb(final Vfs vfs, final Stream<String> fileNamesS) throws IOException {

        dbFile = Files.createTempDirectory("md5Lmdb-").toFile();
        dbFile.deleteOnExit();

        serializer = new KryoSerializer(true, new KryoOptimizer() {
            @Override
            public void preRegister(final Kryo kryo) {

                kryo.register(URI.class, new com.esotericsoftware.kryo.Serializer<URI>() {
                    @Override
                    public void write(final Kryo kryo, final Output output, final URI uri) {
                        output.writeString(uri.toString()); // Serialize URI as a String
                    }

                    @Override
                    public URI read(final Kryo kryo, final Input input, final Class<? extends URI> type) {
                        return URI.create(input.readString()); // Deserialize String back to URI
                    }
                });
            }

            @Override
            public void postRegister(final Kryo kryo) {}
        },
            new Registration(FileRecord.class.getName()),
            new Registration(ImageDetails.class.getName())

        );

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

        // We always need an Env. An Env owns a physical on-disk storage file. One
        // Env can store many different databases (ie sorted maps).
        env = Env.create()
            // LMDB also needs to know how large our DB might be. Over-estimating is OK.
            .setMapSize(dbSize)
            // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
            .setMaxDbs(1)
            // Now let's open the Env. The same path can be concurrently opened and
            // used in different processes, but do not open the same path twice in
            // the same process at the same time.
            .open(dbFile);

        // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
        // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
        db = env.openDbi(DB_NAME, MDB_CREATE);

        try(Txn<ByteBuffer> txn = env.txnWrite()) {
            FileRecord.readFileRecordsAsStream(fileNames)
                .forEach(fr -> {
                    final var keyBytes = toKey(fr);
                    final var key = getCleanKeyByteBufferFor(keyBytes.length);
                    key.put(keyBytes).flip();

                    final ByteBuffer existingValue = db.get(txn, key);

                    final FileRecord toWrite;
                    if(existingValue != null) {
                        final byte[] existingBytes = new byte[existingValue.remaining()];
                        existingValue.get(existingBytes);
                        final FileRecord existing = uncheck(() -> serializer.deserialize(existingBytes, FileRecord.class));
                        final var selected = validateAndSelect(existing, fr);
                        toWrite = (selected == existing) ? null : fr;
                    } else
                        toWrite = fr;

                    if(toWrite != null) {
                        final var valBytes = uncheck(() -> serializer.serialize(toWrite));
                        final ByteBuffer valBb = getCleanValueByteBufferFor(valBytes.length);
                        valBb.put(valBytes).flip();
                        db.put(txn, key, valBb);
                    }
                });
            txn.commit();
        }

        readTxn = env.txnRead();
    }

    private byte[] toKey(final URI uri) {
        return MD5.hash(uri.toString(), StandardCharsets.UTF_8);
    }

    private byte[] toKey(final FileRecord fr) {
        return toKey(fr.uri());
    }

    @Override
    public void close() throws IOException {
        ignore(() -> readTxn.close());
        ignore(() -> db.close());
        ignore(() -> env.close());
    }

    @Override
    public FileRecord find(final URI uri) throws IOException {
        final var keyBytes = toKey(uri);
        final var key = getCleanKeyByteBufferFor(keyBytes.length);
        key.put(keyBytes).flip();
        final ByteBuffer existingValue = db.get(readTxn, key);
        if(existingValue != null) {
            final byte[] existingBytes = new byte[existingValue.remaining()];
            existingValue.get(existingBytes);
            return uncheck(() -> serializer.deserialize(existingBytes, FileRecord.class));
        }
        return null;
    }

    @Override
    public long size() {
        // Transaction to get statistics
        final var stats = db.stat(readTxn);

        // Total record count
        return stats.entries;
    }

    @Override
    public Stream<FileRecord> stream() {
        final CursorIterable<ByteBuffer> ci = db.iterate(readTxn, KeyRange.all());
        return Functional.iteratorAsStream(ci.iterator())
            .map(kv -> kv.val())
            .map(bb -> {
                final byte[] existingBytes = new byte[bb.remaining()];
                bb.get(existingBytes);
                return uncheck(() -> serializer.deserialize(existingBytes, FileRecord.class));
            })
            .onClose(() -> {
                ignore(() -> ci.close());
            });
    }

    public static FileRecordLmdb makeFileRecordsManager(final Vfs vfs, final String md5FileToWrite, final String[] md5FilesToRead) throws IOException {
        final var md5FileToWriteFile = md5FileToWrite == null ? null : new File(md5FileToWrite);
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

        return new FileRecordLmdb(vfs,
            (md5FileToWriteExists
                ? Stream.concat(Stream.of(md5FileToWriteCopy), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))
                : Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0])))

        );
    }

    private ByteBuffer value = ByteBuffer.allocateDirect(512);

    private ByteBuffer getCleanValueByteBufferFor(final int size) {
        if(value.capacity() < size) {
            // free(value);
            value = ByteBuffer.allocateDirect(size);
        }
        value.clear();
        return value;
    }

    private ByteBuffer keyX = ByteBuffer.allocateDirect(16);

    private ByteBuffer getCleanKeyByteBufferFor(final int size) {
        if(keyX.capacity() < size) {
            // free(value);
            keyX = ByteBuffer.allocateDirect(size);
        }
        keyX.clear();
        return keyX;
    }

    private static FileRecord validateAndSelect(final FileRecord existingRecord, final FileRecord newRecord) {
        if(!existingRecord.uri().equals(newRecord.uri()))
            throw new IllegalStateException("YOW! A hash collision!");
        if(!existingRecord.md5().equals(newRecord.md5()))
            throw new IllegalStateException("Entry " + existingRecord + " has a different md5 than " + newRecord);

        if(ImageDetails.hasHistogram(existingRecord))
            return existingRecord;
        else
            return newRecord;
    }

}
