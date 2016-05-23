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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class Md5Sifter2 {

	public static final String[] md5files = new String[] { "C:\\Users\\Jim\\Documents\\md5.pics.txt" };
	public static final String dirPrescedence = "C:\\Users\\Jim\\dirs.txt";
	public static final String actionsFileName = "C:\\Users\\Jim\\Documents\\actions.txt";

	private static void readMd5File(Map<String,List<String>> md5map, String fileName) throws IOException {
		File file = new File(fileName);
		if (!file.exists()) throw new FileNotFoundException("MD5 file " + fileName + " doesn't exist.");
		try (BufferedReader br = new BufferedReader(new FileReader(file));) {
			for (String line = br.readLine(); line != null; line = br.readLine()) {
				String[] entry = line.split("\\|\\|");
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
	
	private static Map<String,Integer> readRanking(String dirsFileName) throws IOException {
		File file = new File(dirsFileName);
		int index = 0;
		Map<String,Integer> ret = new HashMap<>();
		if (!file.exists()) throw new FileNotFoundException("Directory prescendence file " + dirsFileName + " doesn't exist.");
		try (BufferedReader br = new BufferedReader(new FileReader(file));) {
			for (String line = br.readLine(); line != null; line = br.readLine())
				ret.put(line, new Integer(index++));
		}
		return ret;
	}
	
	
	public static void main(String[] args) throws Exception {
		Map<String, List<String>>  md52files = new HashMap<>();

		recheck(() -> Arrays.stream(md5files).forEach(fileName -> uncheck(() -> readMd5File(md52files,fileName)) ));
		System.out.println("num duplicate groups: " + md52files.values().stream().filter(l -> l.size() > 1).count());
		final Map<String,Integer> ranking = readRanking(dirPrescedence);
		final File actionsFile = new File(actionsFileName);
		try (final PrintWriter actions = new PrintWriter(new BufferedOutputStream(new FileOutputStream(actionsFile)),true);) {
			md52files.values().stream().forEach(f -> {
				if (f.size() > 1) {
					actions.println("# Disambiguating:" );
					f.stream().forEach(e -> actions.println("#     " + e));

					List<String> files = new ArrayList<>(f);
					Collections.sort(files, new Comparator<String>() {

						@Override
						public int compare(String o1, String o2) {
							String p1 = new File(o1).getParent();
							String p2 = new File(o2).getParent();
							if (p1.equals(p2)) {
								return o1.length() - o2.length();
							}
							return ranking.get(p1) - ranking.get(p2);
						}
					});

					String keeper = files.remove(0);
					actions.println("KP " + keeper);
					files.stream().forEach(c -> actions.println("RM " + c));
				}
			});
		}
	}
}
