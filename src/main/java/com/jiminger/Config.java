package com.jiminger;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class Config {
    public static final String mountPoint = "/media/jim/Seagate Expansion Drive";
    // public static final String mountPoint = "/media/jim/BigBackUp-4T-Damaged";

    // Md5Sifter, Execute
    public static final String actionsFileName = "/tmp/actions.txt";

    // Md5Sifter
    public static final String dirPrescedence = "/media/jim/Seagate Expansion Drive/dirPresedence.txt";

    // Md5File, Organize, Md5Verify(only when deleting)
    public static final String md5FileToWrite = mountPoint + "/md5.txt";

    // Md5File, Md5Verify, Organize, Md5Sifter
    public static final String[] md5FilesToRead = new String[] { mountPoint + "/md5.copy.txt" };
    // public static final String[] md5FilesToRead = new String[] {};

    // Md5File, Md5Verify
    public static final String[] directoriesToScan = new String[] { mountPoint };

    // Md5File
    public static final boolean deleteEmptyDirs = false;

    // Md5Verify
    public static final String verifyOutputFile = mountPoint + "/verify.txt";

    // Organize
    public static final String srcDirectoryStr = "/home/jim/Landing";
    public static final String dstDirectoryStr = "/media/jim/Seagate Expansion Drive/Family Media/Pictures/Scanned.bad";
    public static final String dups = "DUPS";
    public static final boolean appendOutfile = true;
    public static final long byteBufferSize = 4L * 1024L * 1024L * 1024;

    // Organize, Md5Verify
    public static final String outFile = "/tmp/out.txt";

    // Md5File, Md5Verify, Organize
    public static final String failedFile = "/tmp/failed.txt";

    // Organize
    public static String[] filesToSkipAr = { "thumbs.db", "thumbs.db:encryptable", "recycler", "zbthumbnail.info", "zbthumbnail (2).info",
            "$recycle.bin", "system volume information", "desktop.ini", "desktop (2).ini", ".appledouble", ".ds_store", "digikam4.db",
            "thumbnails-digikam.db", "sample pictures.lnk", "itunes", "album artwork", "amazon mp3", "podcasts", "picasa.ini"
    };

    // Md5File, Organize
    public static String[] fileNameContains = { mountPoint.toLowerCase() + "/md5.", mountPoint.toLowerCase() + "/tmp",
            mountPoint.toLowerCase() + "/verify." };

    public static Set<String> filesToSkip = new HashSet<>();
    static {
        filesToSkip.addAll(Arrays.asList(filesToSkipAr));
    }

    public static Predicate<File> organizeFilter = sourceFile -> {
        if (sourceFile.getName().startsWith(".") || filesToSkip.contains(sourceFile.getName().toLowerCase()))
            return false;
        else {
            for (final String fn : fileNameContains) {
                if (sourceFile.getAbsolutePath().toLowerCase().contains(fn))
                    return false;
            }
            return true;
        }
    };

    public static Predicate<File> md5FileFilter = sourceFile -> {
        for (final String fn : fileNameContains) {
            if (sourceFile.getAbsolutePath().toLowerCase().contains(fn))
                return false;
        }
        return true;
    };

}
