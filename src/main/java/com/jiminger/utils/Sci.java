package com.jiminger.utils;

import static net.dempsy.util.Functional.chain;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.drew.imaging.ImageProcessingException;
import com.jiminger.records.FileRecord;
import com.jiminger.utils.Utils.ImageMatch;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;

public class Sci {
    static {
        CvMat.initOpenCv();
    }

    private static final Mat nothing = new Mat();

    public static CvMat normalizeChannels(final CvMat src, final double alpha, final double beta) {
        try(final Closer closer = new Closer();) {
            final List<Mat> channels = new ArrayList<Mat>();
            Core.split(src, channels);

            channels.forEach(m -> closer.addMat(m));

            for(int i = 0; i < channels.size(); i++) {
                final Mat channel = channels.get(i);
                Core.normalize(channel, channel, alpha, beta, Core.NORM_MINMAX);
            }

            try(final CvMat normalizedImg = new CvMat();) {
                Core.merge(channels, normalizedImg);
                return normalizedImg.returnMe();
            }
        }
    }

    public static List<ImageMatch> matches(final FileRecord cMain, final List<FileRecord> sorted, final Vfs vfs, final boolean avoidMemMap, final int maxDim,
        final boolean normalizeChannels, final double alpha, final double beta) throws ImageProcessingException, IOException, URISyntaxException {

        if(cMain == null || sorted == null || sorted.size() == 0)
            return Collections.emptyList();

        try(var cMainFa = cMain.fileAccess(vfs, avoidMemMap);
            CvMat mainMat = ImageUtils.loadImageWithCorrectOrientation(cMainFa, maxDim);) {

            if(mainMat == null || mainMat.dataAddr() == 0L)
                return Collections.emptyList();

            final List<ImageMatch> matches = new ArrayList<>();

            for(int i = 0; i < sorted.size(); i++) {
                final var curFr = sorted.get(i);
                System.out.println("CALCCC:" + curFr.uri());
                try(final var cur = curFr.fileAccess(vfs, avoidMemMap);
                    final CvMat mat = ImageUtils.loadImageWithCorrectOrientation(cur, maxDim);) {
                    if(mat == null || mat.dataAddr() == 0L)
                        continue;

                    final double cc = correlationCoef(mainMat, mat, normalizeChannels, alpha, beta,
                        i == (sorted.size() - 1), // we can delete the source image if we're on the last corr coef calc
                        true);
                    matches.add(new ImageMatch(cc, curFr));
                }
            }
            return matches;
        }
    }

    private static CvMat convertIfNeeded(final Mat toConvert, final int referenceType, final boolean canDisposeOfImage) {
        if(toConvert.type() == referenceType) {
            final var depth = toConvert.depth();
            if(depth == CvType.CV_8U || depth == CvType.CV_32F)
                return CvMat.shallowCopy(toConvert);
            // we need to convert to F32
            try(CvMat ret = chain(new CvMat(), m -> toConvert.convertTo(m, CvType.CV_32F));) {
                return ret.returnMe();
            }
        }
        // if the number of channels are the same then just convert to F32.
        if(toConvert.channels() == CvType.channels(referenceType)) {
            try(CvMat ret = chain(new CvMat(), m -> toConvert.convertTo(m, CvType.CV_32F));) {
                return ret.returnMe();
            }
        }
        // okay, the depth is different. We need to convert them both to C1 F32.
        try(CvMat ret = sumThenNormalizeChannels(toConvert, 0.0, 1.0, canDisposeOfImage);) {
            return ret.returnMe();
        }
    }

