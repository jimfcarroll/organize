//package com.jiminger;
//
//import static com.jiminger.VfsConfig.createVfs;
//import static com.jiminger.records.FileRecord.mergedFileRecords;
//import static com.jiminger.utils.Utils.groupByBaseFnameSimilarity;
//import static com.jiminger.utils.Utils.isParentUri;
//import static net.dempsy.util.Functional.uncheck;
//
//import java.io.File;
//import java.io.PrintStream;
//import java.net.URI;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.jiminger.commands.HandleImageDuplicate;
//import com.jiminger.records.FileRecord;
//import com.jiminger.utils.Utils;
//import com.jiminger.utils.Utils.ImageMatch;
//
//public class ImageDupFinder {
//
//    public static void usage() {
//        System.err
//            .println(
//                "Usage: java -cp [classpath] " + ImageDupFinder.class.getName() + " path/to/config.json path/to/command-file uri/to/images");
//        System.exit(1);
//    }
//
//    private static ObjectMapper om = FileRecord.makeStandardObjectMapper();
//    private static Comparator<FileRecord> FR_COMP = Utils.getGroupingCompliantFileRecordComparator();
//
//    static public void main(final String[] args) throws Exception {
//        if(args == null || args.length != 3)
//            usage();
//
//        final Config conf = Config.load(args[0]);
//        final var vfs = createVfs(conf.passwordsToTry);
//
//        final String commandFileStr = args[1];
//        try(final PrintStream commandOs = new PrintStream(new File(commandFileStr));) {
//
//            final URI rootToConsider = new URI(args[2]);
//
//            final Map<URI, FileRecord> file2FileRecords = mergedFileRecords(conf.md5FileToWrite, conf.md5FilesToRead);
//
//            // get all image records.
//            final List<FileRecord> records = new LinkedList<>(file2FileRecords.values().stream()
//                .filter(fr -> isParentUri(rootToConsider, fr.uri()))
//                .filter(fr -> fr.mime() != null)
//                .filter(fr -> fr.mime().startsWith("image/"))
//                .sorted((fr1, fr2) -> FR_COMP.compare(fr1, fr2))
//                .collect(Collectors.toList()));
//
//            System.out.println("Total number of images: " + records.size());
//
//            // Group the files by name and parent so we can find cheesey duplicates
//            {
//                final List<List<FileRecord>> grouped = groupByBaseFnameSimilarity(records);
//
////        final ImageDisplay id = new ImageDisplay.Builder()
////            .windowName("Rotated")
////            // .dim(new Size(640, 480))
////            .build();
////        final ImageDisplay preRotId = new ImageDisplay.Builder()
////            .windowName("Pre Rot")
////            // .dim(new Size(640, 480))
////            .build();
//
//                for(final var group: grouped) {
//                    if(group.size() < 2)
//                        continue;
//
//                    // if there's a .CR2 (we'll use the mime type to check) and there's more than 2, we need to check.
//                    final long largestSize = group.stream().mapToLong(f -> f.size()).max().orElse(0);
//                    final var sorted = new ArrayList<>(group.stream()
//                        .sorted((fr1, fr2) -> Long.compare(modifiedSize(fr2, largestSize), modifiedSize(fr1, largestSize)))
//                        .collect(Collectors.toList()));
//
//                    final var mainFr = sorted.get(0);
//                    if(mainFr.uri().toString().toLowerCase().endsWith(".cr2")) { // the largest is a CR2
//                        // if the size of the group is greater than 2 and ALL of the images are
//                        // the same then we want to remove the smallest until there's only 2, the cr2
//                        // and a single other.
//                        if(sorted.size() > 2) {
//                            final List<ImageMatch> matches = Utils.matches(sorted, vfs, conf.avoidMemMap);
//
//                            // On a CR2 we leave one match alone
//                            if(matches.size() > 1) {
//                                final ImageMatch keeping = matches.remove(0);
//                                System.out.println("=================================================");
//                                System.out.println("KEEP:" + mainFr.uri());
//                                System.out.println("KEEP:" + String.format("%.2f", (keeping.cc() * 100)) + ":" + keeping.imageFr().uri());
//                                matches.stream()
//                                    .filter(im -> {
//                                        final boolean ret = shouldDelete(im);
//                                        if(!ret)
//                                            System.out.println("NDEL:" + String.format("%.2f", (im.cc() * 100)) + ":" + im.imageFr().uri());
//                                        return ret;
//                                    })
//                                    .peek(e -> System.out.println("DEL :" + String.format("%.2f", (e.cc() * 100)) + ":" + e.imageFr().uri()))
//                                    .map(m -> new HandleImageDuplicate(HandleImageDuplicate.TYPE, m.imageFr(), mainFr, m.cc()))
//                                    .forEach(hid -> commandOs.println(uncheck(() -> om.writeValueAsString(hid))));
//                            }
//                        } else {
//                            System.out.println("CR2 group " + mainFr.uri() + " has only " + sorted.size() + " in the group.");
//                            continue;
//                        }
//                    } else {
//                        // non-cr2 group
//                        final List<ImageMatch> matches = Utils.matches(sorted, vfs, conf.avoidMemMap);
//
//                        System.out.println("=================================================");
//                        System.out.println("KEEP:" + mainFr.uri());
//                        matches.stream()
//                            .filter(im -> {
//                                final boolean ret = shouldDelete(im);
//                                if(!ret)
//                                    System.out.println("NDEL:" + String.format("%.2f", (im.cc() * 100)) + ":" + im.imageFr().uri());
//                                return ret;
//                            })
//                            .peek(e -> System.out.println("DEL :" + String.format("%.2f", (e.cc() * 100)) + ":" + e.imageFr().uri()))
//                            .map(m -> new HandleImageDuplicate(HandleImageDuplicate.TYPE, m.imageFr(), mainFr, m.cc()))
//                            .forEach(hid -> commandOs.println(uncheck(() -> om.writeValueAsString(hid))));
//                    }
//                }
//            }
//        }
//    }
//
//    private static long modifiedSize(final FileRecord fr, final long largest) {
//        return (fr.uri().toString().contains("_shotwell") || fr.uri().toString().contains("_embedded")) ? (fr.size() - largest) : fr.size();
//    }
//
//    private static boolean shouldDelete(final ImageMatch im) {
//        final String imPathLc = im.imageFr().uri().toString().toLowerCase();
//        return im.cc() > 0.996 || ((imPathLc.endsWith("_embedded.jpg") || imPathLc.endsWith("_shotwell.jpg")) && im.cc() > 0.95);
//    }
//
//}
