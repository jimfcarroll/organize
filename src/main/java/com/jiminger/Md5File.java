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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.twmacinta.util.MD5;

public class Md5File {
	
	private static Map<String, String> readMd5FileLookup(String... fileNames) throws IOException {
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
	
	public static void makeMd5File(String md5FileToWrite, String[] md5FilesToRead, String[] directoriesToScan, String failedFile) throws IOException {
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
					doMd5(md5os, directory, file2md5);
				}
			})));

		}
	}
	
	static public void main(String[] args) throws Exception {
		
		makeMd5File(
				"C:\\Users\\Jim\\Documents\\md5.animations.txt", 
				null, 
				new String[] { "C:\\Users\\Jim\\Pictures\\Animations" }, 
				"C:\\Users\\Jim\\Documents\\failed.txt");

		System.out.println("Finished Clean");
	}

	private static void doMd5(PrintWriter md5os, File file, Map<String, String> existing) throws IOException {
		if (!file.exists())
			throw new FileNotFoundException("File " + file + " doesn't exist.");

		if (file.isDirectory()) {
			recheck(() -> Arrays.stream(file.listFiles()).forEach(f -> uncheck(() -> doMd5(md5os, f, existing))));
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

