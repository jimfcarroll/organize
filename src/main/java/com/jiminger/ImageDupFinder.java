package com.jiminger;

import static com.jiminger.FileRecord.readFileRecords;
import static com.jiminger.VfsConfig.createVfs;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dempsy.vfs.Vfs;

public class ImageDupFinder {
    private static Vfs vfs;

    public static void usage() {
        System.err
            .println(
                "Usage: java -cp [classpath] " + ImageDupFinder.class.getName() + " path/to/config.json");
        System.exit(1);
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 1)
            usage();

        final Config conf = Config.load(args[0]);
        vfs = createVfs(conf.passwordsToTry);

        final String commandFileStr = args[1];
        final PrintStream commandOs = new PrintStream(new File(commandFileStr));

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
        final List<FileRecord> records = file2FileRecords.values().stream()
            .filter(fr -> fr.mime() != null)
            .filter(fr -> fr.mime().startsWith("image/"))
            .collect(Collectors.toList());

        System.out.println("Total number of images: " + records.size());

    }

}
