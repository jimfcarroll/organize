package com.jiminger;

import static com.jiminger.FileRecord.readFileRecords;
import static com.jiminger.Utils.isParentUri;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.commands.DeleteCommand;

import net.dempsy.serialization.jackson.JsonUtils;

public class DirMerge {

//    private static Vfs vfs;

    private static final long KB = 1024;
    private static final long MEG = 1024 * KB;
    private static final long GIG = 1024 * MEG;

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    private static double toGig(final long bytes) {
        return (double)bytes / GIG;
    }

    private static double toMeg(final long bytes) {
        return (double)bytes / MEG;
    }

    private static double toKB(final long bytes) {
        return (double)bytes / KB;
    }

    private static String humanReadable(final long bytes) {
        if(bytes > GIG)
            return String.format("%.2f GB", toGig(bytes));
        if(bytes > MEG)
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
//        vfs = createVfs(conf.passwordsToTry);

        final String commandFileStr = args[1];
        final PrintStream commandOs = new PrintStream(new File(commandFileStr));

        final String srcDir = args[2];

        final String[] mergeDirs = args.length == 3 ? new String[] {}
            : IntStream.range(3, args.length)
                .mapToObj(i -> args[i])
                .toArray(String[]::new);

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

        System.out.println("Separating children of " + srcDir);

        final URI uri = new URI(srcDir);
        if(uri.getPath().endsWith("/"))
            throw new IllegalArgumentException("The given srcdir path \"" + srcDir + "\" should not end with a '/'");

        final List<FileRecord> toMove = new ArrayList<>();
        final List<FileRecord> others = new ArrayList<>(file2FileRecords.size());
        for(final var cur: file2FileRecords.values()) {
            if(isParentUri(uri, uncheck(() -> new URI(cur.path())))) {
                toMove.add(cur);
            } else
                others.add(cur);
        }

        if(toMove.size() == 0) {
            throw new IllegalStateException("It appears there are no children of \"" + uri + "\" among the entries in " +
                Stream.concat(Stream.of(conf.md5FileToWrite), Arrays.stream(Optional.ofNullable(conf.md5FilesToRead).orElse(new String[0])))
                    .collect(Collectors.toList()));
        }

        System.out.println("Calculating overall savings for " + srcDir);

        final Map<String, List<FileRecord>> md5ToOtherRecords = groupByMd5(others);
        // now let's scan all of the other records to find matching md5s and see how much data would be saved by a merge.
        long totalBytes = 0;
        long numFiles = 0;
        for(final var fr: toMove) {
            if(fr.size() < 16)
                System.out.println(fr.path() + " is too small at " + fr.size());
            else {
                final List<FileRecord> match = md5ToOtherRecords.get(fr.md5());
                if(match != null && match.size() > 0) {
                    numFiles += match.size();
                    totalBytes += fr.size() * match.size();
                    System.out.println(fr.path());
                    match.forEach(c -> System.out.println("   " + c.path()));
                } else
                    System.out.println("No match for " + fr.path());
            }
        }

        if(mergeDirs.length == 0)
            return;

        System.out.println("Merge would save " + humanReadable(totalBytes) + " across " + numFiles + " files");

        for(final String mergeDir: mergeDirs) {
            System.out.println("======================================");
            System.out.println(mergeDir);
            System.out.println("======================================");
//            final File merge1DirFile = vfs.toFile(new URI(mergeDir));
//            final URI merge1Uri = merge1DirFile.toURI();
            final URI merge1Uri = new URI(mergeDir);
            final List<FileRecord> merge1Records = childrenOf(merge1Uri, file2FileRecords.values());

            final Map<String, List<FileRecord>> srcMd5ToRecord = groupByMd5(toMove);
            long bytesOverlap = 0;
            long numFilesOverlap = 0;
            long bytesExtra = 0;
            long numFilesExtra = 0;
            for(final var cur: merge1Records) {
                final var srcRecord = srcMd5ToRecord.get(cur.md5());
                if(srcRecord != null) {
                    bytesOverlap += cur.size();
                    numFilesOverlap++;
                    commandOs.println(om.writeValueAsString(new DeleteCommand(DeleteCommand.TYPE, cur, srcRecord)));
                } else {
                    System.out.println("Remaining: " + cur.path());
                    bytesExtra += cur.size();
                    numFilesExtra++;
                }
            }

            System.out.println("After " + humanReadable(bytesOverlap) + " in " + numFilesOverlap + " files are merged, " + humanReadable(bytesExtra) + " in "
                + numFilesExtra + " files (" + String.format("%.2f", ((double)bytesExtra / (double)(bytesExtra + bytesOverlap)) * 100.0) + "%) will remain");
        }
    }

    public static Map<String, List<FileRecord>> groupByMd5(final Collection<FileRecord> toGroup) {
        final Map<String, List<FileRecord>> ret = new HashMap<>(toGroup.size());
        toGroup.forEach(fr -> ret.compute(fr.md5(), (k, v) -> {
            final List<FileRecord> cur = (v == null) ? new ArrayList<>() : v;
            cur.add(fr);
            return cur;
        }));
        return ret;
    }

    public static List<FileRecord> childrenOf(final URI parentURI, final Collection<FileRecord> among) {
        final var ret = new ArrayList<FileRecord>();
        for(final var cur: among) {
            if(isParentUri(parentURI, uncheck(() -> new URI(cur.path())))) {
                ret.add(cur);
            }
        }
        return ret;
    }
}
