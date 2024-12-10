package com.jiminger.utils;

import static com.jiminger.utils.ImageUtils.DONT_RESIZE;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.jiminger.records.CorrelatesWith;
import com.jiminger.records.FileRecord;
import com.jiminger.records.ImageDetails;
import com.jiminger.utils.ImageUtils.Dims;

import org.apache.commons.lang3.mutable.MutableInt;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Scalar;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;

public class HistogramBasedImageIndex implements QuietCloseable {
    static {
        CvMat.initOpenCv();
    }

    private final CvMat histograms;
    private final URI[] pathLookup;
    private final Map<URI, Integer> indexLookup;
    private final Mat normalizedLargeMat;

    private static final Mat nothing = new Mat();

    public HistogramBasedImageIndex(final Collection<FileRecord> frs) {
        this(frs.stream());
    }

    public HistogramBasedImageIndex(final Supplier<Stream<FileRecord>> frs) {
        final long count;
        // we're going to iterate twice
        try(var qc = frs.get()) {
            count = qc
                .filter(ImageDetails::hasHistogram)
                .count();
        }

        try(var qc = frs.get();
            CvMat histograms = new CvMat((int)count, 256, CvType.CV_32FC1);) {
            final int rows = (int)count;
            final MutableInt row = new MutableInt(0);

            pathLookup = new URI[rows];
            indexLookup = new HashMap<>(rows, 1.0f);

            qc
                .filter(ImageDetails::hasHistogram)
                .forEach(fr -> {
                    final int i = row.intValue();
                    pathLookup[i] = fr.uri();
                    indexLookup.put(pathLookup[i], i);
                    histograms.put(i, 0, ImageDetails.getFrom(fr).histogram());
                    row.increment();
                });

            this.histograms = histograms.returnMe();
        }
        this.normalizedLargeMat = calcNormalizedLargeMat(this.histograms);
    }

    public HistogramBasedImageIndex(final Stream<FileRecord> frs) {
        // need a count.
        final List<URI> paths = new ArrayList<>(10000);
        final List<float[]> hists = new ArrayList<>(10000);
        frs
            .filter(ImageDetails::hasHistogram)
            .forEach(fr -> {
                paths.add(fr.uri());
                hists.add(ImageDetails.getFrom(fr).histogram());
            });
        final int rows = hists.size();
        pathLookup = new URI[rows];
        indexLookup = new HashMap<>(rows, 1.0f);
        try(CvMat histograms = new CvMat(rows, 256, CvType.CV_32FC1);) {
            for(int i = 0; i < rows; i++) {
                pathLookup[i] = paths.get(i);
                indexLookup.put(pathLookup[i], i);
                histograms.put(i, 0, hists.get(i));
            }

            this.histograms = histograms.returnMe();
        }
        this.normalizedLargeMat = calcNormalizedLargeMat(this.histograms);
    }

    public CorrelatesWith[] search(final FileAccess ref, final float threshold, final int maxNum) throws IOException {
        // check if the uri is already part of the DB.
        final Integer refIndex = indexLookup.get(ref.uri());
        if(refIndex != null) {
            final float[] refHist = new float[256];
            histograms.get(refIndex.intValue(), 0, refHist);
            return _match(refHist, threshold, maxNum, refIndex.intValue());
        }
        try(var lmat = ImageUtils.loadImage(ref, DONT_RESIZE);) {
            if(lmat == null || lmat.image().dataAddr() == 0L)
                throw new IllegalStateException("Mat created from " + ref + " is invalid.");
            return _match(ref.uri(), lmat.image(), threshold, maxNum, true);
        }
    }

    public CorrelatesWith[] search(final FileRecord ref, final Vfs vfs, final boolean avoidMemMap, final float threshold, final int maxNum)
        throws IOException {
        if(ImageDetails.hasHistogram(ref)) {
            return _match(ImageDetails.getFrom(ref).histogram(), threshold, maxNum, Optional.ofNullable(indexLookup.get(ref.uri())).orElse(-1));
        } else
            return search(ref.fileAccess(vfs, avoidMemMap), threshold, maxNum);
    }

