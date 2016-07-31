package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;

import com.twmacinta.util.MD5;

public class Md5Verify {
	
	public static void verifyMd5File(String outputFile, String[] md5FilesToRead, String[] directoriesToScan, String failedFile) throws IOException {
		final Map<String,String> file2md5 = Md5File.readMd5FileLookup(md5FilesToRead);
		
		File outFile = new File(outputFile);
		try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile))) : new PrintWriter(System.err);
				PrintWriter outos = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFile)));) {

			// pass to calc md5
			recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck( () -> {
				File directory = new File(d);
				if (!directory.exists()) 
					failed.println(directory.toURI().toString() + "||" + "doesn't exist" );
				else {
					doMd5(outos, directory, file2md5, failed);
				}
			})));

		}
	}
	
	static public void main(String[] args) throws Exception {
//		deleteDups(Config.md5FileToWrite);
		verifyMd5File(
				Config.verifyOutputFile, 
				Config.md5FilesToRead, 
				Config.directoriesToScan, 
				Config.failedFile);

		System.out.println("Finished Clean");
	}

	private static void doMd5(PrintWriter outos, File file, Map<String, String> existing, PrintWriter failed) throws IOException {
		System.out.println(file);
		if (!file.exists())
			throw new FileNotFoundException("File " + file + " doesn't exist.");
		
		if (file.isDirectory()) {
			File[] subdirs = file.listFiles();
			if (subdirs == null) {
				if (failed != null) 
					failed.println("CANT GET SUBDIRS:" + file.getAbsolutePath());
			} else
				recheck(() -> Arrays.stream(subdirs).forEach(f -> uncheck(() -> doMd5(outos, f, existing, failed))));
		} else {
			String existingMd5 = existing.get(file.getAbsolutePath());
			if (existingMd5 != null) {
				String curDigest = MD5.asHex(MD5.getHash(file)).toString();
				if (!existingMd5.equals(curDigest)) {
					outos.println("BADMD5 " + file.getAbsolutePath());
					outos.flush();
				}
			}
			else {
				outos.println("MSGMD5 " + file.getAbsolutePath());
				outos.flush();
			}
		}
	}

//	private static void deleteDups(String... md5FilesToRead) throws IOException {
//		final Map<String,List<String>> md52Files = Md5File.readMd5File(md5FilesToRead);
//		
//		// find dups
//		md52Files.entrySet().stream()
//		   .filter(e -> e.getValue().size() > 1)
//		   .map(e -> e.getValue())
//		   .forEach(fileNames -> {
//			   final Set<String> allNames = new HashSet<>();
//			   allNames.addAll(fileNames);
//			   final List<String> withDups = fileNames.stream().filter(n -> {
//				   File f = new File(n);
//				   //System.out.println(f.getParentFile().getAbsolutePath());
//				   return f.exists() && f.getParentFile().getName().startsWith("DUPS");
//			   }).collect(Collectors.toList());
//			   withDups.stream()
//			       .map(File::new)
//			       .forEach(f -> {
//			    	   System.out.println("removing: " + f.getAbsolutePath());
//			    	   f.delete();
//			    	   File p = f.getParentFile();
//			    	   File[] children = p.listFiles();
//			    	   if (children == null || children.length == 0) {
//			    		   System.out.println("deleting: " + p.getAbsolutePath());
//			    		   p.delete();
//			    	   }
//			       });
//		   });
//		
//	}
	
}

