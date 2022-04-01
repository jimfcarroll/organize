package com.jiminger;

import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dempsy.serialization.jackson.JsonUtils;

@JsonInclude(Include.NON_EMPTY)
public class Config {

    private static final String FILE_RECORD_FILE_TO_WRITE_STR = "md5FileToWrite";

    public static final String FILE_RECORD_FILE_TO_WRITE = uncheck(() -> Config.class.getField(FILE_RECORD_FILE_TO_WRITE_STR).getName());

    public static Config load(final String configPath) throws IOException {
        final File configFile = new File(configPath);
        return load(configFile);
    }

    public static Config load(final File configFile) throws IOException {
        if(!configFile.exists())
            throw new FileNotFoundException("The config file \"" + configFile.getAbsolutePath() + "\" doesn't exist");
        if(configFile.isDirectory())
            throw new FileNotFoundException("The config file specified \"" + configFile.getAbsolutePath() + "\" is a directory.");

        final ObjectMapper mapper = JsonUtils.makeStandardObjectMapper();
        return mapper.readValue(configFile, Config.class);
    }

    public static class FileReference {
        public final String root;
        public final String path;

        @SuppressWarnings("unused")
        private FileReference() {
            root = path = null;
        }

        public FileReference(final String root, final String path) {
            this.root = root;
            this.path = path;
        }

        public URI uri() {
            return new File(new File(root), path).toURI();
        }
    }

    public final boolean enableLocalFileCaching;

    // Md5File, Organize, Md5Verify(only when deleting), MergeRecords
    public final String md5FileToWrite;

    // Md5File
    public final String[] passwordsToTry;

    // Md5File, Md5Verify, Organize, Md5Sifter, MergeRecords
    public final String[] md5FilesToRead;

    // Md5File, Md5Verify
    public final FileReference[] directoriesToScan;

    // Md5File
    public final boolean deleteEmptyDirs;

    // Md5File
    public final boolean md5FileWriteLineBuffered;

    // Md5File, Md5Verify, Organize
    public final String failedFile;

    // Md5File, Organize
    public final String[] fileNameContains;

    // Dont use memory mapping of files for anything
    // Md5File
    public final boolean avoidMemMap;

    // public final String mountPoint;
    // "/media/jim/Seagate Expansion Drive";
    // public static final String mountPoint = "/media/jim/BigBackUp-4T-Damaged";
    //
    // // Md5Sifter, Execute
    // public final String actionsFileName = null; // = "/tmp/actions.txt";
    //
    // // Md5Sifter
    // public final String dirPrescedence = null; // = "/media/jim/Seagate Expansion Drive/dirPresedence.txt";

    // // Md5Verify
    // public final String verifyOutputFile = null; // = mountPoint + "/verify.txt";
    //
    // // Organize
    // public final String srcDirectoryStr = null;// = "/home/jim/Landing";
    // public final String dstDirectoryStr = null;// = "/media/jim/Seagate Expansion Drive/Family Media/Pictures/Scanned.bad";
    // public final String dups = "DUPS";
    // public final boolean appendOutfile = true;
    // public final long byteBufferSize = 1024L * 1024L * 1024;
    //
    // // Organize, Md5Verify
    // public final String outFile = "/tmp/out.txt";
    //
    // // Organize
    // public final String[] filesToSkipAr = {"thumbs.db","thumbs.db:encryptable","recycler","zbthumbnail.info","zbthumbnail (2).info",
    // "$recycle.bin","system volume information","desktop.ini","desktop (2).ini",".appledouble",".ds_store","digikam4.db",
    // "thumbnails-digikam.db","sample pictures.lnk","itunes","album artwork","amazon mp3","podcasts","picasa.ini"
    // };
    // private Set<String> filesToSkipSet = null;
    //
    // public Config() {}
    //
    // public Predicate<File> organizeFilter = sourceFile -> {
    // if(sourceFile.getName().startsWith(".") || filesToSkip().contains(sourceFile.getName().toLowerCase()))
    // return false;
    // else {
    // for(final String fn: fileNameContains) {
    // if(sourceFile.getAbsolutePath().toLowerCase().contains(fn))
    // return false;
    // }
    // return true;
    // }
    // };

    public Predicate<FileSpec> md5FileFilter() {
        return sourceFile -> {
            for(final String fn: fileNameContains) {
                if(sourceFile.uri().toString().toLowerCase().contains(fn))
                    return false;
            }
            return true;
        };
    }

    // private Set<String> filesToSkip() {
    // if(filesToSkipSet == null) {
    // filesToSkipSet = new HashSet<>(Arrays.stream(filesToSkipAr).collect(Collectors.toSet()));
    // }
    // return filesToSkipSet;
    // }

    private Config() {
        md5FileToWrite = null;
        md5FilesToRead = null;
        directoriesToScan = null;
        deleteEmptyDirs = false;
        md5FileWriteLineBuffered = false;
        failedFile = "/tmp/failed.txt";
        fileNameContains = new String[0];
        avoidMemMap = false;
        enableLocalFileCaching = false;
        passwordsToTry = null;
    }

}
