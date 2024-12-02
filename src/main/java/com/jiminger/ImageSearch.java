//package com.jiminger;
//
//import static com.jiminger.VfsConfig.createVfs;
//import static com.jiminger.records.FileRecord.readFileRecords;
//import static com.jiminger.records.ImageCorrelation.readImageCorrelationRecordsToManager;
//import static com.jiminger.utils.ImageUtils.DONT_RESIZE;
//
//import java.util.ArrayList;
//
//import com.jiminger.records.ImageDetails;
//import com.jiminger.utils.FileAccess;
//import com.jiminger.utils.HistogramBasedImageIndex;
//import com.jiminger.utils.ImageUtils;
//
//import ai.kognition.pilecv4j.image.CvMat;
//
//public class ImageSearch {
//
//    public static void usage() {
//        System.err
//            .println(
//                "Usage: java -cp [classpath] " + ImageSearch.class.getName() + " path/to/config.json");
//        System.exit(1);
//    }
//
//    // rules:
//    // keep jp2 (if pixel perfect copy)
//    // else:
//    // -if pixel perfect:
//    // ---keep CR2 and dng
//    // ---keep CR2 over dng
//    // ---keep largest
//    // ---if keep remains, auto move others
//    // -else if not pixel perfect:
//    // -- all must apply to auto move:
//    // -----threshold very high (e.g. > 0.995) (looking for reencodes of original)
//    // -----keep oldest
//    // -----keep highest resolution
//    // -----if keep remains, auto move others
//    // default:
//    // --ask.
//    //
//    // move means copy to a common directory with the full path
//
//    static public void main(final String[] args) throws Exception {
//        if(args == null || args.length != 2)
//            usage();
//
//        final Config conf = Config.load(args[0]);
//        final var vfs = createVfs(conf.passwordsToTry);
//
//        System.out.println("Loading Manager for existing records.");
//        final var frRecs = readImageCorrelationRecordsToManager(conf.imageCorrelationsFileToWrite, conf.imageCorrelationsFilesToRead, null);
//
//        final var fileRecords = new ArrayList<>(readFileRecords(conf.md5FileToWrite, conf.md5FilesToRead, ImageDetails::hasHistogram));
//
//        final int rows = fileRecords.size();
//        try(FileAccess fa = new FileAccess(fSpec, conf.avoidMemMap);
//            CvMat refImg = ImageUtils.loadImage(fa, DONT_RESIZE);
//            var index = new HistogramBasedImageIndex(fileRecords);) {
//            System.out.println("Building histogram DB with " + rows + " entries ...");
//
//            System.out.println("... Done");
//
//            final ImageDetails id = ImageDetails.calculate(refImg);
//            final float[] coef = index.multiHistogramCorrelationCoef(id.histogram());
//            int maxIndex = -1;
//            float maxVal = Float.NEGATIVE_INFINITY;
//            for(int i = 0; i < coef.length; i++) {
//                if(coef[i] > maxVal) {
//                    maxVal = coef[i];
//                    maxIndex = i;
//                }
//            }
//
//            System.out.println("Closest Match: " + fileRecords.get(maxIndex));
//        }
//
//    }
//
//}
