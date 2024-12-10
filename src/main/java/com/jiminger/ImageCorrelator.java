package com.jiminger;

import static com.jiminger.VfsConfig.createVfs;
import static com.jiminger.records.FileRecord.readFileRecordsAsStream;
import static com.jiminger.utils.Utils.groupByBaseFnameSimilarity;
import static com.jiminger.utils.Utils.isParentUri;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.records.CorrelatesWith;
import com.jiminger.records.FileRecord;
import com.jiminger.records.ImageCorrelation;
import com.jiminger.records.ImageDetails;
import com.jiminger.utils.FileAccess;
import com.jiminger.utils.HistogramBasedImageIndex;
import com.jiminger.utils.ImageUtils;
import com.jiminger.utils.Sci;
import com.jiminger.utils.Utils;
import com.jiminger.utils.Utils.ImageMatch;

import org.apache.commons.lang3.tuple.Pair;

import net.dempsy.util.MutableRef;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;

public class ImageCorrelator {

    public static void usage() {
        System.err
            .println(
                "Usage: java -cp [classpath] " + ImageCorrelator.class.getName() + " path/to/config.json uri/to/images");
        System.exit(1);
    }

    private static ObjectMapper om = FileRecord.makeStandardObjectMapper();
    private static Comparator<FileRecord> FR_COMP = Utils.getGroupingCompliantFileRecordComparator();

    public static ImageCorrelation find(final ImageCorrelation.Manager[] ims, final Map<URI, String> md5s, final URI uri1, final URI uri2) {
        // check if there's already md5s for each file and if they match.
        final var md51 = md5s.get(uri1);
        final var md52 = md5s.get(uri2);
        if(md51 != null && md51.equals(md52)) {
            // then they're copies of the same file
            return ImageCorrelation.make(uri1, uri2, (float)1.0);
        }
        for(final var im: ims) {
            final var ret = im.find(uri1, uri2);
            if(ret != null)
                return ret;
        }
        return null;
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 2)
            usage();

        final Config conf = Config.load(args[0]);
        final var vfs = createVfs(conf.passwordsToTry);

        final String correlationFileStr = conf.imageCorrelationsFileToWrite;

        System.out.println("Loading Manager for existing records.");
        final ImageCorrelation.Manager existingIcMgr = ImageCorrelation.readImageCorrelationRecordsToManager(conf.imageCorrelationsFileToWrite,
            conf.imageCorrelationsFilesToRead, null);

        final ImageCorrelation.Manager newCoefMgr = new ImageCorrelation.Manager();
        final ImageCorrelation.Manager[] ims = new ImageCorrelation.Manager[] {existingIcMgr,newCoefMgr};

        final int maxImageSize = conf.maxImageDim;

