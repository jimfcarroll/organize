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

import com.twmacinta.util.MD5;

public class MakeMd5 {
	
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
	
	static public void main(String[] args) throws Exception {

		// make a backup of the organizied music:
		String[] directoryStrs = new String[] { "C:\\Users\\Jim\\Pictures\\Pictures" };
		
//		String md5FileStr = "C:\\Users\\Jim\\Documents\\md5.pics-reduced.txt";
//		String md5FileToRead = "C:\\Users\\Jim\\Documents\\md5.pics.txt";
		String md5FileStr = "C:\\Users\\Jim\\Documents\\md5.pics.txt";
		String md5FileToRead = null;
		
		String failedFile = "C:\\Users\\Jim\\Documents\\failed.txt";

		final Map<String,String> file2md5 = readMd5FileLookup(md5FileStr, md5FileToRead);
		
		File md5File = new File(md5FileStr);
		try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile))) : new PrintWriter(System.err);
				PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {

			// pass to calc md5
			recheck(() -> Arrays.stream(directoryStrs).forEach(d -> uncheck( () -> {
				File directory = new File(d);
				if (!directory.exists()) 
					failed.println(directory.toURI().toString() + "||" + "doesn't exist" );
				else {
					doMd5(md5os, directory, file2md5);
				}
			})));

		}
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
		out.println(new Md5Hash(hash) + "||" + file.getAbsolutePath());
	}
}

