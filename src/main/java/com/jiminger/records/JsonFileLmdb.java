package com.jiminger.records;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static org.lmdbjava.DbiFlags.MDB_CREATE;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.apache.commons.io.FileUtils;
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

public class JsonFileLmdb<K, V> implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonFileLmdb.class);
    public static final String DB_NAME = "main";

    private final File dbFile;
    private final Env<ByteBuffer> env;

    private final KryoSerializer serializerX;
    private final Dbi<ByteBuffer> db;
    private Txn<ByteBuffer> readTxn;

    public final long dbSize;

    private static List<File> toCloseOnShutdown = new ArrayList<>();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized(toCloseOnShutdown) {
                toCloseOnShutdown.stream()
                    .filter(f -> f.exists())
                    .forEach(f -> ignore(() -> FileUtils.deleteDirectory(f), e -> LOGGER.error("ERROR cleaning up {}: ", f, e)));
            }
        }, "Lmdb-shutdown-cleanup"));
    }

    public static interface Index<K1, V1> {
        public byte[] toKey(K1 key);

        public K1 extractKey(V1 fr);

        public V1[] select(final V1[] existingRecord, final V1 newRecord);
    }

    private final Index<K, V> index;
    private final Class<V[]> valueArrayClass;
    private final Class<V> valueClass;

    protected JsonFileLmdb(final Class<V> valueClass, final Index<K, V> index, final long dbSize, final Stream<V> frStream, final Registration... additional)
        throws IOException {
        this.valueClass = valueClass;
        @SuppressWarnings("unchecked")
        final var tmp = (Class<V[]>)Array.newInstance(valueClass, 0).getClass();
        this.valueArrayClass = tmp;

        this.dbSize = dbSize;
        this.index = index;
        dbFile = Files.createTempDirectory("md5Lmdb-").toFile();
        synchronized(toCloseOnShutdown) {
            toCloseOnShutdown.add(dbFile);
        }

        serializerX = new KryoSerializer(true, new KryoOptimizer() {
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
            new Registration(valueClass.getName()),
            new Registration(valueArrayClass.getName())

        );

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
            frStream
                .forEach(fr -> {
                    final var keyBytes = valueToKey(fr);
                    final var key = getCleanKeyByteBufferFor(keyBytes.length);
                    key.put(keyBytes).flip();

                    final ByteBuffer existingValue = db.get(txn, key);

                    final V[] toWrite;
                    if(existingValue != null) {
                        final byte[] existingBytes = new byte[existingValue.remaining()];
                        existingValue.get(existingBytes);
                        final V[] existing = deserialize(existingBytes);
                        final var selected = index.select(existing, fr);
                        toWrite = (selected == existing) ? null : makeValueArray(fr);
                    } else
                        toWrite = makeValueArray(fr);

                    if(toWrite != null) {
                        final var valBytes = serialize(toWrite);
                        final ByteBuffer valBb = getCleanValueByteBufferFor(valBytes.length);
                        valBb.put(valBytes).flip();
                        db.put(txn, key, valBb);
                    }
                });
            txn.commit();
        }

        readTxn = env.txnRead();
    }

    @Override
    public void close() throws IOException {
        ignore(() -> readTxn.close());
        ignore(() -> db.close());
        ignore(() -> env.close());
        ignore(() -> FileUtils.deleteDirectory(dbFile));
        if(!dbFile.exists()) {
            synchronized(toCloseOnShutdown) {
                toCloseOnShutdown.remove(dbFile);
            }
        }
    }

    public V[] find(final K key) throws IOException {
        final var keyBytes = toKey(key);
        final var keyBb = getCleanKeyByteBufferFor(keyBytes.length);
        keyBb.put(keyBytes).flip();
        final ByteBuffer existingValue = db.get(readTxn, keyBb);
        if(existingValue != null) {
            final byte[] existingBytes = new byte[existingValue.remaining()];
            existingValue.get(existingBytes);
            return deserialize(existingBytes);
        }
        return null;
    }

    public long size() {
        // Transaction to get statistics
        final var stats = db.stat(readTxn);

        // Total record count
        return stats.entries;
    }

    public Stream<V> stream() {
        final CursorIterable<ByteBuffer> ci = db.iterate(readTxn, KeyRange.all());
        return Functional.iteratorAsStream(ci.iterator())
            .map(kv -> kv.val())
            .map(bb -> {
                final byte[] existingBytes = new byte[bb.remaining()];
                bb.get(existingBytes);
                return deserialize(existingBytes);
            })
            .filter(frs -> frs != null)
            .filter(frs -> frs.length > 0)
            .flatMap(frs -> Arrays.stream(frs))
            .onClose(() -> {
                ignore(() -> ci.close());
            });
    }

    public void upsert(final V fr) throws IOException {
        final var frs = find(index.extractKey(fr));
        final V[] toWrite;
        if(frs != null) {
            toWrite = index.select(frs, fr);
            if(toWrite == frs) // then there's no change
                return;
        } else
            toWrite = makeValueArray(fr);

        if(toWrite != null) {
            final var keyBytes = valueToKey(fr);
            final var key = getCleanKeyByteBufferFor(keyBytes.length);
            key.put(keyBytes).flip();
            final var valBytes = serialize(toWrite);
            final ByteBuffer valBb = getCleanValueByteBufferFor(valBytes.length);
            valBb.put(valBytes).flip();

            readTxn.close();
            try(Txn<ByteBuffer> txn = env.txnWrite()) {
                db.put(txn, key, valBb);
                txn.commit();
            } finally {
                readTxn = env.txnRead();
            }
        }
    }

    private V[] makeValueArray(final V values) {
        @SuppressWarnings("unchecked")
        final var ret = (V[])Array.newInstance(valueClass, 1);
        ret[0] = values;
        return ret;
    }

    private byte[] toKey(final K key) {
        return index.toKey(key);
    }

    private byte[] valueToKey(final V fr) {
        return toKey(index.extractKey(fr));
    }

    private V[] deserialize(final byte[] records) {
        return uncheck(() -> serializerX.deserialize(records, valueArrayClass));
    }

    private byte[] serialize(final V[] records) {
        return uncheck(() -> serializerX.serialize(records));
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
}