    public static double correlationCoef(final CvMat mat1p, final CvMat mat2p, final boolean normalizeChannels, final double alpha, final double beta,
        final boolean canDisposeOfImage1, final boolean canDisposeOfImage2) {
        try(Closer outer = new Closer();) {
            final CvMat nimg1;
            final CvMat nimg2;
            try(Closer interim = new Closer();) {
                final CvMat img1;
                final CvMat img2;

                // Ensure images are of the same size
                final int mat1pType = mat1p.type();
                final int mat2pType = mat2p.type();
                try(CvMat mat1 = convertIfNeeded(mat1p, mat2pType, canDisposeOfImage1);
                    CvMat mat2 = convertIfNeeded(mat2p, mat1pType, canDisposeOfImage2);) {

                    if(!mat1.size().equals(mat2.size())) {
                        final CvMat larger = mat1.total() > mat2.total() ? mat1 : mat2;
                        final CvMat smaller = mat1.total() > mat2.total() ? mat2 : mat1;

                        img1 = interim.add(CvMat.shallowCopy(smaller));
                        img2 = interim.add(new CvMat());
                        Imgproc.resize(larger, img2, smaller.size(), 0, 0, Imgproc.INTER_AREA);
                    } else {
                        img1 = interim.add(CvMat.shallowCopy(mat1));
                        img2 = interim.add(CvMat.shallowCopy(mat2));
                    }
                }

                {
                    final double divisor = 341.0;
                    final int dimToUse = Math.min(Math.min(img1.rows(), img1.cols()), Math.min(img2.rows(), img2.cols()));
                    final int ksize = ((int)Math.round(Math.max(3.0, (dimToUse / divisor))) << 1) + 1;
                    Imgproc.GaussianBlur(img1, img1, new Size(ksize, ksize), 0, 0);
                    Imgproc.GaussianBlur(img2, img2, new Size(ksize, ksize), 0, 0);
                }

                if(normalizeChannels) {
                    nimg1 = outer.add(normalizeChannels(img1, alpha, beta));
                    nimg2 = outer.add(normalizeChannels(img2, alpha, beta));
                } else {
                    nimg1 = outer.add(CvMat.shallowCopy(img1));
                    nimg2 = outer.add(CvMat.shallowCopy(img2));
                }
            }
            try(final CvMat result = new CvMat();) {
                Imgproc.matchTemplate(nimg1, nimg2, result, normalizeChannels ? Imgproc.TM_CCOEFF_NORMED : Imgproc.TM_CCOEFF);

                final double correlationCoefficient = Core.minMaxLoc(result).maxVal;
                return correlationCoefficient;
            }
        }
    }

//    public static double correlationCoef(final CvMat[] mains, final CvMat mat, final boolean normalizeChannels, final double alpha, final double beta) {
//        return Arrays.stream(mains)
//            .mapToDouble(m -> correlationCoef(m, mat, normalizeChannels, alpha, beta, canDisposeOfImage))
//            .max()
//            .orElse(0);
//    }

    public static float[] computeNormalizedHistogram(final Mat image, final int numBins, final double alpha, final double beta,
        final boolean canDisposeOfImage) {

        try(var histMat = computeNormalizedHistogramAsMat(image, numBins, alpha, beta, canDisposeOfImage);
            Closer closer = new Closer();) {
            // Check if the input Mat has 3 channels
            if(histMat.rows() != numBins || histMat.cols() != 1 && histMat.channels() != 1)
                throw new IllegalArgumentException("Calculated histogram Mat should have had 1 channel, " + numBins + " rows, and 1 col:" + histMat.toString());

            // Prepare the 2D float array
            final int rows = histMat.rows();  // Number of bins (e.g., 256)
            final float[] result = new float[rows];

            // Iterate over each channel and fill the result array
            for(int i = 0; i < rows; i++)
                result[i] = (float)histMat.get(i, 0)[0]; // Extract the value

            return result;
        }
    }

