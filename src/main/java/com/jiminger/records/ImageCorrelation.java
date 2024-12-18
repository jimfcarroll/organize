package com.jiminger.records;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.Closer;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public record ImageCorrelation(URI[] uris, float cc) {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageCorrelation.class);
    private static final ObjectMapper om = FileRecord.makeStandardObjectMapper();

    public ImageCorrelation {
        if(uris == null)
            throw new NullPointerException("Must supply uris");
        if(uris.length != 2)
            throw new IllegalStateException("The uris must be a pair.");
        if(uris[0] == null || uris[1] == null)
            throw new NullPointerException("Cannot set either URI to null");
    }

    /**
     * Return's the index of the given uri or -1 if it's not found. The result will be either
     * 0, 1, or -1.
     */
    public int which(final URI uri) {
        return uris[0].equals(uri) ? 0 : (uris[1].equals(uri) ? 1 : -1);
    }

    /**
     * Get the other uri paired with the one given. If the one given isn't either, null is returned.
     */
    public URI other(final URI uri) {
        switch(which(uri)) {
            case 0:
                return uris[1];
            case 1:
                return uris[0];
            default:
                return null;
        }
    }

    public static ImageCorrelation make(final URI uri1, final URI uri2, final float cc) {
        return new ImageCorrelation(new URI[] {uri1,uri2}, cc);
    }

    @Override
    public String toString() {
        return "ImageCorrelation [uris=" + Arrays.toString(uris) + ", cc=" + cc + "]";
    }

    public static class Manager {
        private final Map<URI, Map<URI, CorrelatesWith>> optimizedLookup = new HashMap<>();

        public void put(final ImageCorrelation ic) {
            final URI[] uris = ic.uris();
            // are either of them uri's already registered?
            final int mainUri;
            if(optimizedLookup.containsKey(uris[0]))
                mainUri = 0;
            else if(optimizedLookup.containsKey(uris[1]))
                mainUri = 1;
            else
                mainUri = -1;

            if(mainUri < 0) { // this is a new entry
                optimizedLookup.put(uris[0], new HashMap<>(Map.of(uris[1], new CorrelatesWith(uris[1], ic.cc()))));
            } else {
                final Map<URI, CorrelatesWith> cws = optimizedLookup.get(uris[mainUri]);
                // cws shouldn't be able to be null.
                final int other = mainUri == 0 ? 1 : 0;
                cws.put(uris[other], new CorrelatesWith(uris[other], ic.cc()));
            }
        }

        public ImageCorrelation find(final URI uri1, final URI uri2) {
            Map<URI, CorrelatesWith> ic = optimizedLookup.get(uri1);
            final boolean first;
            if(ic == null) {
                ic = optimizedLookup.get(uri2);
                first = false;
            } else
                first = true;

            if(ic == null)
                return null;

            final var cw = ic.get(first ? uri2 : uri1);
            if(cw == null)
                return null;
            return ImageCorrelation.make(uri1, uri2, cw.cc());
        }

        public Set<CorrelatesWith> findAllAssociated(final URI uri) {
            final Set<CorrelatesWith> ret = new HashSet<>();
            ret.addAll(Optional.ofNullable(optimizedLookup.get(uri)).map(v -> v.values()).orElse(Collections.emptySet()));
            for(final var cw: optimizedLookup.values()) {
                final var cur = cw.get(uri);
                if(cur != null)
                    ret.add(cur);
            }
            return ret;
        }

        /**
         * This will find the group of all other uri's where the weakest link is stronger
         * then the given threshold.
         */
        public Set<ImageCorrelation> group(final URI uri, final float threshold) {
            final Set<ImageCorrelation> cur = new HashSet<>();
            final int prevSize = 0;
            final URI curUri = uri;
            final Set<URI> alreadyChecked = new HashSet<>();
            // final Set<URI> toCheck = new HashSet<>();
            for(boolean done = false; !done;) {
                final var cws = findAllAssociated(curUri);
                cws.stream()
                    .filter(cw -> !alreadyChecked.contains(cw.uri()))
                    .peek(cw -> alreadyChecked.add(cw.uri()))
                    .forEach(cw -> cur.add(ImageCorrelation.make(curUri, cw.uri(), cw.cc())));

                if(cur.size() > prevSize)
                    done = false;
            }
            return null;
        }
    }

    public static Stream<ImageCorrelation> readImageCorrelationRecordsAsStream(final String[] fileNames) {
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
            .map(l -> uncheck(() -> om.readValue(l, new TypeReference<Map<String, Object>>() {})))
            .map(m -> convert(m))
            .filter(l -> l != null)
            .flatMap(l -> l.stream())
            .onClose(() -> c.close());
    }

    public static Stream<ImageCorrelation> readImageCorrelationRecordsAsStream(final String fileToWrite, final String[] filesToRead) {
        final boolean md5FileToWriteExists = fileToWrite == null ? false : new File(fileToWrite).exists();

        return readImageCorrelationRecordsAsStream(
            (md5FileToWriteExists
                ? Stream.concat(Stream.of(fileToWrite), Arrays.stream(Optional.ofNullable(filesToRead).orElse(new String[0])))
                : Arrays.stream(Optional.ofNullable(filesToRead).orElse(new String[0])))
                    .toArray(String[]::new));
    }

    public static Manager readImageCorrelationRecordsToManager(final String[] fileNames, final Manager appendTo) {
        final Manager manager = appendTo == null ? new Manager() : appendTo;
        readImageCorrelationRecordsAsStream(fileNames)
            .forEach(ic -> manager.put(ic));
        return manager;
    }

    public static Manager readImageCorrelationRecordsToManager(final String fileToWrite, final String[] filesToRead, final Manager appendTo) {
        final Manager manager = appendTo == null ? new Manager() : appendTo;
        readImageCorrelationRecordsAsStream(fileToWrite, filesToRead)
            .forEach(ic -> manager.put(ic));
        return manager;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_EMPTY)
    private static record Legacy(URI uri, CorrelatesWith[] correlations) {

    }

    private static List<ImageCorrelation> convert(final Map<String, Object> json) {
        // is it a legacy entry.
        if(json.containsKey("uri")) {
            // it's legacy
            final var legacy = om.convertValue(json, Legacy.class);
            if(legacy.correlations == null)
                return null;
            final URI refUri = legacy.uri();
            return Arrays.stream(legacy.correlations)
                .map(cw -> ImageCorrelation.make(refUri, cw.uri(), cw.cc()))
                .collect(Collectors.toList());
        } else // convert it normally.
            return List.of(om.convertValue(json, ImageCorrelation.class));
    }
}
