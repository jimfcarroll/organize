package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.dempsy.util.HexStringUtil;

public class Md5File {

    public static Map<String, String> readMd5FileLookup(final String... fileNames) throws IOException {
        final Map<String, String> file2Md5 = new HashMap<String, String>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            if(fileName != null) {
                final File file = new File(fileName);
                if(file.exists()) {
                    try(BufferedReader br = new BufferedReader(new FileReader(file));) {
                        for(String line = br.readLine(); line != null; line = br.readLine()) {
                            final String[] entry = line.split("\\|\\|");
                            if(entry.length != 2)
                                throw new RuntimeException("An md5 file entry must have 2 values separated by a \"||\". The file " + fileName +
                                    " appears to have an entry of the form:" + line);
                            file2Md5.put(entry[1], entry[0]);
                        }
                    }
                }
            }
        })));
        return file2Md5.isEmpty() ? null : file2Md5;
    }

    public static Map<String, List<String>> readMd5File(final String... fileNames) throws IOException {
        final Map<String, List<String>> md5map = new HashMap<>();
        recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
            final File file = new File(fileName);
            if(!file.exists())
                throw new FileNotFoundException("MD5 file " + fileName + " doesn't exist.");
            try(BufferedReader br = new BufferedReader(new FileReader(file));) {
                for(String line = br.readLine(); line != null; line = br.readLine()) {
                    final String[] entry = line.split("\\|\\|");
                    if(entry.length != 2)
                        throw new RuntimeException("An md5 file entry must have 2 values separated by a \"||\". The file " + fileName +
                            " appears to have an entry of the form:" + line);
                    final String key = entry[0];
                    List<String> filesWithMd5 = md5map.get(key);
                    if(filesWithMd5 == null) {
                        filesWithMd5 = new ArrayList<>(2);
                        md5map.put(key, filesWithMd5);
                    }
                    filesWithMd5.add(entry[1]);
                }
            }
        })));
        return md5map;
    }

    public static void makeMd5File(final String md5FileToWrite, final String[] md5FilesToRead, final String[] directoriesToScan,
        final String failedFile, final boolean deleteEmtyDirs, final Predicate<File> md5FileFilter, final boolean lineBuffered) throws IOException {
        final Map<String, String> file2md5 = readMd5FileLookup(Stream.concat(Stream.of(md5FileToWrite),
            Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new));

        final File md5File = new File(md5FileToWrite);
        try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile)))
            : new PrintWriter(System.err);
            PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

            // pass to calc md5
            recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck(() -> {
                final File directory = new File(d);
                if(!directory.exists())
                    failed.println(directory.toURI().toString() + "||" + "doesn't exist");
                else {
                    doMd5(md5os, directory, file2md5, deleteEmtyDirs, md5FileFilter, lineBuffered);
                }
            })));

        }
    }

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getSimpleName() + " path/to/config.json");
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 1)
            usage();
        else {
            final ConfigX c = ConfigX.load(args[0]);
            makeMd5File(c.md5FileToWrite, c.md5FilesToRead, c.directoriesToScan, c.failedFile, c.deleteEmptyDirs, c.md5FileFilter, c.md5FileWriteLineBuffered);
            System.out.println("Finished Clean");
        }
    }

    private static void doMd5(final PrintWriter md5os, final File file, final Map<String, String> existing, final boolean deleteEmtyDirs,
        final Predicate<File> md5FileFilter, final boolean lineBuffered)
        throws IOException {

        if(Files.isSymbolicLink(file.toPath())) {
            System.out.println("SKIPPING: Symbolic link: " + file.getAbsolutePath());
            return;
        }

        if(!file.exists())
            throw new FileNotFoundException("File " + file + " doesn't exist.");

        if(!md5FileFilter.test(file)) {
            System.out.println("SKIPPING: " + file.getAbsolutePath());
            return;
        }

        if(file.isDirectory()) {
            System.out.println("LISTING : " + file.getAbsolutePath());

            final File[] dirContents = file.listFiles();
            if(dirContents == null || dirContents.length == 0) {
                if(deleteEmtyDirs) {
                    System.out.println("Empty directory: \"" + file + "\"");
                    if(!file.delete()) {
                        System.out.println("FAILED: to delete empty directory: " + file.getAbsolutePath());
                    }
                }
            } else {
                recheck(() -> Arrays.stream(dirContents).forEach(f -> uncheck(() -> doMd5(md5os, f, existing, deleteEmtyDirs, md5FileFilter, lineBuffered))));
            }
        } else {
            System.out.println("SCANNING: " + file.getAbsolutePath());
            final String absFileName = file.getAbsolutePath();
            final String existingMd5 = Optional.ofNullable(existing).map(e -> e.get(absFileName)).orElse(null);
            if(existingMd5 != null)
                md5os.println(existingMd5 + "||" + file.getAbsolutePath());
            else
                // otherwise it's a regular file
                printHash(md5os, file);

            if(lineBuffered)
                md5os.flush();
        }
    }

    private static void printHash(final PrintWriter out, final File file) throws IOException {
        printHash(out, MD5.hash(file), file);
    }

    private static void printHash(final PrintWriter out, final byte[] hash, final File file) throws IOException {
        out.println(HexStringUtil.bytesToHex(hash) + "||" + file.getAbsolutePath());
    }
}
