package com.jiminger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dempsy.serialization.jackson.JsonUtils;

@JsonInclude(Include.NON_EMPTY)
public class ConfigX {

    public static ConfigX load(final String configPath) throws IOException {
        final File configFile = new File(configPath);
        return load(configFile);
    }

    public static ConfigX load(final File configFile) throws IOException {
        if(!configFile.exists())
            throw new FileNotFoundException("The config file \"" + configFile.getAbsolutePath() + "\" doesn't exist");
        if(configFile.isDirectory())
            throw new FileNotFoundException("The config file specified \"" + configFile.getAbsolutePath() + "\" is a directory.");

        final ObjectMapper mapper = JsonUtils.makeStandardObjectMapper();
        return mapper.readValue(configFile, ConfigX.class);
    }

    // public final String mountPoint;
    // "/media/jim/Seagate Expansion Drive";
    // public static final String mountPoint = "/media/jim/BigBackUp-4T-Damaged";

    // Md5Sifter, Execute
    public final String actionsFileName = null; // = "/tmp/actions.txt";

    // Md5Sifter
    public final String dirPrescedence = null; // = "/media/jim/Seagate Expansion Drive/dirPresedence.txt";

    // Md5File, Organize, Md5Verify(only when deleting)
    public final String md5FileToWrite = null; // = mountPoint + "/md5.txt";

    // Md5File, Md5Verify, Organize, Md5Sifter
    public final String[] md5FilesToRead = null; // = new String[] {mountPoint + "/md5.copy.txt"};
    // public static final String[] md5FilesToRead = new String[] {};

    // Md5File, Md5Verify
    public final String[] directoriesToScan = null; // = new String[] {mountPoint};

    // Md5File
    public final boolean deleteEmptyDirs = false;

    // Md5File
    public final boolean md5FileWriteLineBuffered = false;

    // Md5Verify
    public final String verifyOutputFile = null; // = mountPoint + "/verify.txt";

    // Organize
    public final String srcDirectoryStr = null;// = "/home/jim/Landing";
    public final String dstDirectoryStr = null;// = "/media/jim/Seagate Expansion Drive/Family Media/Pictures/Scanned.bad";
    public final String dups = "DUPS";
    public final boolean appendOutfile = true;
    public final long byteBufferSize = 1024L * 1024L * 1024;

    // Organize, Md5Verify
    public final String outFile = "/tmp/out.txt";

    // Md5File, Md5Verify, Organize
    public final String failedFile = "/tmp/failed.txt";

    // Organize
    public final String[] filesToSkipAr = {"thumbs.db","thumbs.db:encryptable","recycler","zbthumbnail.info","zbthumbnail (2).info",
        "$recycle.bin","system volume information","desktop.ini","desktop (2).ini",".appledouble",".ds_store","digikam4.db",
        "thumbnails-digikam.db","sample pictures.lnk","itunes","album artwork","amazon mp3","podcasts","picasa.ini"
    };

    // Md5File, Organize
    public String[] fileNameContains = {};
    // = {mountPoint.toLowerCase() + "/md5.",mountPoint.toLowerCase() + "/tmp",
    // mountPoint.toLowerCase() + "/verify."};

    private Set<String> filesToSkipSet = null;

    public ConfigX() {}

    public Predicate<File> organizeFilter = sourceFile -> {
        if(sourceFile.getName().startsWith(".") || filesToSkip().contains(sourceFile.getName().toLowerCase()))
            return false;
        else {
            for(final String fn: fileNameContains) {
                if(sourceFile.getAbsolutePath().toLowerCase().contains(fn))
                    return false;
            }
            return true;
        }
    };

    public Predicate<File> md5FileFilter = sourceFile -> {
        for(final String fn: fileNameContains) {
            if(sourceFile.getAbsolutePath().toLowerCase().contains(fn))
                return false;
        }
        return true;
    };

    private Set<String> filesToSkip() {
        if(filesToSkipSet == null) {
            filesToSkipSet = new HashSet<>(Arrays.stream(filesToSkipAr).collect(Collectors.toSet()));
        }
        return filesToSkipSet;
    }

}
