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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class Md5Sifter {

	public static final String[] md5files = new String[] { "C:\\Users\\Jim\\Documents\\md5.pics.txt" };
	
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
	
	private static String makeBaseFileName(String fn) {
		String base = new File(fn).toPath().getFileName().toString();
		String[] tokens = base.split("\\.(?=[^\\.]+$)");
		if (tokens.length == 2)  return new File(new File(fn).getParent(), tokens[0]).getAbsolutePath();
		else return fn;
	}
	
	private static Set<String> roots = new HashSet<>(Arrays.asList(Arrays.stream(File.listRoots()).map(f -> f.toString()).toArray(String[]::new)));
	
	private static String selectPreferred(Set<String> preferedDirectories, String fn) {
		if (roots.contains(fn)) return null;
		File f = new File(fn);
		String parent = f.getParent();
		if (preferedDirectories.contains(parent))
			return parent;
		else 
			return selectPreferred(preferedDirectories,parent);
	}
	
	private static boolean isPreferred(Set<String> preferedDirectories, String fn) {
		return selectPreferred(preferedDirectories, fn) != null;
	}
	
	private static String chooseBetweenPreferred(String[] preferred, List<String> files, Set<String> preferredDirectories) {
		// this is String of the prefered are both subdirs of the selected dir
		Set<String> selectedPreferred= Arrays.stream(preferred).map(p -> selectPreferred(preferredDirectories,p)).collect(Collectors.toSet());
		if (selectedPreferred.size() == 1) {
			// return the shortest.
			String ret = preferred[0];
			for (String p : preferred) {
				if (ret.length() > p.length())
					ret = p;
			}
			return ret;
		}
		
		throw new IllegalArgumentException();
	}
	
	private static void makeChoiceList(List<String> choices, String fn) {
		String parent = new File(fn).getParent();
		if (!roots.contains(parent)) {
			choices.add(parent);
			makeChoiceList(choices,parent);
		}
	}
	
	private static void keeper(String keeper, List<String> filesWithSameMd5, PrintWriter actions) {
		actions.println("KP \"" + keeper + "\"");
		for (String fn : filesWithSameMd5) {
			if (!keeper.equals(fn))
				actions.println("RM " + fn);
		}
	}
	
	private static void choose(List<String> filesList, PrintWriter actions, Set<String> preferedDirectories, Scanner scanner) {
		if (filesList.size() > 1) {
			List<String> filesWithSameMd5 = new ArrayList<>(filesList);
			actions.println("### " + filesWithSameMd5);

			// go through every pair and see if they are the same except for a suffix.
			boolean changed = false;
			for (boolean done = false; !done; ) {
				done = true;
				for (int i = 0; i < filesWithSameMd5.size() && done == true; i++) {
					for (int j = i + 1; j < filesWithSameMd5.size(); j++) {
						String fni = makeBaseFileName(filesWithSameMd5.get(i));
						String fnj = makeBaseFileName(filesWithSameMd5.get(j));
						if (fni.startsWith(fnj)) {
							actions.println("RM " + filesWithSameMd5.get(i));
							filesWithSameMd5.remove(i);
							done = false;
							changed = true;
							break;
						} else if (fnj.startsWith(fni)) {
							actions.println("RM " + filesWithSameMd5.get(j));
							filesWithSameMd5.remove(j);
							done = false;
							changed = true;
							break;
						}
					}
				}
			}
			
			if (changed) 
				choose(filesWithSameMd5, actions, preferedDirectories, scanner);
			
			// check to see if there's a preferred directory
			String[] preferred = filesWithSameMd5.stream().filter(fileName -> isPreferred(preferedDirectories, fileName)).toArray(String[]::new);
			if (preferred.length > 1) {
				keeper(chooseBetweenPreferred(preferred, filesWithSameMd5, preferedDirectories), filesWithSameMd5, actions);
			}
			else if (preferred.length == 0){
				@SuppressWarnings("unchecked")
				final List<String>[] choices = new List[filesWithSameMd5.size()];
				for (int i = 0; i < choices.length; i++) {
					choices[i] = new ArrayList<>();
					makeChoiceList(choices[i],filesWithSameMd5.get(i));
				}

				System.out.println("For the following ambiguity:");
				filesWithSameMd5.stream().forEach(fn -> System.out.println(fn));
				System.out.println("=============================");
				System.out.println("Select row to use that directory as preferred:");

				Map<String,String> chosenDirs = new HashMap<>();
				for (int i = 0; i < choices.length; i++) {
					List<String> cur = choices[i];
					for (int j = 0; j < cur.size(); j++) {
						String key = Integer.toString(i) + Integer.toString(j);
						System.out.println(key + ") " + cur.get(j));
						chosenDirs.put(key, cur.get(j));
					}
				}
				String choice = scanner.nextLine();

				String dirChosen = chosenDirs.get(choice);
				if (dirChosen == null) {
					System.out.println("Invalid choice!");
					choose(filesWithSameMd5, actions, preferedDirectories, scanner);
				}

				preferedDirectories.add(dirChosen);
				choose(filesWithSameMd5, actions, preferedDirectories, scanner);
			} else {
				keeper(preferred[0],filesWithSameMd5,actions);
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		Map<String, List<String>>  md52files = new HashMap<>();

		recheck(() -> Arrays.stream(md5files).forEach(fileName -> uncheck(() -> readMd5File(md52files,fileName)) ));
		System.out.println("num duplicate groups: " + md52files.values().stream().filter(l -> l.size() > 1).count());
		Set<String> preferedDirectories = new HashSet<>();
		
		Scanner scanner = new Scanner(System.in);
		
		File actionsFile = new File(actionsFileName);
		PrintWriter actions = new PrintWriter(new BufferedOutputStream(new FileOutputStream(actionsFile)),true);
		
		md52files.entrySet().stream().forEach(e -> {
			List<String> filesWithSameMd5 = new ArrayList<>(e.getValue());
			
			choose(filesWithSameMd5,actions, preferedDirectories,scanner);
		});
	}
}
