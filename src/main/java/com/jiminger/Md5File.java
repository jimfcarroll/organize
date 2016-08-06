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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.twmacinta.util.MD5;

public class Md5File {
	
	public static Map<String, String> readMd5FileLookup(String... fileNames) throws IOException {
		final Map<String,String> file2Md5 = new HashMap<String, String>();
		recheck(() -> Arrays.stream(fileNames).forEach(fileName -> uncheck(() -> {
			if (fileName != null) {
				File file = new File(fileName);
				if (file.exists()) {
					try (BufferedReader br = new BufferedReader(new FileReader(file));) {
						for (String line = br.readLine(); line != null; line = br.readLine()) {
							String[] entry = line.split("\\|\\|");
							if (entry.length != 2)
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
	
	public static Map<String,List<String>> readMd5File(String... fileNames) throws IOException {
		Map<String,List<String>> md5map = new HashMap<>();
		recheck(() -> Arrays.stream(fileNames).forEach( fileName -> uncheck(() -> {
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
			}})));
		return md5map;
	}
	   
	public static void makeMd5File(String md5FileToWrite, String[] md5FilesToRead, String[] directoriesToScan, String failedFile, boolean deleteEmtyDirs) throws IOException {
		final Map<String,String> file2md5 = readMd5FileLookup(Stream.concat(Stream.of(md5FileToWrite), 
				Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new));
		
		File md5File = new File(md5FileToWrite);
		try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile))) : new PrintWriter(System.err);
				PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

			// pass to calc md5
			recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck( () -> {
				File directory = new File(d);
				if (!directory.exists()) 
					failed.println(directory.toURI().toString() + "||" + "doesn't exist" );
				else {
					doMd5(md5os, directory, file2md5, deleteEmtyDirs);
				}
			})));

		}
	}
	
	static public void main(String[] args) throws Exception {
		makeMd5File(Config.md5FileToWrite, Config.md5FilesToRead, Config.directoriesToScan, Config.failedFile, Config.deleteEmptyDirs);
		System.out.println("Finished Clean");
	}

	private static void doMd5(PrintWriter md5os, File file, Map<String, String> existing, boolean deleteEmtyDirs) throws IOException {
		if (!file.exists())
			throw new FileNotFoundException("File " + file + " doesn't exist.");

		if (file.isDirectory()) {
			File[] dirContents = file.listFiles();
			if (dirContents == null || dirContents.length == 0) {
				System.out.println("Empty directory: \"" + file + "\"");
				if (!file.delete()) {
					System.out.println("FAILED: to delete empty directory: " + file.getAbsolutePath());
				}
			} else {
				recheck(() -> Arrays.stream(dirContents).forEach(f -> uncheck(() -> doMd5(md5os, f, existing, deleteEmtyDirs))));
			}
		} else {
			String existingMd5 = Optional.ofNullable(existing).map(e -> e.get(file.getAbsolutePath())).orElse(null);
			if (existingMd5 != null)
				md5os.println(existingMd5 + "||" + file.getAbsolutePath());
			else 
				// otherwise it's a regular file
				printHash(md5os, file);
		}
	}

	private static void printHash(PrintWriter out, File file) throws IOException {
		printHash(out, MD5.getHash(file), file);
	}

	private static void printHash(PrintWriter out, byte[] hash, File file) throws IOException {
		out.println(MD5.asHex(hash) + "||" + file.getAbsolutePath());
	}
}

