package com.jiminger;

import static com.jiminger.FileRecord.readFileRecords;
import static com.jiminger.VfsConfig.createVfs;
import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.HexStringUtil.bytesToHex;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.Config.FileReference;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.util.Functional;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class Md5File {
    // private static final Logger LOGGER = LoggerFactory.getLogger(Md5File.class);

    private static Vfs vfs;

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getName() + " path/to/config.json");
    }

    static public void main(final String[] args) throws Exception {

        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);
            vfs = createVfs(c.passwordsToTry);
            c.applyGlobalSettings();

            try(OpContext oc = vfs.operation();) {
                makeMd5File(c.md5FileToWrite, c.md5FilesToRead,

                    c.directoriesToScan

                    , c.failedFile, c.deleteEmptyDirs, c.md5FileFilter(),
                    c.md5FileWriteLineBuffered, vfs, c.recurseIntoArchives);
            }
        }
    }

    public static void makeMd5File(final String md5FileToWrite, final String[] md5FilesToRead, final FileReference[] directoriesToScan, final String failedFile,
        final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered, final Vfs vfs, final boolean recurseIntoArchives)
        throws IOException {
        final Map<String, FileRecord> file2FileRecords =

            readFileRecords(
                Stream.concat(Stream.of(md5FileToWrite), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new)

            ).stream()
                .collect(Collectors.toMap(fs -> fs.path(), fs -> fs, (fr1, fr2) -> {
                    if(fr1.equals(fr2))
                        return fr1;
                    throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
                }));

        final File md5File = new File(md5FileToWrite);
        if(failedFile != null)
            System.out.println("Writing errors to: " + failedFile);
        try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile)))
            : new PrintWriter(System.err);
            PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

            final long startTime = System.currentTimeMillis();
            // pass to calc md5
            recheck(() -> Arrays.stream(directoriesToScan)
                .map(fr -> fr.uri())
                .forEach(uri -> uncheck(() -> {
                    try(var sub = vfs.operation();) {
                        final Path path = sub.toPath(uri);
                        doMd5(md5os, failed, new FileSpec(path), file2FileRecords, deleteEmtyDirs, md5FileFilter, lineBuffered, sub, recurseIntoArchives);
                    }
                })));

            System.out.println("Finished Clean: " + (System.currentTimeMillis() - startTime) + " millis");
        }
    }

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    private static void doMd5(final PrintWriter md5os, final PrintWriter failed, final FileSpec fSpec, final Map<String, FileRecord> existing,
        final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered, final OpContext oc,
        final boolean recurseIntoArchives) throws IOException {

        final BasicFileAttributes attr = fSpec.getAttr();
        if(attr != null && attr.isSymbolicLink()) {
            System.out.println("SKIPPING: Symbolic link: " + fSpec.uri());
            return;
        }

        if(!fSpec.exists())
            throw new FileNotFoundException("File " + fSpec + " doesn't exist.");

        if(!md5FileFilter.test(fSpec)) {
            System.out.println("SKIPPING: " + fSpec.uri() + "(" + fSpec.mimeType() + ")");
            return;
        }

        if(!fSpec.isDirectory()) {
            if(attr == null || !attr.isOther()) {
                final FileRecord existingFr = Optional.ofNullable(existing).map(e -> e.get(fSpec.uri().toString())).orElse(null);

                final FileRecord fr;
                if(existingFr != null && existingFr.isComplete()) {
                    System.out.println("COPYING : " + fSpec.uri());
                    fSpec.setMime(existingFr.mime()); // otherwise it will read the mime by opening a stream in order to determine if it's recursable
                    fr = existingFr;
                } else {
                    System.out.println("SCANNING: " + fSpec.uri());
                    try(var header = fSpec.preserveHeader(1024 * 1024);) {
                        final String md5 = (existingFr != null && existingFr.md5() != null) ? existingFr.md5() : bytesToHex(MD5.hash(fSpec));
                        final String mime = (existingFr != null && existingFr.mime() != null) ? existingFr.mime() : fSpec.mimeType("UNKNOWN");
                        fr = new FileRecord(fSpec.uri().toString(), fSpec.size(), mime, fSpec.lastModifiedTime(), md5);
                    }
                }
                md5os.println(om.writeValueAsString(fr));

                if(lineBuffered)
                    md5os.flush();
            } else {
                if(attr.isOther()) {
                    System.out.println("SKIPPING: Unknown file type (pipe, socket, device, etc.): " + fSpec.uri());
                    fSpec.setMime(""); // there is no mime for this.
                    return; // can't let this fSpec have isRecursable called since it will try to read the file.
                } else
                    throw new IOException("Illegal file type: " + fSpec);
            }
        }

        if((recurseIntoArchives && fSpec.isRecursable()) || (!recurseIntoArchives && fSpec.isDirectory())) {

            final boolean isArchiveOrCompressed = !fSpec.isDirectory(); // if it's recursable but NOT a directory, then it's an archive or it's compressed

            System.out.println("LISTING : " + fSpec.uri());

            final FileSpec[] dirContents;
            // =========================================================================
            // Since fSpec could be an archive or compressed file and that determination
            // was made using Tiki's mime type, and Tiki's mime type can be confused by
            // file extensions (e.g. ".zip" files that are not pkzip compressed files), then
            // we want to detect that and move on.
            // =========================================================================
            {
                try {
                    dirContents = fSpec.listSorted(oc);
                } catch(final IOException ze) {
                    if(!isArchiveOrCompressed)
                        throw ze;
                    failed.println(fSpec.uri());
                    ze.printStackTrace(failed);
                    failed.flush();
                    System.out.println("ERROR: LISTING: " + fSpec.uri());
                    ze.printStackTrace();
                    return; // don't continue on.
                }
            }
            // =========================================================================

            if(dirContents == null || dirContents.length == 0) {
                if(deleteEmtyDirs) {
                    System.out.println("Empty directory: \"" + fSpec + "\"");
                    fSpec.delete();
                }
            } else {
                try {
                    Functional.<IOException>recheck(
                        () -> Arrays.stream(dirContents)
                            .forEach(f -> uncheck(() -> {
                                try(OpContext subCtx = oc.sub();) {
                                    doMd5(md5os, failed, f, existing, deleteEmtyDirs, md5FileFilter, lineBuffered, subCtx, recurseIntoArchives);
                                }
                            })));
                } catch(final IOException ze) {
                    if(!isArchiveOrCompressed)
                        throw ze;
                    failed.println(fSpec.uri());
                    ze.printStackTrace(failed);
                    failed.flush();
                    System.out.println("ERROR: LISTING: " + fSpec.uri());
                    ze.printStackTrace();
                    return; // don't continue on.
                }
            }
        }
    }
}
