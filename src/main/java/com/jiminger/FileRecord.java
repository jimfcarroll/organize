package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.vfs.Vfs;

public record FileRecord(String path, long size, String mime, long lastModifiedTime, String md5) {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecord.class);

    public static final long UNINITIALIZED = Long.MIN_VALUE;

    public FileRecord {
        if(path == null)
            throw new NullPointerException("Cannot create a " + FileRecord.class.getSimpleName() + " without a path.");
    }

    @JsonIgnore
    public boolean isComplete() {
        return !(size == UNINITIALIZED
            || lastModifiedTime == UNINITIALIZED
            || mime == null
            || md5 == null);
    }

    public URI uri() {
        return uncheck(() -> new URI(path));
    }

    public InputStream read(final Vfs vfs) throws IOException {
        return vfs.toPath(uri()).read();
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
}
