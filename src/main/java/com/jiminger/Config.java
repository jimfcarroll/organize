package com.jiminger;

import static com.jiminger.utils.ImageUtils.DONT_RESIZE;
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
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.local.LocalFileSystem;

@JsonInclude(Include.NON_EMPTY)
public class Config {

    private static final String FILE_RECORD_FILE_TO_WRITE_STR = "md5FileToWrite";

    public static final String FILE_RECORD_FILE_TO_WRITE = uncheck(() -> Config.class.getField(FILE_RECORD_FILE_TO_WRITE_STR).getName());

    private static final ObjectMapper mapper = JsonUtils.makeStandardObjectMapper();

    public static Config load(final String configPath) throws IOException {
        final File configFile = new File(configPath);
        return load(configFile);
    }

    public static Config load(final File configFile) throws IOException {
        if(!configFile.exists())
            throw new FileNotFoundException("The config file \"" + configFile.getAbsolutePath() + "\" doesn't exist");
        if(configFile.isDirectory())
            throw new FileNotFoundException("The config file specified \"" + configFile.getAbsolutePath() + "\" is a directory.");

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

    // RunCommandFile
    public final boolean dryRun;

    // RunCommandFile - set to a directory where the results will ultimately be deleted.
    public final String toRemoveStore;

    // Md5File, RunCommandFile
    public final boolean enableLocalFileCaching;

    // Md5File, DirMerge, MergeRecords
    public final String md5FileToWrite;

    // Md5File, DirMerge, RunCommandFile
    public final String[] passwordsToTry;

    // Md5File, DirMerge, MergeRecords
    public final String[] md5FilesToRead;

    // Md5File
    public final String md5RemainderFile;

    // Md5File
    public final boolean verify;

    // Md5File
    public final String verifyCheckpointFile;

    // Md5File
    public final FileReference[] directoriesToScan;

    // Md5File
    public final boolean deleteEmptyDirs;

    // Md5File
    public final boolean md5FileWriteLineBuffered;

    // Md5File
    public final String failedFile;

    // Md5File
    public final String[] fileNameContains;

    // Dont use memory mapping of files for anything
    // Md5File, RunCommandFile
    public final boolean avoidMemMap;

    // Md5File
    public final boolean recurseIntoArchives;

    // ImageDupFinder
    public final String[] imageCorrelationsFilesToRead;
    public final String imageCorrelationsFileToWrite;

    public final int maxImageDim;

    public Predicate<FileSpec> md5FileFilter() {
        return sourceFile -> {
            for(final String fn: fileNameContains) {
                if(uncheck(() -> sourceFile.uri()).toString().toLowerCase().contains(fn))
                    return false;
            }
            return true;
        };
    }

    public void applyGlobalSettings() {
        System.out.println((enableLocalFileCaching ? "" : "NOT ") + "enabling local file caching.");
        LocalFileSystem.enableCaching(enableLocalFileCaching);
    }

    @Override
    public String toString() {
        return uncheck(() -> mapper.writeValueAsString(this));
    }

    private Config() {
        md5FileToWrite = null;
        md5FilesToRead = null;
        md5RemainderFile = null;
        verify = false;
        verifyCheckpointFile = null;
        directoriesToScan = null;
        deleteEmptyDirs = false;
        md5FileWriteLineBuffered = false;
        failedFile = "/tmp/failed.txt";
        fileNameContains = new String[0];
        avoidMemMap = false;
        enableLocalFileCaching = false;
        passwordsToTry = null;
        recurseIntoArchives = true;
        dryRun = false;
        toRemoveStore = null;
        imageCorrelationsFilesToRead = null;
        imageCorrelationsFileToWrite = null;
        maxImageDim = DONT_RESIZE;
    }

}
