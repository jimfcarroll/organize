package com.jiminger;

import static com.jiminger.records.FileRecord.groupByMd5;
import static com.jiminger.records.FileRecordLmdb.makeFileRecordsManager;
import static com.jiminger.utils.Utils.isParentUri;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.commands.DeleteCommand;
import com.jiminger.records.FileRecord;
import com.jiminger.records.FileRecordDb;
import com.jiminger.records.FileRecordLmdb;

import net.dempsy.util.MutableInt;

public class DirMerge {

    private static final long KB = 1024L;
    private static final long MB = 1024L * KB;
    private static final long GB = 1024L * MB;
    private static final long TB = 1024L * GB;

    private static ObjectMapper om = FileRecord.makeStandardObjectMapper();

    private static double toTB(final long bytes) {
        return (double)bytes / TB;
    }

    private static double toGig(final long bytes) {
        return (double)bytes / GB;
    }

    private static double toMeg(final long bytes) {
        return (double)bytes / MB;
    }

    private static double toKB(final long bytes) {
        return (double)bytes / KB;
    }

    private static String humanReadable(final long bytes) {
        if(bytes > TB)
            return String.format("%.2f TB", toTB(bytes));
        if(bytes > GB)
            return String.format("%.2f GB", toGig(bytes));
        if(bytes > MB)
            return String.format("%.2f MB", toMeg(bytes));
        if(bytes > KB)
            return String.format("%.2f KB", toKB(bytes));
        else
            return "" + bytes + " B";
    }

    public static void usage() {
        System.err
            .println("Usage: java -cp [classpath] " + DirMerge.class.getName() + " path/to/config.json path/to/command-file.json srcdir-uri dirsToMerge...");
        System.exit(1);
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length < 3)
            usage();

        final Config conf = Config.load(args[0]);

        final String commandFileStr = args[1];
        try(final PrintStream commandOs = new PrintStream(new File(commandFileStr));) {

            final String srcDir = args[2];

            final String[] mergeDirs = args.length == 3 ? new String[] {}
                : IntStream.range(3, args.length)
                    .mapToObj(i -> args[i])
                    .toArray(String[]::new);

            try(final var file2FileRecords = makeFileRecordsManager(conf.md5FileToWrite, conf.md5FilesToRead);) {

                System.out.println("Separating children of " + srcDir + " from the overall set of file records.");

                final URI uri = new URI(srcDir);
                if(uri.getPath().endsWith("/"))
                    throw new IllegalArgumentException("The given srcdir path \"" + srcDir + "\" should not end with a '/'");

                final List<FileRecord> toMove = new ArrayList<>();
                final List<FileRecord> others = new ArrayList<>();
                file2FileRecords.stream()
                    .forEach(cur -> {
                        if(isParentUri(uri, cur.uri()))
                            toMove.add(cur);
                        else
                            others.add(cur);
                    });

                if(toMove.size() == 0) {
                    throw new IllegalStateException("It appears there are no children of \"" + uri + "\" among the entries in " +
                        Stream.concat(Stream.of(conf.md5FileToWrite), Arrays.stream(Optional.ofNullable(conf.md5FilesToRead).orElse(new String[0])))
                            .collect(Collectors.toList()));
                }

                System.out.println("Calculating overall savings for " + srcDir);

                final FileRecordDb<String> md5ToOtherRecords = makeFileRecordsManager(new FileRecordLmdb.Md5Index(), conf.md5FileToWrite, conf.md5FilesToRead);
                // now let's scan all of the other records to find matching md5s and see how much data would be saved by a merge.
                long totalBytes = 0;
                long numFiles = 0;
                for(final var fr: toMove) {
                    if(fr.size() < 16)
                        System.out.println(fr.uri() + " is too small at " + fr.size());
                    else {
                        final List<FileRecord> match = Arrays.asList(md5ToOtherRecords.find(fr.md5()));
                        if(match != null && match.size() > 0) {
                            numFiles += match.size();
                            totalBytes += fr.size() * match.size();
                            System.out.println(fr.uri());
                            match.forEach(c -> System.out.println("   " + c.uri()));
                        } else
                            System.out.println("No match for " + fr.uri());
                    }
                }

                if(mergeDirs.length == 0)
                    return;

                System.out.println("Merge would save " + humanReadable(totalBytes) + " across " + numFiles + " files");

                for(final String mergeDir: mergeDirs) {
                    System.out.println("======================================");
                    System.out.println(mergeDir);
                    System.out.println("======================================");
                    final URI merge1Uri = new URI(mergeDir);

                    final Map<String, List<FileRecord>> srcMd5ToRecord = groupByMd5(toMove);
                    final var bytesOverlap = new MutableInt(0);
                    final var numFilesOverlap = new MutableInt(0);
                    final var bytesExtra = new MutableInt(0);
                    final var numFilesExtra = new MutableInt(0);
                    file2FileRecords.stream()
                        .filter(cur -> isParentUri(merge1Uri, cur.uri()))
                        .forEach(cur -> uncheck(() -> {
                            final var srcRecord = srcMd5ToRecord.get(cur.md5());
                            if(srcRecord != null) {
                                bytesOverlap.val += cur.size();
                                numFilesOverlap.val++;
                                commandOs.println(om.writeValueAsString(new DeleteCommand(DeleteCommand.TYPE, cur, srcRecord)));
                            } else {
                                System.out.println("Remaining: " + cur.uri());
                                bytesExtra.val += cur.size();
                                numFilesExtra.val++;
                            }
                        }));

                    System.out
                        .println(
                            "After " + humanReadable(bytesOverlap.val) + " in " + numFilesOverlap.val + " files are merged, " + humanReadable(bytesExtra.val)
                                + " in " + numFilesExtra.val + " files ("
                                + String.format("%.2f", (bytesExtra.val / (double)(bytesExtra.val + bytesOverlap.val)) * 100.0) + "%) will remain");
                }
            }
        }
    }
}
