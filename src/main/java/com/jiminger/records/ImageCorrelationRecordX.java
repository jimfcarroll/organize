//package com.jiminger.records;
//
//import static net.dempsy.util.Functional.recheck;
//import static net.dempsy.util.Functional.uncheck;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.io.IOException;
//import java.net.URI;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.function.Predicate;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.annotation.JsonInclude.Include;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.jiminger.records.ImageCorrelationRecordX.CorrelationWith;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import net.dempsy.serialization.jackson.JsonUtils;
//
//@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonInclude(Include.NON_EMPTY)
//public record ImageCorrelationRecordX(URI uri, CorrelatesWith[] correlations) {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(ImageCorrelationRecordX.class);
//    private static final ObjectMapper om = FileRecord.makeStandardObjectMapper();
//
//    public CorrelatesWith findCorrelation(final URI path) {
//        return Arrays.stream(Optional.ofNullable(correlations).orElse(new CorrelatesWith[0]))
//            .filter(cw -> path.equals(cw.uri))
//            .findAny()
//            .orElse(null);
//    }
//
//    public static List<ImageCorrelationRecordX> readImageCorrelationRecord(final String[] fileNames, final Predicate<ImageCorrelationRecordX> filter)
//        throws IOException {
//        final ObjectMapper om = JsonUtils.makeStandardObjectMapper();
//
//        final List<ImageCorrelationRecordX> ret = new ArrayList<>();
//        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
//            final File file = new File(fileName);
//            if(file.exists()) {
//                LOGGER.info("Reading {} ...", file);
//                try(BufferedReader br = new BufferedReader(new FileReader(file));) {
//                    for(String line = br.readLine(); line != null; line = br.readLine()) {
//                        final var fr = om.readValue(line, ImageCorrelationRecordX.class);
//                        if(filter == null || filter.test(fr))
//                            ret.add(fr);
//                    }
//                }
//            } else {
//                LOGGER.warn("The file \"{}\" doesn't exist. Can't load specs from it. Please update the config.", fileName);
//                throw new IllegalArgumentException("The file \"" + fileName + "\" doesn't exist. Can't load specs from it. Please update the config.");
//            }
//        })));
//        return ret;
//    }
//
//    public static List<ImageCorrelationRecordX> readImageCorrelationRecord(final String[] fileNames) throws IOException {
//        return readImageCorrelationRecord(fileNames, null);
//    }
//
//    public static List<ImageCorrelationRecordX> readImageCorrelationRecord(final String icFileToWrite, final String[] icFilesToRead,
//        final Predicate<ImageCorrelationRecordX> filter) throws IOException {
//        final boolean icFileToWriteExists = icFileToWrite == null ? false : new File(icFileToWrite).exists();
//
//        return readImageCorrelationRecord(
//            (icFileToWriteExists
//                ? Stream.concat(Stream.of(icFileToWrite), Arrays.stream(Optional.ofNullable(icFilesToRead).orElse(new String[0])))
//                : Arrays.stream(Optional.ofNullable(icFilesToRead).orElse(new String[0])))
//                    .toArray(String[]::new),
//
//            filter
//
//        );
//    }
//
//    public static List<ImageCorrelationRecordX> readImageCorrelationRecord(final String icFileToWrite, final String[] icFilesToRead) throws IOException {
//        return readImageCorrelationRecord(icFileToWrite, icFilesToRead, null);
//    }
//
//    public static Map<URI, ImageCorrelationRecordX> mergedImageCorrelationRecord(final String icFileToWrite, final String[] icFilesToRead,
//        final Predicate<ImageCorrelationRecordX> filter) throws IOException {
//
//        return readImageCorrelationRecord(icFileToWrite, icFilesToRead, filter).stream()
//            .collect(Collectors.toMap(fs -> fs.uri(), fs -> fs, (fr1, fr2) -> {
//                if(fr1.uri().equals(fr2.uri())) {
//                    final var c1 = Optional.ofNullable(fr1.correlations()).orElse(new CorrelatesWith[0]);
//                    final var c2 = Optional.ofNullable(fr2.correlations()).orElse(new CorrelatesWith[0]);
//                    final List<CorrelatesWith> corrs = new ArrayList<>(c1.length + c2.length);
//                    Stream.concat(Arrays.stream(c1), Arrays.stream(c2))
//                        // make sure the inaccurate ones are on the bottom so accurate ones will end up on corrs first
//                        .sorted((ic1, ic2) -> ic1.isInaccurate() ? (ic2.isInaccurate() ? 0 : 1) : (ic2.isInaccurate() ? -1 : 0))
//                        .forEach(ic -> {
//                            final var existing = corrs.stream().filter(i -> i.uri().equals(ic.uri())).findFirst().orElse(null);
//                            if(existing != null) {
//                                // verify they match.
//                                if(!ic.equals(existing)) {
//                                    if(!ic.closeEnough(existing))
//                                        throw new IllegalStateException("Duplicate keys for " + uncheck(() -> om.writeValueAsString(fr1)) + " and "
//                                            + uncheck(() -> om.writeValueAsString(fr2)) + " that can't be merged because of incompatible correlations at "
//                                            + ic.uri());
//                                }
//                            } else
//                                corrs.add(ic);
//                        });
//                    return new ImageCorrelationRecordX(fr1.uri(), corrs.toArray(ImageCorrelationRecordX.CorrelatesWith[]::new));
//                }
//                throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
//            }));
//
//    }
//
//    public static Map<URI, ImageCorrelationRecordX> mergedImageCorrelationRecord(final String icFileToWrite, final String[] icFilesToRead)
//        throws IOException {
//        return mergedImageCorrelationRecord(icFileToWrite, icFilesToRead, null);
//    }
//
//}