        try(final PrintStream corrOs = new PrintStream(new File(correlationFileStr));) {

            final URI rootToConsider = new URI(args[1]);

            final Map<URI, String> md5s = new HashMap<>();

            // Group the files by name and parent so we can find cheesey duplicates
            {
                // get all image records.
                final List<FileRecord> records = readFileRecordsAsStream(conf.md5FileToWrite, conf.md5FilesToRead)
                    .peek(fr -> md5s.compute(fr.uri(), (k, v) -> {
                        final var nmd5 = fr.md5();
                        if(v == null)
                            return nmd5;
                        if(!v.equals(nmd5))
                            throw new IllegalStateException(
                                "Mismatched md5 value between two FileRecords for " + fr.uri() + ". One is " + nmd5 + " and the other is " + v);
                        return nmd5;
                    }))
                    .filter(fr -> isParentUri(rootToConsider, fr.uri()))
                    .filter(fr -> fr.mime() != null)
                    .filter(fr -> fr.mime().startsWith("image/"))
                    .sorted((fr1, fr2) -> FR_COMP.compare(fr1, fr2))
                    .collect(Collectors.toList())

                ;

                System.out.println("Total number of images: " + records.size());

                System.out.println("Grouping records");
                final List<List<FileRecord>> grouped = groupByBaseFnameSimilarity(records);
                System.out.println("Total number of groups: " + grouped.size());

                for(final var group: grouped) {
                    System.out.println("=================================================");
                    // if there's a .CR2 (we'll use the mime type to check) and there's more than 2, we need to check.
                    final long largestSize = group.stream().mapToLong(f -> f.size()).max().orElse(0);
                    final var sortedX = new ArrayList<>(group.stream()
                        .sorted((fr1, fr2) -> Long.compare(modifiedSize(fr2, largestSize), modifiedSize(fr1, largestSize)))
                        .collect(Collectors.toList()));
                    System.out.println("Sorted group contains " + sortedX.size() + " entries");

                    final Pair<FileRecord[], List<FileRecord>> separated = Utils.separateMainImagesFromRemainder(sortedX);

                    for(final var mainFr: separated.getKey()) {
                        final var sorted = new LinkedList<>(separated.getValue());
                        if(sorted.size() > 0) {
                            System.out.println("MAIN  :" + mainFr.uri());

                            final List<ImageMatch> matches = new ArrayList<>(sorted.size());
                            // check if I already have matches loaded from cur.
                            for(int i = sorted.size() - 1; i >= 0; i--) {
                                // now what ....
                                final FileRecord cur = sorted.get(i);
                                final ImageCorrelation entry = find(ims, md5s, mainFr.uri(), cur.uri());
                                if(entry != null) {
                                    System.out.println("EXISTS:" + entry.other(mainFr.uri()));
                                    matches.add(new ImageMatch(entry.cc(), cur));
                                    sorted.remove(i);
                                }
                            }

                            matches.addAll(Sci.matches(mainFr, sorted, vfs, conf.avoidMemMap, maxImageSize, true, ImageDetails.NORM_MIN_MAX_LOW,
                                ImageDetails.NORM_MIN_MAX_HIGH));

                            final var mainUri = mainFr.uri();
                            matches.stream()
                                .peek(e -> System.out.println("CORR  :" + String.format("%.2f", (e.cc() * 100)) + ":" + e.imageFr().uri()))
                                .map(m -> new CorrelatesWith(m.imageFr().uri(), (float)m.cc()))
                                .map(cw -> ImageCorrelation.make(mainUri, cw.uri(), cw.cc()))
                                .peek(ic -> newCoefMgr.put(ic))
                                .peek(ic -> corrOs.println(uncheck(() -> om.writeValueAsString(ic))))
                                .forEach(ic -> corrOs.flush());
                        } else
                            System.out.println("NOGRP4:" + mainFr.uri());
                    }
                }
            } // end group processing.

            // now we're searching for duplicates.
            {
                final float thresh = 0.98f;
                // get all image records with histograms
                System.out.println("Building histogram from FileRecords");
                try(var index = new HistogramBasedImageIndex(

                    readFileRecordsAsStream(conf.md5FileToWrite, conf.md5FilesToRead)
                        .filter(ImageDetails::hasHistogram)

                );) {

                    System.out.println("Calculating likley candidates.");
                    // get all image records with histograms under rootToConsider
                    readFileRecordsAsStream(conf.md5FileToWrite, conf.md5FilesToRead)
                        .filter(fr -> isParentUri(rootToConsider, fr.uri()))
                        .filter(fr -> uncheck(() -> fr.fileSpec(vfs).exists()))
                        .filter(ImageDetails::hasHistogram)
                        .map(fr -> Pair.of(fr.uri(), uncheck(() -> index.search(fr, vfs, conf.avoidMemMap, thresh, 30))))
                        .filter(ics -> ics.getValue() != null)
                        .filter(ics -> ics.getValue().length > 0)
                        .map(ics -> uncheck(() -> checkCandidates(ims, md5s, ics, vfs, conf.avoidMemMap, maxImageSize)))
                        .filter(ics -> ics.getValue() != null)
                        .filter(ics -> ics.getValue().length > 0)
                        .flatMap(ics -> Arrays.stream(ics.getValue())
                            .map(cw -> ImageCorrelation.make(ics.getKey(), cw.uri(), cw.cc())))
                        .peek(ic -> newCoefMgr.put(ic))
                        .peek(ic -> corrOs.println(uncheck(() -> om.writeValueAsString(ic))))
                        .forEach(ic -> corrOs.flush());

                    ;
                }
            }
        }
    }

    private static Pair<URI, CorrelatesWith[]> checkCandidates(final ImageCorrelation.Manager[] ims, final Map<URI, String> md5s,
        final Pair<URI, CorrelatesWith[]> candidates, final Vfs vfs, final boolean avoidMemMap, final int maxImageSize) throws IOException {

        final MutableRef<CvMat> refMatX = new MutableRef<>();
        final URI mainUri = candidates.getKey();
        System.out.println("MAIN  : " + mainUri);
        try(final FileAccess fa = new FileAccess(new FileSpec(vfs.toPath(mainUri)), avoidMemMap);
            final var closer = new Closer();) {

            final Supplier<CvMat> getRefMat = () -> {
                if(refMatX.ref == null) {
                    System.out.println("LODING:" + fa.uri());
                    refMatX.ref = uncheck(() -> closer.add(ImageUtils.loadImage(fa, maxImageSize))).image();
                }
                return refMatX.ref;
            };

            final List<CorrelatesWith> cws = new ArrayList<>();
            final var candidateCws = candidates.getValue();
            for(int i = 0; i < candidateCws.length; i++) {
                final var c = candidateCws[i];
                final URI curUri = c.uri();

                final ImageCorrelation existing = find(ims, md5s, mainUri, curUri);
                if(existing != null) {
                    System.out.println("EXISTS:" + curUri);
                    cws.add(new CorrelatesWith(curUri, existing.cc()));
                } else {
                    System.out.println("CALCC :" + curUri);
                    try(FileAccess cfa = new FileAccess(new FileSpec(vfs.toPath(curUri)), avoidMemMap);) {
                        if(cfa.toFile().exists()) {
                            try(var against = ImageUtils.loadImage(cfa, maxImageSize);) {

                                final float coef = (float)Sci.correlationCoef(getRefMat.get(), against.image(), true, ImageDetails.NORM_MIN_MAX_LOW,
                                    ImageDetails.NORM_MIN_MAX_HIGH, i == (candidateCws.length - 1), true);
                                cws.add(new CorrelatesWith(curUri, coef));
                            }
                        }
                    }
                }
            }

            cws.forEach(cw -> System.out.println("CORR  :" + cw.cc() + ":" + cw.uri()));

            return Pair.of(mainUri, cws.toArray(CorrelatesWith[]::new));
        }
    }

    private static long modifiedSize(final FileRecord fr, final long largest) {
        return (fr.uri().toString().contains("_shotwell") || fr.uri().toString().contains("_embedded")) ? (fr.size() - largest) : fr.size();
    }

}
