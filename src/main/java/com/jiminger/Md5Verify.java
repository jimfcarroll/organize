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

import com.twmacinta.util.MD5;

public class Md5Verify {
	
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
	
	public static void verifyMd5File(String outputFile, String[] md5FilesToRead, String[] directoriesToScan, String failedFile) throws IOException {
		final Map<String,String> file2md5 = readMd5FileLookup(md5FilesToRead);
		
		File outFile = new File(outputFile);
		try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile))) : new PrintWriter(System.err);
				PrintWriter outos = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFile)));) {

			// pass to calc md5
			recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck( () -> {
				File directory = new File(d);
				if (!directory.exists()) 
					failed.println(directory.toURI().toString() + "||" + "doesn't exist" );
				else {
					doMd5(outos, directory, file2md5);
				}
			})));

		}
	}
	
	static public void main(String[] args) throws Exception {
		
		verifyMd5File(
				"C:\\Users\\Jim\\Documents\\verify.pics.txt", 
				new String[] { "I:\\md5.pics.txt" }, 
				new String[] { "I:\\Family Media\\Pictures" }, 
				"C:\\Users\\Jim\\Documents\\failed.txt");

		System.out.println("Finished Clean");
	}

	private static void doMd5(PrintWriter outos, File file, Map<String, String> existing) throws IOException {
		if (!file.exists())
			throw new FileNotFoundException("File " + file + " doesn't exist.");

		if (file.isDirectory()) {
			recheck(() -> Arrays.stream(file.listFiles()).forEach(f -> uncheck(() -> doMd5(outos, f, existing))));
		} else {
			String existingMd5 = existing.get(file.getAbsolutePath());
			if (existingMd5 != null) {
				String curDigest = new Md5Hash(MD5.getHash(file)).toString();
				if (!existingMd5.equals(curDigest))
					outos.println("BADMD5 " + file.getAbsolutePath());
			}
			else 
				outos.println("MISSING " + file.getAbsolutePath());
		}
	}

}

