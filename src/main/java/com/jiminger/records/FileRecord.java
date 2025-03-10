package com.jiminger.records;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.utils.FileAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;

public record FileRecord(URI uri, long size, String mime, long lastModifiedTime, String md5, Map<String, Object> additional) {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRecord.class);

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

    public static Map<String, List<FileRecord>> groupByMd5(final Collection<FileRecord> toGroup) {
        final Map<String, List<FileRecord>> ret = new HashMap<>((int)Math.ceil(toGroup.size() / 0.74));
        toGroup.forEach(fr -> ret.compute(fr.md5(), (k, v) -> {
            final List<FileRecord> cur = (v == null) ? new ArrayList<>() : v;
            cur.add(fr);
            return cur;
        }));
        return ret;
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
