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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Md5Sifter {

    private static final Map<String, Integer> ranking = uncheck(() -> readRanking(Config.dirPrescedence));

    private static void readMd5File(final Map<String, List<String>> md5map, final String fileName) throws IOException {
        final File file = new File(fileName);
        if (!file.exists())
            throw new FileNotFoundException("MD5 file " + fileName + " doesn't exist.");
        try (BufferedReader br = new BufferedReader(new FileReader(file));) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                final String[] entry = line.split("\\|\\|");
                if (entry.length != 2)
                    throw new RuntimeException("An md5 file entry must have 2 values separated by a \"||\". The file " + fileName +
                            " appears to have an entry of the form:" + line);
                final String key = entry[0];
                List<String> filesWithMd5 = md5map.get(key);
                if (filesWithMd5 == null) {
                    filesWithMd5 = new ArrayList<>(2);
                    md5map.put(key, filesWithMd5);
                }
                filesWithMd5.add(entry[1]);
            }
        }
    }

    private static Map<String, Integer> readRanking(final String dirsFileName) throws IOException {
        final File dirPresedenceFile = dirsFileName == null ? null : new File(dirsFileName);
        int index = 0;
        final Map<String, Integer> ret = new HashMap<>();
        if (dirPresedenceFile == null)
            return new HashMap<>();
        else if (!dirPresedenceFile.exists())
            throw new FileNotFoundException("Directory prescendence file " + dirsFileName + " doesn't exist.");
        try (BufferedReader br = new BufferedReader(new FileReader(dirPresedenceFile));) {
            for (String line = br.readLine(); line != null; line = br.readLine())
                ret.put(line, new Integer(index++));
        }
        return ret;
    }

    private static int ranking(final String dir) {
        final Integer rank = ranking.get(dir);
        if (rank == null)
            return ranking.size() + dir.length();
        else return rank.intValue();
    }

    public static void main(final String[] args) throws Exception {
        final Map<String, List<String>> md52files = new HashMap<>();

        recheck(() -> Arrays.stream(Config.md5FilesToRead).forEach(fileName -> uncheck(() -> readMd5File(md52files, fileName))));
        System.out.println("num duplicate groups: " + md52files.values().stream().filter(l -> l.size() > 1).count());
        final File actionsFile = new File(Config.actionsFileName);
        try (final PrintWriter actions = new PrintWriter(new BufferedOutputStream(new FileOutputStream(actionsFile)), true);) {
            md52files.values().stream()
                    .filter(f -> f.size() > 1)
                    .filter(f -> f.stream().filter(v -> v.startsWith("/media/jim/Seagate Expansion Drive/Family Media/Animations")).count() > 0)
                    .forEach(f -> {
                        actions.println("# Disambiguating:");
                        f.stream().forEach(e -> actions.println("#     " + e));

                        final List<String> files = new ArrayList<>(f);
                        Collections.sort(files, new Comparator<String>() {

                            @Override
                            public int compare(final String o1, final String o2) {
                                final String p1 = new File(o1).getParent();
                                final String p2 = new File(o2).getParent();
                                if (p1.equals(p2)) {
                                    return o1.length() - o2.length();
                                }
                                return ranking(p1) - ranking(p2);
                            }
                        });

                        final String keeper = files.remove(0);
                        actions.println("KP " + keeper);
                        files.stream().forEach(c -> actions.println("RM " + c));
                    });
        }
    }
}
