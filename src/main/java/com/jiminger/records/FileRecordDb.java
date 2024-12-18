package com.jiminger.records;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

public interface FileRecordDb<K> extends Closeable {

    public FileRecord[] find(final K key) throws IOException;

    public default FileRecord findOne(final K key) throws IOException {
        return Optional.ofNullable(find(key))
            .map(fs -> {
                if(fs.length > 1)
                    throw new IllegalStateException("Key " + key + " has multiple entries: " + Arrays.toString(fs));
                return fs.length == 0 ? null : fs[0];
            })
            .orElse(null);
    }

    public long size();

    public Stream<FileRecord> stream();

    public void upsert(FileRecord fr) throws IOException;

}