    public float[] multiHistogramCorrelationCoef(final float[] refHistogram) {
        try(CvMat refHistogramMat = new CvMat(1, 256, CvType.CV_32FC1);) {
            refHistogramMat.put(0, 0, refHistogram);
            return multiHistogramCorrelationCoef(refHistogramMat);
        }
    }

    @Override
    public void close() {
        if(histograms != null)
            histograms.close();
    }

    private static int[] topIndexes(final float[] array, final float thresh, final int maxNum, final int skipMe) {
        return IntStream.range(0, array.length) // Create a stream of indexes
            .filter(i -> array[i] >= thresh) // Filter values >= threshold
            .boxed() // Box to Integer for sorting
            .sorted(Comparator.comparing((final Integer i) -> array[i]) // Sort by array values
                .reversed()) // In descending order
            .filter(i -> skipMe != i.intValue())
            .limit(maxNum) // Take the top N
            .mapToInt(Integer::intValue) // Unbox to int
            .toArray(); // Collect as an int[]
    }

    // Example histograms: 10000 rows x 256 columns
    // Reference row: 1 x 256
    private float[] multiHistogramCorrelationCoef(final Mat refHistogram) {
        try(final Closer c = new Closer();
            final CvMat normalizedRefRow = new CvMat();) {
            if(histograms.type() != CvType.CV_32F)
                throw new IllegalArgumentException("Mat with set of histograms must be of type CV_32F");
            if(refHistogram.type() != CvType.CV_32F)
                throw new IllegalArgumentException("Mat with reference histogram must be of type CV_32F");

            try(final var c2 = new Closer();) {
                // Compute mean and stddev for reference row
                final MatOfDouble meanRef = c2.addMat(new MatOfDouble());
                final MatOfDouble stddevRef = c2.addMat(new MatOfDouble());
                Core.meanStdDev(refHistogram, meanRef, stddevRef);

                // Normalize the reference row
                Core.subtract(refHistogram, meanRef, normalizedRefRow);
                Core.divide(normalizedRefRow, stddevRef, normalizedRefRow);
            }

            // Compute dot products (Pearson correlation coefficients)
            try(final CvMat coefficients = new CvMat();
                final CvMat normalizedRefRow_transposed = CvMat.move(normalizedRefRow.reshape(1, 256));
                final var c2 = new Closer();) {
                Core.gemm(normalizedLargeMat, c2.addMat(normalizedRefRow_transposed), 1, nothing, 0, coefficients);

                // Extract the results (coefficients) into an array if needed
                final float[] result = new float[(int)coefficients.total()];
                coefficients.get(0, 0, result);

                return result;
            }
        }
    }

    private CorrelatesWith[] _match(final float[] refHist, final float threshold, final int maxNum, final int skipMe) {
        final float[] coef = multiHistogramCorrelationCoef(refHist);
        return Arrays.stream(topIndexes(coef, threshold, maxNum, skipMe))
            .mapToObj(i -> new CorrelatesWith(pathLookup[i], coef[i]))
            .toArray(CorrelatesWith[]::new);
    }

    private CorrelatesWith[] _match(final URI path, final Mat toSearchFor, final float threshold, final int maxNum, final boolean canDisposeOfImage) {
        final ImageDetails id = ImageDetails.calculate(Dims.make(toSearchFor), toSearchFor, canDisposeOfImage);
        return _match(id.histogram(), threshold, maxNum, -1);
    }

    private static CvMat calcNormalizedLargeMat(final Mat histograms) {
        try(var c2 = new Closer();
            final var meanLargeMat = new CvMat();
            final var stddevLargeMat = new CvMat();
            var normalizedLargeMat = new CvMat();) {
            // Compute mean and stddev for each row of the large matrix
            Sci.rowWiseMeanStdDev(histograms, meanLargeMat, stddevLargeMat);

            // Normalize the large matrix
            Core.subtract(histograms, meanLargeMat, normalizedLargeMat);
            Core.divide(normalizedLargeMat, stddevLargeMat, normalizedLargeMat);
            Core.multiply(normalizedLargeMat, new Scalar(1.0 / 256.0), normalizedLargeMat);
            return normalizedLargeMat.returnMe();
        }
    }
}
