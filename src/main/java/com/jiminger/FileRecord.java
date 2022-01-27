package com.jiminger;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FileRecord {

    public static final long UNINITIALIZED = Long.MIN_VALUE;

    public final String path;
    public final long size;
    public final String mime;
    public final long lastModifiedTime;
    public final String md5;

    public FileRecord(final String path, final long size, final String mime, final long lastModifiedTime, final String md5) {
        if(path == null)
            throw new NullPointerException("Cannot create a " + FileRecord.class.getSimpleName() + " without a path.");
        this.path = path;
        this.size = size;
        this.mime = mime;
        this.lastModifiedTime = lastModifiedTime;
        this.md5 = md5;
    }

    @SuppressWarnings("unused")
    private FileRecord() {
        path = null;
        mime = null;
        md5 = null;
        size = UNINITIALIZED;
        lastModifiedTime = UNINITIALIZED;
    }

    @JsonIgnore
    public boolean isComplete() {
        return !(size == UNINITIALIZED
            || lastModifiedTime == UNINITIALIZED
            || mime == null
            || md5 == null);
    }

    @Override
    public String toString() {
        return "FileRecord [path=" + path + ", size=" + size + ", mime=" + mime + ", lastModifiedTime=" + new Date(lastModifiedTime) + ", md5=" + md5 + "]";
    }
}
