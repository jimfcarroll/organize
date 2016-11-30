package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.twmacinta.util.MD5;
import com.twmacinta.util.MD5InputStream;

public class Organize {
    private static boolean dryrun = false;

    private static Map<String, String> invert(final Map<String, List<String>> md52files) {
        final Map<String, String> ret = new HashMap<>();
        md52files.entrySet().stream().forEach(e -> {
            final String key = e.getKey();
            e.getValue().stream().forEach(fn -> ret.put(fn, key));
        });
        return ret;
    }

    static PrintWriter out = new PrintWriter(System.out);

    private static List<ByteBuffer> fileContents;
    static {
        // see if we can read the entire thing into memory
        {
            fileContents = new ArrayList<>();
            long remainingBytes = Config.byteBufferSize;
            for (boolean done = false; remainingBytes > 0 && !done;) {
                final int nextSize = (remainingBytes < 0x40000000L) ? (int) remainingBytes : 0x40000000;
                try {
                    fileContents.add(ByteBuffer.allocateDirect(nextSize));
                    remainingBytes -= nextSize;
                } catch (final OutOfMemoryError oom) {
                    done = true;
                    fileContents.clear();
                    fileContents = null;
                    System.out.println("Can't make a byte buffer of " + Config.byteBufferSize + " because there's not enough memory.");
                }
            }
        }

    }

