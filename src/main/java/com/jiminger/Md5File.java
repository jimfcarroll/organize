package com.jiminger;

import static com.jiminger.VfsConfig.createVfs;
import static com.jiminger.records.FileRecord.makeFileRecordsManager;
import static net.dempsy.util.Functional.chain;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.Config.FileReference;
import com.jiminger.records.FileRecord;
import com.jiminger.records.FileRecord.Manager;
import com.jiminger.records.ImageDetails;
import com.jiminger.utils.FileAccess;
import com.jiminger.utils.MD5;

import net.dempsy.util.Functional;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class Md5File {
    // private static final Logger LOGGER = LoggerFactory.getLogger(Md5File.class);

    private static Vfs vfs;
    private static ObjectMapper om = FileRecord.makeStandardObjectMapper();

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getName() + " path/to/config.json");
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);

            System.out.println(c);

            vfs = createVfs(c.passwordsToTry);
            c.applyGlobalSettings();

            try(OpContext oc = vfs.operation();) {
                makeMd5File(c.md5FileToWrite, c.md5FilesToRead,

                    c.directoriesToScan

                    , c.md5RemainderFile, c.failedFile, c.deleteEmptyDirs, c.md5FileFilter(),
                    c.md5FileWriteLineBuffered, vfs, c.recurseIntoArchives, c.avoidMemMap, c.verify);
            }
        }
    }

    public static void makeMd5File(final String md5FileToWrite, final String[] md5FilesToRead, final FileReference[] directoriesToScan,
        final String md5RemainderFile, final String failedFile, final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter,
        final boolean lineBuffered, final Vfs vfs, final boolean recurseIntoArchives, final boolean avoidMemMap, final boolean verify) throws IOException {

        if(md5FileToWrite == null)
            throw new IllegalArgumentException("Cannot generate FileRecord index without setting md5FileToWrite in the config");
        System.out.println((avoidMemMap ? "" : "NOT ") + "avoiding memory mapping.");

        final boolean md5FileToWriteExists = new File(md5FileToWrite).exists();

        // if we're verifying then the md5FileToWrite should not already exist.
        if(verify && md5FileToWriteExists)
            throw new IllegalArgumentException("Cannot set Md5File to verify but have a file that exists for the md5FileToWrite(" + md5FileToWrite + ")");

        final Manager file2FileRecords = makeFileRecordsManager(vfs, md5FileToWrite, md5FilesToRead);

        final File md5File = new File(md5FileToWrite);
        if(failedFile != null)
            System.out.println("Writing errors to: " + failedFile);
        try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile)))
            : new PrintWriter(System.err);
            PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

            final Set<FileRecord> written = new HashSet<>();
            final long startTime = System.currentTimeMillis();
            // pass to calc md5
            recheck(() -> Arrays.stream(directoriesToScan)
                .map(fr -> fr.uri())
                .forEach(uri -> uncheck(() -> {
                    try(var sub = vfs.operation();) {
                        final Path path = sub.toPath(uri);
                        written.addAll(
                            doMd5(md5os, failed, new FileSpec(path), file2FileRecords, deleteEmtyDirs, md5FileFilter, lineBuffered, sub, recurseIntoArchives,
                                avoidMemMap, verify));
                    }
                })));

            System.out.println("Wrote " + written.size() + " entries into " + md5FileToWrite);
            if(md5RemainderFile != null) {
                written.forEach(fr -> file2FileRecords.remove(fr.uri()));
                System.out.println("Writing " + file2FileRecords.size() + " remaining entries to " + md5RemainderFile);
                try(PrintWriter remainderPw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5RemainderFile)));) {
                    file2FileRecords.stream().forEach(fr -> uncheck(() -> remainderPw.println(om.writeValueAsString(fr))));
                }
            }

            System.out.println("Finished Clean: " + (System.currentTimeMillis() - startTime) + " millis");
        }
    }

    private static Set<FileRecord> doMd5(final PrintWriter md5os, final PrintWriter failed, final FileSpec fSpec, final Manager existing,
        final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered, final OpContext oc,
        final boolean recurseIntoArchives, final boolean avoidMemMap, final boolean verify) throws IOException {

        final Set<FileRecord> ret = new HashSet<>();

        final BasicFileAttributes attr = fSpec.getAttr();
        if(attr != null && attr.isSymbolicLink()) {
            System.out.println("SKIPPING: Symbolic link: " + fSpec.uri());
            return ret;
        }

        if(!fSpec.exists())
            throw new FileNotFoundException("File " + fSpec + " doesn't exist.");

        if(!md5FileFilter.test(fSpec)) {
            System.out.println("SKIPPING: " + fSpec.uri() + "(" + fSpec.mimeType() + ")");
            return ret;
        }

        if(!fSpec.isDirectory()) {
            if(attr == null || !attr.isOther()) {
                try(FileAccess fAccess = new FileAccess(fSpec, avoidMemMap);) {
                    final FileRecord existingFr = existing.find(fAccess.uri());
                    final boolean frExists = existingFr != null && existingFr.isComplete();

                    final FileRecord fr;
                    if(!verify && frExists) {
                        System.out.println("COPYING  : " + fAccess.uri());
                        fAccess.setMime(existingFr.mime()); // otherwise it will read the mime by opening a stream in order to determine if it's recursable

                        final ImageDetails imageDetails = ImageDetails.checkImageDetails(fAccess, existingFr, failed);
                        if(imageDetails != null)
                            fr = new FileRecord(existingFr.uri(), existingFr.size(), existingFr.mime(), existingFr.lastModifiedTime(), existingFr.md5(),
                                existingFr.copyAdditionalWith(ImageDetails.KEY, imageDetails));
                        else
                            fr = existingFr;
                    } else {
                        if(verify && frExists)
                            System.out.println("VERIFYING: " + fAccess.uri());
                        else
                            System.out.println("SCANNING : " + fAccess.uri());
                        try(final var header = fAccess.preserveHeader(1024 * 1024);) {
                            final String md5 = bytesToHex(MD5.hash(fAccess));
                            final String mime = fAccess.mimeType("UNKNOWN");
                            final ImageDetails imageDetails = ImageDetails.checkImageDetails(fAccess, existingFr, failed);
                            fr = new FileRecord(fAccess.uri(), fAccess.size(), mime, fAccess.lastModifiedTime(), md5,
                                imageDetails != null ? chain(new HashMap<>(), m -> m.put(ImageDetails.KEY, imageDetails)) : null);
                        }
                    }
                    if(verify && frExists) {
                        if(!fr.md5().equals(existingFr.md5())) {
                            final var msg = "VERIFICATION FAILED on " + fAccess.uri() + " for " + fr + " against existing record " + existingFr;
                            failed.println(msg);
                            System.out.println(msg);
                            failed.flush();
                        }
                    } else {
                        ret.add(fr);
                        md5os.println(om.writeValueAsString(fr));
                    }

                    if(lineBuffered)
                        md5os.flush();
                }
            } else {
                if(attr.isOther()) {
                    System.out.println("SKIPPING: Unknown file type (pipe, socket, device, etc.): " + fSpec.uri());
                    fSpec.setMime(""); // there is no mime for this.
                    return Collections.emptySet(); // can't let this fSpec have isRecursable called since it will try to read the file.
                } else
                    throw new IOException("Illegal file type: " + fSpec);
            }
        }

        if((recurseIntoArchives && fSpec.isRecursable()) || (!recurseIntoArchives && fSpec.isDirectory())) {

            final boolean isArchiveOrCompressed = !fSpec.isDirectory(); // if it's recursable but NOT a directory, then it's an archive or it's compressed

            System.out.println("LISTING  : " + fSpec.uri());

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
                    return ret; // don't continue on.
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
                                    ret.addAll(doMd5(md5os, failed, f, existing, deleteEmtyDirs, md5FileFilter, lineBuffered, subCtx, recurseIntoArchives,
                                        avoidMemMap, verify));
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
                    return ret; // don't continue on.
                }
            }
        }

        return ret;
    }
}