    public static CvMat computeNormalizedHistogramAsMat(final Mat image, final int numBins, final double alpha, final double beta,
        final boolean canDisposeOfImage) {
        try(final CvMat hist = new CvMat();
            final CvMat fimage = sumThenNormalizeChannels(image, alpha, beta, canDisposeOfImage);
            var c = new Closer();) {

            final MatOfInt histSize = c.addMat(new MatOfInt(numBins));
            final MatOfFloat ranges = c.addMat(new MatOfFloat(0, numBins));

            Imgproc.calcHist(
                List.of(fimage), // List of channels
                new MatOfInt(0), // Histogram for the 0th (and only) channel
                nothing, // No mask
                hist, // Output histogram
                histSize, // Histogram size (bins)
                ranges // Value range
            );

            // Normalize the histogram
            Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);
            return hist.returnMe();
        }
    }

    public static double histogramCorrelationCoef(final float[] hist1, final float[] hist2) {
        // this may throw an NPE
        final int len = hist1.length;

        // this may also throw an NPE
        if(len != hist2.length)
            throw new IllegalArgumentException("To calculate the Pearson correlation coef betwen two float arrays, they must be the same length. One is "
                + len + " and the other is " + hist2.length);

        try(CvMat hMat1 = new CvMat(len, 1, CvType.CV_32F);
            CvMat hMat2 = new CvMat(len, 1, CvType.CV_32F);) {
            hMat1.put(0, 0, hist1);
            hMat2.put(0, 0, hist2);
            return histogramCorrelationCoef(hMat1, hMat2);
        }
    }

    public static double histogramCorrelationCoef(final Mat hist1, final Mat hist2) {
        // Compare using correlation
        return Imgproc.compareHist(hist1, hist2, Imgproc.HISTCMP_CORREL);
    }

    /**
     * This will reduce the source mat into a row-wise mean and std deviation. The resulting Mats
     * will be the same number of rows as the source Mat but a single column. Each value for each
     * corresponding row in the source will be the mean or std deviation of the source row.
     *
     * @param src - the mat to calculate the mean and std deviation of each row independently
     * @param dstMeanMat - the resulting calculated statistical mean of each row independently
     * @param dstStdDevMat - the resulting calculated statistical standard deviation of each row independently
     */
    public static void rowWiseMeanStdDev(final Mat src, final CvMat dstMeanMat, final CvMat dstStdDevMat) {
        final int rows = src.rows();
        final int cols = src.cols();
        try(CvMat tMean = new CvMat(rows, 1, CvType.CV_32FC1);
            CvMat tStd = new CvMat(rows, 1, CvType.CV_32FC1);
            Closer c = new Closer();) {

            final MatOfDouble mean = c.addMat(new MatOfDouble());
            final MatOfDouble stddev = c.addMat(new MatOfDouble());

            for(int i = 0; i < rows; i++) {
                final Mat row = src.row(i); // Extract each row
                Core.meanStdDev(row, mean, stddev); // Calculate for the row
                tMean.put(i, 0, mean.get(0, 0)); // Store the mean
                tStd.put(i, 0, stddev.get(0, 0)); // Store the stddev
            }
            CvMat.reassign(dstMeanMat, c.add(new CvMat()));
            CvMat.reassign(dstStdDevMat, c.add(new CvMat()));
            Core.repeat(tMean, 1, cols, dstMeanMat);
            Core.repeat(tStd, 1, cols, dstStdDevMat);
        }
    }

    private static final int[] ORIGIN = new int[] {0,0};

    private static CvMat sumThenNormalizeChannels(final Mat image, final double alpha, final double beta, final boolean canDisposeOfImage) {
        // Ensure the image has multiple channels
        if(image.channels() == 1) {
            try(var fmat = new CvMat();
                var toUse = canDisposeOfImage ? CvMat.move(image) : CvMat.shallowCopy(image);) {
                Core.normalize(toUse, fmat, alpha, beta, Core.NORM_MINMAX, CvType.CV_32F);
                return fmat.returnMe();
            }
        }

        // we're going to try to do this in a mem efficient manner.
        try(CvMat weightsMat = new CvMat(1, image.channels(), CvType.CV_32FC1);
            CvMat src = canDisposeOfImage ? CvMat.move(image) : CvMat.shallowCopy(image);
            CvMat sum = new CvMat();) {
            final double[] weights = new double[src.channels()];
            for(int i = 0; i < weights.length; i++)
                weights[i] = 1.0 / weights.length;
            weightsMat.put(ORIGIN, weights);
            Core.transform(src, sum, weightsMat);
            Core.normalize(sum, sum, alpha, beta, Core.NORM_MINMAX, CvType.CV_32F);
            return sum.returnMe();
        }
    }
}
