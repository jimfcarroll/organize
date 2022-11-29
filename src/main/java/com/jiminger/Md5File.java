package com.jiminger;

import static com.jiminger.FileRecord.readFileRecords;
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
import net.dempsy.vfs.CopyingArchiveFileSystem;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.OpContext;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.SevenZArchiveFileSystem;
import net.dempsy.vfs.Vfs;
import net.dempsy.vfs.ZCompressedFileSystem;
import net.dempsy.vfs.bz.Bz2FileSystem;
import net.dempsy.vfs.gz.GzFileSystem;
import net.dempsy.vfs.local.LocalFileSystem;
import net.dempsy.vfs.tar.TarFileSystem;
import net.dempsy.vfs.xz.XzFileSystem;
import net.dempsy.vfs.zip.ZipFileSystem;
import net.sf.sevenzipjbinding.SevenZip;

public class Md5File {
    // private static final Logger LOGGER = LoggerFactory.getLogger(Md5File.class);

    private static Vfs vfs;

    private static Vfs getVfs(final String[] passwordsToTry) throws IOException {
        // final var szfs = new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz" /* , "zip" */);
        // if(passwordsToTry != null && passwordsToTry.length > 0)
        // szfs.tryPasswords(passwordsToTry);
        //
        // return new Vfs(
        // new CopyZipFileSystem(),
        // szfs,
        // // new DecompressedFileSystem(new GzFileSystem()),
        // new GzFileSystem(),
        // new ZCompressedFileSystem(),
        // new Bz2FileSystem(),
        // new XzFileSystem()
        //
        // );

        SevenZArchiveFileSystem szfs;

        final var ret = new Vfs(
            // szfs = new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
            // new GzFileSystem(),
            // new ZCompressedFileSystem(),
            // new Bz2FileSystem()
            //
            // , new XzFileSystem()

            new CopyingArchiveFileSystem(new TarFileSystem()),
            new GzFileSystem(),
            new CopyingArchiveFileSystem(new ZipFileSystem()),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem(),
            szfs = new SevenZArchiveFileSystem()

        );

        if(passwordsToTry != null && passwordsToTry.length > 0)
            szfs.tryPasswords(passwordsToTry);

        return ret;
    }

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getName() + " path/to/config.json");
    }

    static public void main(final String[] args) throws Exception {

        final String osArch = System.getProperty("os.arch");
        System.out.println("OS Arch:" + osArch);
        if("aarch64".equals(osArch))
            SevenZip.initSevenZipFromPlatformJAR("Linux-arm64");

        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);
            vfs = getVfs(c.passwordsToTry);
            System.out.println((c.avoidMemMap ? "" : "NOT ") + "avoiding memory mapping.");
            MD5.avoidMemMap(c.avoidMemMap);
            System.out.println((c.enableLocalFileCaching ? "" : "NOT ") + "enabling local file caching.");
            LocalFileSystem.enableCaching(c.enableLocalFileCaching);

            try(OpContext oc = vfs.operation();) {
                makeMd5File(c.md5FileToWrite, c.md5FilesToRead,

                    c.directoriesToScan

                    , c.failedFile, c.deleteEmptyDirs, c.md5FileFilter(),
                    c.md5FileWriteLineBuffered, vfs);
            }
        }
    }

    public static void makeMd5File(final String md5FileToWrite, final String[] md5FilesToRead, final FileReference[] directoriesToScan,
        final String failedFile, final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered, final Vfs vfs)
        throws IOException {
        final Map<String, FileRecord> file2FileRecords =

            readFileRecords(
                Stream.concat(Stream.of(md5FileToWrite), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new)

            ).stream()
                .collect(Collectors.toMap(fs -> fs.path, fs -> fs, (fr1, fr2) -> {
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
                        doMd5(md5os, failed, new FileSpec(path), file2FileRecords, deleteEmtyDirs, md5FileFilter, lineBuffered, sub);
                    }
                })));

            System.out.println("Finished Clean: " + (System.currentTimeMillis() - startTime) + " millis");
        }
    }

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    private static void doMd5(final PrintWriter md5os, final PrintWriter failed, final FileSpec fSpec, final Map<String, FileRecord> existing,
        final boolean deleteEmtyDirs, final Predicate<FileSpec> md5FileFilter, final boolean lineBuffered, final OpContext oc) throws IOException {

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
                final FileRecord existingFr = Optional.ofNullable(existing).map(e -> e.get(uncheck(() -> fSpec.uri()).toString())).orElse(null);

                final FileRecord fr;
                if(existingFr != null && existingFr.isComplete()) {
                    System.out.println("COPYING : " + fSpec.uri());
                    fSpec.setMime(existingFr.mime); // otherwise it will read the mime by opening a stream in order to determine if it's recursable
                    fr = existingFr;
                } else {
                    System.out.println("SCANNING: " + fSpec.uri());
                    try(var header = fSpec.preserveHeader(1024 * 1024);) {
                        final String md5 = (existingFr != null && existingFr.md5 != null) ? existingFr.md5 : bytesToHex(MD5.hash(fSpec));
                        final String mime = (existingFr != null && existingFr.mime != null) ? existingFr.mime : fSpec.mimeType("UNKNOWN");
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

        if(fSpec.isRecursable()) {

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
                                    doMd5(md5os, failed, f, existing, deleteEmtyDirs, md5FileFilter, lineBuffered, subCtx);
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
