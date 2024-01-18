package com.jiminger;

import static com.jiminger.FileRecord.readFileRecords;
import static com.jiminger.Utils.groupByBaseFnameSimilarity;
import static com.jiminger.Utils.isParentUri;
import static com.jiminger.VfsConfig.createVfs;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.Utils.ImageMatch;
import com.jiminger.commands.HandleImageDuplicate;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.vfs.Vfs;

public class ImageDupFinder {
    private static Vfs vfs;

    public static void usage() {
        System.err
            .println(
                "Usage: java -cp [classpath] " + ImageDupFinder.class.getName() + " path/to/config.json path/to/command-file uri/to/images /path/to/dest");
        System.exit(1);
    }

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 4)
            usage();

        final Config conf = Config.load(args[0]);
        vfs = createVfs(conf.passwordsToTry);

        final String commandFileStr = args[1];
        final PrintStream commandOs = new PrintStream(new File(commandFileStr));

        final URI rootToConsider = new URI(args[2]);

        final String dstDirStr = args[3];

        final File dstDir = new File(dstDirStr);
        if(dstDir.exists()) {
            System.err.println("The destination directory \"" + dstDirStr + "\" already exists.");
            System.exit(1);
        }

        // test that I can make the directory.
        if(!dstDir.mkdirs()) {
            System.err.println("The destination directory \"" + dstDirStr + "\" cannot be created.");
            System.exit(1);
        }

        // cleanup in case we fail before we write the directory
        dstDir.delete();

        final Map<String, FileRecord> file2FileRecords =

            readFileRecords(
                Stream.concat(Stream.of(conf.md5FileToWrite), Arrays.stream(Optional.ofNullable(conf.md5FilesToRead).orElse(new String[0])))
                    .toArray(String[]::new)

            ).stream()
                .collect(Collectors.toMap(fs -> fs.path(), fs -> fs, (fr1, fr2) -> {
                    if(fr1.equals(fr2))
                        return fr1;
                    throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
                }));

        // get all image records.
        final List<FileRecord> records = new LinkedList<>(file2FileRecords.values().stream()
            .filter(fr -> isParentUri(rootToConsider, uncheck(() -> new URI(fr.path()))))
            .filter(fr -> fr.mime() != null)
            .filter(fr -> fr.mime().startsWith("image/"))
            .sorted((fr1, fr2) -> fr1.path().compareTo(fr2.path()))
            .collect(Collectors.toList()));

        System.out.println("Total number of images: " + records.size());

        final List<List<FileRecord>> grouped = groupByBaseFnameSimilarity(records);

//        final ImageDisplay id = new ImageDisplay.Builder()
//            .windowName("Rotated")
//            // .dim(new Size(640, 480))
//            .build();
//        final ImageDisplay preRotId = new ImageDisplay.Builder()
//            .windowName("Pre Rot")
//            // .dim(new Size(640, 480))
//            .build();

        for(final var group: grouped) {
            if(group.size() < 2)
                continue;

            // if there's a .CR2 (we'll use the mime type to check) and there's more than 2, we need to check.
            final long largestSize = group.stream().mapToLong(f -> f.size()).max().orElse(0);
            final var sorted = new ArrayList<>(group.stream()
                .sorted((fr1, fr2) -> Long.compare(modifiedSize(fr2, largestSize), modifiedSize(fr1, largestSize)))
                .collect(Collectors.toList()));

            final var mainFr = sorted.get(0);
            if(mainFr.path().toLowerCase().endsWith(".cr2")) { // the largest is a CR2
                // if the size of the group is greater than 3 and ALL of the images are
                // the same then we want to remove the smallest until there's only 2, the cr2
                // and a single other.
                if(sorted.size() > 2) {
                    final List<ImageMatch> matches = Utils.matches(sorted, vfs);

                    // On a CR2 we leave one match alone
                    if(matches.size() > 1) {
                        final ImageMatch keeping = matches.remove(0);
                        System.out.println("=================================================");
                        System.out.println("KEEP:" + mainFr.path());
                        System.out.println("KEEP:" + String.format("%.2f", (keeping.cc() * 100)) + ":" + keeping.imageFr().path());
                        matches.stream()
                            .filter(im -> {
                                final boolean ret = shouldDelete(im);
                                if(!ret)
                                    System.out.println("NDEL:" + String.format("%.2f", (im.cc() * 100)) + ":" + im.imageFr().path());
                                return ret;
                            })
                            .peek(e -> System.out.println("DEL :" + String.format("%.2f", (e.cc() * 100)) + ":" + e.imageFr().path()))
                            .map(m -> new HandleImageDuplicate(HandleImageDuplicate.TYPE, m.imageFr(), mainFr, m.cc()))
                            .forEach(hid -> commandOs.println(uncheck(() -> om.writeValueAsString(hid))));
                    }
                } else {
                    System.out.println("CR2 group " + mainFr.path() + " has only " + sorted.size() + " in the group.");
                    continue;
                }
            } else {
                // non-cr2 group
                final List<ImageMatch> matches = Utils.matches(sorted, vfs);

                System.out.println("=================================================");
                System.out.println("KEEP:" + mainFr.path());
                matches.stream()
                    .filter(im -> {
                        final boolean ret = shouldDelete(im);
                        if(!ret)
                            System.out.println("NDEL:" + String.format("%.2f", (im.cc() * 100)) + ":" + im.imageFr().path());
                        return ret;
                    })
                    .peek(e -> System.out.println("DEL :" + String.format("%.2f", (e.cc() * 100)) + ":" + e.imageFr().path()))
                    .map(m -> new HandleImageDuplicate(HandleImageDuplicate.TYPE, m.imageFr(), mainFr, m.cc()))
                    .forEach(hid -> commandOs.println(uncheck(() -> om.writeValueAsString(hid))));
            }

        }
    }

    private static long modifiedSize(final FileRecord fr, final long largest) {
        return (fr.path().contains("_shotwell") || fr.path().contains("_embedded")) ? (fr.size() - largest) : fr.size();
    }

    private static boolean shouldDelete(final ImageMatch im) {
        final String imPathLc = im.imageFr().path().toLowerCase();
        return im.cc() > 0.996 || ((imPathLc.endsWith("_embedded.jpg") || imPathLc.endsWith("_shotwell.jpg")) && im.cc() > 0.95);
    }

}
