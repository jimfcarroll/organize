package com.jiminger.records;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

public interface FileRecordDb extends Closeable {

    public FileRecord find(final URI uri) throws IOException;

    public long size();

    public Stream<FileRecord> stream();

}
