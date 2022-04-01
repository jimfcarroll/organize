package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.jackson.JsonUtils;

public class FileRecord {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecord.class);

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

    public static List<FileRecord> readFileRecords(final String... fileNames) throws IOException {

        final ObjectMapper om = JsonUtils.makeStandardObjectMapper();

        final List<FileRecord> ret = new ArrayList<>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            final File file = new File(fileName);
            if(file.exists()) {
                LOGGER.info("Reading {} ...", file);
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int)(lastModifiedTime ^ (lastModifiedTime >>> 32));
        result = prime * result + ((md5 == null) ? 0 : md5.hashCode());
        result = prime * result + ((mime == null) ? 0 : mime.hashCode());
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + (int)(size ^ (size >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if(this == obj) return true;
        if(obj == null) return false;
        if(getClass() != obj.getClass()) return false;
        final FileRecord other = (FileRecord)obj;
        if(lastModifiedTime != other.lastModifiedTime) return false;
        if(md5 == null) {
            if(other.md5 != null) return false;
        } else if(!md5.equals(other.md5)) return false;
        if(mime == null) {
            if(other.mime != null) return false;
        } else if(!mime.equals(other.mime)) return false;
        if(path == null) {
            if(other.path != null) return false;
        } else if(!path.equals(other.path)) return false;
        if(size != other.size) return false;
        return true;
    }

    @Override
    public String toString() {
        return "FileRecord [path=" + path + ", size=" + size + ", mime=" + mime + ", lastModifiedTime=" + new Date(lastModifiedTime) + ", md5=" + md5 + "]";
    }
}