    static public void main(final String[] args) throws Exception {
        final String srcDirectoryStr = Config.srcDirectoryStr;
        final String dstDirectoryStr = Config.dstDirectoryStr;

        final String md5FileToWrite = Config.md5FileToWrite;
        final String[] md5FilesToRead = Config.md5FilesToRead;
        final String failedFile = Config.failedFile;
        final String dups = Config.dups;
        final String outFile = Config.outFile;

        if (outFile != null)
            out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFile, Config.appendOutfile)), true);

        out.println("CPDIR: Copying files from " + srcDirectoryStr + " into " + dstDirectoryStr);

        final File dstDirectory = new File(dstDirectoryStr);
        if (dstDirectory.exists() && dstDirectory.isDirectory()) {
            out.print("MKMD5: Making MD5 file for " + dstDirectoryStr + " ...");
            out.flush();
            Md5File.makeMd5File(md5FileToWrite, md5FilesToRead, new String[] { dstDirectoryStr }, failedFile, false);
            out.println("Done!");
        }
        final File srcDirectory = new File(srcDirectoryStr);

        final String[] readMd5Files = (new File(md5FileToWrite).exists()) ? Stream.concat(Stream.of(md5FileToWrite),
                Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new) : md5FilesToRead;

        final Map<String, List<String>> md52files = Md5File.readMd5File(readMd5Files);
        final Map<String, String> files2Md5 = invert(md52files);

        if (!srcDirectory.exists()) {
            System.err.println("The directory \"" + srcDirectoryStr + "\" doesn't exist.");
            System.exit(1);
        }

        if (!dstDirectory.exists()) {
            if (!dstDirectory.mkdirs()) {
                System.err.println("ERROR: Failed to create the destination directory:" + dstDirectoryStr);
                System.exit(1);
            }
        }

        try (PrintWriter md5os = (md5FileToWrite != null)
                ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5FileToWrite, true)), true) : null;
                PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile, true)))
                        : new PrintWriter(System.err);) {
            copyFromTo(srcDirectory, dstDirectory, Config.organizeFilter, md52files, files2Md5, md5os, failed, dups);
        }
        out.println("DONE: Finished Clean");
    }

    public static void copyFromTo(final File srcDirectory, final File dstDirectory, final Predicate<File> copyFilter,
            final Map<String, List<String>> md52files, final Map<String, String> files2Md5, final PrintWriter md5os, final PrintWriter failed,
            final String dups) throws IOException {
        if (!dstDirectory.exists()) {
            if (!dstDirectory.mkdirs())
                throw new FileNotFoundException("Could not create \"" + getName(dstDirectory) + "\"");
        } else if (!dstDirectory.isDirectory())
            throw new FileNotFoundException("The file \"" + getName(dstDirectory) + "\" is not a directory.");

        final List<File> subDirs = new ArrayList<File>();
        if (!srcDirectory.exists())
            throw new FileNotFoundException("The directory \"" + getName(srcDirectory) + "\" doesn't exist.");

        if (!srcDirectory.isDirectory())
            throw new FileNotFoundException("The file \"" + getName(srcDirectory) + "\" is not a directory.");

        File[] files = srcDirectory.listFiles();
        if (files == null) {
            if (failed != null) {
                failed.println("# The following is an entire directory failure:");
                failed.println(getName(srcDirectory) + " !=> " + getName(dstDirectory));
            }
            out.println("FAIL: Failed to copy directory " + getName(srcDirectory));
            files = new File[0];
        }

        for (final File file : files) {
            if (file.isDirectory())
                subDirs.add(file);
            else
                conditionalCopyTo(file, new File(dstDirectory, file.getName()), copyFilter, md52files, files2Md5, md5os, failed, dups, 0);
        }

        md5os.flush();

        if (subDirs.size() > 0) {
            for (final File subdir : subDirs) {
                if (copyFilter == null || copyFilter.test(subdir)) {
                    final File newDestDirectory = new File(dstDirectory, subdir.getName());
                    copyFromTo(subdir, newDestDirectory, copyFilter, md52files, files2Md5, md5os, failed, dups);
                } else
                    out.println("SKIP: Skipping " + getName(subdir));
            }
        }
    }

    private static final int CHUNK_SIZE = 65536;

    private static void readFromStream(final InputStream is, final ByteBuffer bb, final int size) throws IOException {
        int remainingBytes = size;
        final byte[] chunk = new byte[CHUNK_SIZE];
        while (remainingBytes > 0) {
            final int chunkSize = remainingBytes > CHUNK_SIZE ? CHUNK_SIZE : remainingBytes;
            final int bytesRead = is.read(chunk, 0, chunkSize);
            bb.put(chunk, 0, bytesRead);
            remainingBytes -= bytesRead;
        }
    }

    private static void bufClearData(final List<ByteBuffer> fileContents) {
        if (fileContents != null)
            fileContents.stream().forEach(bb -> bb.clear());
    }

    private static void readFromStream(final InputStream is, final List<ByteBuffer> fileContents, final long sourceFileSize) throws IOException {
        long remainingBytes = sourceFileSize;
        int index = 0;
        // reset the bb to refill it.
        bufClearData(fileContents);
        while (remainingBytes > 0) {
            final ByteBuffer bb = fileContents.get(index++);
            final long bbSize = bb.remaining();
            final int nextSize = (remainingBytes < bbSize) ? (int) remainingBytes : (int) bbSize;
            readFromStream(is, bb, nextSize);
            remainingBytes -= nextSize;
        }
    }

    private static void writeToStream(final List<ByteBuffer> bba, final OutputStream os) throws IOException {
        recheck(() -> bba.stream().forEach(bb -> uncheck(() -> writeToStream(bb, os))));
    }

    private static void writeToStream(final ByteBuffer bb, final OutputStream os) throws IOException {
        bb.flip();
        int remainingBytes = bb.remaining();
        final byte[] chunk = new byte[CHUNK_SIZE];
        while (remainingBytes > 0) {
            final int chunkSize = remainingBytes > CHUNK_SIZE ? CHUNK_SIZE : remainingBytes;
            bb.get(chunk, 0, chunkSize);
            os.write(chunk, 0, chunkSize);
            remainingBytes -= chunkSize;
        }
    }

    static public void conditionalCopyTo(final File from, final File to, final Predicate<File> copyFilter,
            final Map<String, List<String>> md52files, final Map<String, String> files2Md5,
            final PrintWriter md5, final PrintWriter failed, final String copyDupFolder, int dupCount) throws IOException {

        bufClearData(fileContents);

        if (copyFilter != null && !copyFilter.test(from)) {
            out.println("SKIP: Skipping " + getName(from));
            return;
        }

        // check to see if the file already exists and if it's the same size
        if (to.exists() && to.isFile()) {
            if (copyDupFolder != null) {
                File parent = to.getParentFile();
                if (parent.getName().equals(copyDupFolder + (dupCount - 1)))
                    parent = parent.getParentFile();

                final File newDestDirectory = new File(parent, copyDupFolder + dupCount);
                conditionalCopyTo(from, new File(newDestDirectory, from.getName()), copyFilter, md52files, files2Md5, md5, failed, copyDupFolder,
                        ++dupCount);
                return;
            } else {
                out.println("EXISTS: File \"" + getName(from) + "\" already exists at the destination.");
            }
        }

        // see if the source file md5 exists
        String md5From = files2Md5.get(from.getAbsolutePath());

        final long sourceFileSize = from.length();
        if (md5From == null) {

            if (fileContents != null && sourceFileSize <= Config.byteBufferSize) { // fill fileContents and calc md5
                try (MD5InputStream is = new MD5InputStream(new FileInputStream(from));) {
                    readFromStream(is, fileContents, sourceFileSize);
                    md5From = is.getMD5().asHex();
                } catch (final IOException ioe) {
                    out.println("ERROR: Failed to read \"" + from + "\" into a buffer:" + ioe.getMessage());
                }
            }
        }

        // if it still == null then just calculate it right off of the file
        if (md5From == null) {
            try {
                md5From = MD5.asHex(MD5.getHash(from)).toString();
            } catch (final IOException ioe) {
                out.println("ERROR: Failed calculate md5 for \"" + from + "\":" + ioe.getMessage());
            }
        }

        // now see if it already exists at the destination somewhere
        final List<String> dstWithSameMd5 = md5From == null ? null : md52files.get(md5From);
        if (dstWithSameMd5 != null) {
            out.println("EXISTS: File \"" + from + "\" already exists in destination at " + dstWithSameMd5);
            return;
        }

        if (!dryrun) {
            // make sure folder exists.
            final File destDirectory = to.getParentFile();
            if (!destDirectory.exists()) {
                out.println("MKDIR " + destDirectory);
                if (!dryrun)
                    destDirectory.mkdirs();
            }

            if (copyTo(from, fileContents, to, md5, md5From))
                transferAttributes(from, to);
        }
    }

    static private boolean bufHasData(final List<ByteBuffer> fileContents) {
        return fileContents != null && fileContents.get(0).position() > 0;
    }

    static public boolean copyTo(final File from, final List<ByteBuffer> fileContents, final File to, final PrintWriter md5, final String srcMd5)
            throws IOException {
        if (bufHasData(fileContents)) {
            bufCopy(fileContents, from, to, srcMd5, md5);
            return true;
        } else {
            try {
                simpleCopyFile(from, to, md5, srcMd5);
                return true;
            } catch (final IOException ioe) {
                out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage());
            }
            out.println("ERROR: Trying again....");
            try {
                simpleCopyFile(from, to, md5, srcMd5);
                return true;
            } catch (final IOException ioe) {
                out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage());
            }
            out.println("ERROR: Trying again using memory mapping ....");
            try {
                memMapCopyFile(from, to, md5, srcMd5);
                return true;
            } catch (final IOException ioe) {
                out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage());
            }
            out.println("ERROR: Trying again using persistent byte copy ....");
            try {
                persistentBytewiseCopyTo(from, to, md5, srcMd5);
                return true;
            } catch (final IOException ioe) {
                out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage());
            }

            // if we got here then this failed... .so we should remove the to file.
            if (to.exists())
                try {
                    Files.delete(to.toPath());
                } catch (final IOException ioe) {
                    out.println("ERROR: Failed to delete " + to.getAbsolutePath() + " " + ioe.getLocalizedMessage());
                }
            return false;
        }
    }

    static final public int BUFSIZE = 10 * 1024 * 1024;
    static final public long MAX_FAILED_COUNT = 100;

    static String getName(final File file) {
        try {
            return file.getCanonicalPath();
        } catch (final IOException ioe) {
            return "/...unknown path.../" + file.getName();
        }
    }

    static void transferAttributes(final File from, final File to) {
        try {
            final BasicFileAttributes fromAttrs = Files.readAttributes(from.toPath(), BasicFileAttributes.class);
            final BasicFileAttributeView v = Files.getFileAttributeView(to.toPath(), BasicFileAttributeView.class);
            final BasicFileAttributes toAttrs = v.readAttributes();

            FileTime creationTime = fromAttrs.creationTime();
            if (creationTime.compareTo(toAttrs.creationTime()) > 0)
                creationTime = toAttrs.creationTime();

            FileTime lastModifiedTime = fromAttrs.lastModifiedTime();
            if (lastModifiedTime.compareTo(toAttrs.lastModifiedTime()) > 0)
                lastModifiedTime = toAttrs.creationTime();

            v.setTimes(lastModifiedTime, null, creationTime);
        } catch (final IOException ioe) {
            out.println("ERROR: Failed to transfer attributes for " + getName(from) + " ... continuing.");
        }
    }

    static public void persistentBytewiseCopyTo(final File from, final File to, final PrintWriter md5, final String srcMd5) throws IOException {
        out.println("PBCP: \"" + getName(from) + "\" to \"" + getName(to) + "\"");

        try (RandomAccessFile fir = new RandomAccessFile(from, "r");
                BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(to))) {
            final byte[] buf = new byte[BUFSIZE];
            int i = 0;
            long pos = 0;
            long failedCount = 0;
            boolean done = false;
            while (!done) {
                try {
                    buf[i++] = fir.readByte();
                    pos++;
                    failedCount = 0;
                } catch (final EOFException eof) {
                    done = true;
                } // we're done
                catch (final IOException ioe) {
                    out.println("ERROR: Problem reading byte " + pos + " from file.");
                    fir.seek(pos);
                    failedCount++;
                    if (failedCount > MAX_FAILED_COUNT)
                        throw ioe;
                    continue;
                }

                if (i == BUFSIZE || done) {
                    fos.write(buf, 0, i);
                    i = 0;
                }
            }
            fos.close();
            checkCopy(from, to, srcMd5, md5);
        }
    }

    public static void memMapCopyFile(final File source, final File dest, final PrintWriter md5, final String srcMd5) throws IOException {
        out.println("MMCP: \"" + getName(source) + "\" to \"" + getName(dest) + "\" using memory mapping");
        try (FileInputStream sourceis = new FileInputStream(source);
                FileOutputStream destos = new FileOutputStream(dest);
                FileChannel in = sourceis.getChannel();
                FileChannel out = destos.getChannel()) {
            final long size = in.size();
            final MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);

            out.write(buf);
        }

        checkCopy(source, dest, srcMd5, md5);
    }

    static public void simpleCopyFile(final File src, final File dest, final PrintWriter md5, final String srcMd5) throws IOException {
        out.println("SCOPY: " + src + " => " + dest);
        try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(src));
                BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dest))) {
            final byte[] buf = new byte[10 * 1024 * 1024];
            int i = 0;
            while ((i = fis.read(buf)) != -1)
                fos.write(buf, 0, i);
        }
        checkCopy(src, dest, srcMd5, md5);
    }

    static public void bufCopy(final List<ByteBuffer> fileContents, final File src, final File dest, final String srcMd5, final PrintWriter md5)
            throws IOException {
        try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dest));) {
            out.println("BUFCOPY: " + src + " => " + dest);
            writeToStream(fileContents, fos);
        }
        checkCopy(src, dest, srcMd5, md5);
    }

    static void checkCopy(final File src, final File dest, final String srcMd5, final PrintWriter md5) throws IOException {
        final String newmd5 = MD5.asHex(MD5.getHash(dest)).toString();
        if (srcMd5 != null && !srcMd5.equals(newmd5)) {
            dest.delete();
            throw new IOException("Copying " + src + " to " + dest + " resulted in corrupt file.");
        }
        md5.println(newmd5 + "||" + dest.getAbsolutePath());
    }
}
