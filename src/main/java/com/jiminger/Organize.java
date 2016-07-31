package com.jiminger;

import static net.dempsy.util.Functional.recheck;
import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.twmacinta.util.MD5;

public class Organize {
   private static boolean dryrun = false;
   
   static String[] filesToSkipAr = { "thumbs.db", "thumbs.db:encryptable", "recycler","zbthumbnail.info", "zbthumbnail (2).info", "$recycle.bin",
      "system volume information", "desktop.ini", "desktop (2).ini", ".appledouble", ".ds_store", "digikam4.db", "thumbnails-digikam.db",
      "sample pictures.lnk", "itunes", "album artwork", "amazon mp3", "podcasts", "picasa.ini" };
   
   static Set<String> filesToSkip = new HashSet<>();
   static {
      filesToSkip.addAll(Arrays.asList(filesToSkipAr));
   }

   static Predicate<File> filter = sourceFile -> !(filesToSkip.contains(sourceFile.getName().toLowerCase()) || sourceFile.getName().startsWith("."));
   
   private static Map<String,List<String>> readMd5File(String... fileNames) throws IOException {
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
   
   private static Map<String, String> invert(Map<String,List<String>> md52files) {
	   final Map<String,String> ret = new HashMap<>();
	   md52files.entrySet().stream().forEach(e -> {
		   final String key = e.getKey();
		   e.getValue().stream().forEach(fn -> ret.put(fn, key));
	   });
	   return ret;
   }
   
   static PrintWriter out = new PrintWriter(System.out);

   static public void main(String[] args) throws Exception {
      String srcDirectoryStr = Config.srcDirectoryStr;
      String dstDirectoryStr = Config.dstDirectoryStr;
      
      String md5FileToWrite =  Config.md5FileToWrite;
      String[] md5FilesToRead = Config.md5FilesToRead;
      String failedFile = Config.failedFile;
      String dups = Config.dups;
      String outFile = Config.outFile;
      
      if (outFile != null)
    	  out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFile,Config.appendOutfile)), true);
      
      out.println("CPDIR: Copying files from " + srcDirectoryStr + " into " + dstDirectoryStr);
      
      File dstDirectory = new File(dstDirectoryStr);
      if(dstDirectory.exists() && dstDirectory.isDirectory()) {
    	  out.print("MKMD5: Making MD5 file for " + dstDirectoryStr + " ...");
    	  out.flush();
    	  Md5File.makeMd5File(md5FileToWrite, md5FilesToRead, new String[] { dstDirectoryStr } , failedFile);
    	  out.println("Done!");
      }      
      File srcDirectory = new File(srcDirectoryStr);
      
      Map<String, List<String>> md52files = readMd5File(Stream.concat(Stream.of(md5FileToWrite), 
				Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new));
      Map<String, String> files2Md5 = invert(md52files);
      
      if (!srcDirectory.exists()) {
         System.err.println("The directory \"" + srcDirectoryStr + "\" doesn't exist.");
         System.exit(1);
      }
      
      if (!dstDirectory.exists()) {
         if (!dstDirectory.mkdirs()) {
            System.err.println("ERROR:Failed to create the destination directory:" + dstDirectoryStr);
            System.exit(1);
         }
      }
      
      try (PrintWriter md5os = (md5FileToWrite != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5FileToWrite,true))) : null;
            PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile,true))) : new PrintWriter(System.err);) {
         copyFromTo(srcDirectory,dstDirectory,filter,md52files, files2Md5,md5os,failed,dups);
      }
      out.println("DONE: Finished Clean");
   }
   
   public static void copyFromTo(File srcDirectory, File dstDirectory, Predicate<File> copyFilter, Map<String, List<String>> md52files, Map<String, String> files2Md5, PrintWriter md5os, PrintWriter failed, String dups) throws IOException {
      if (!dstDirectory.exists()) {
         if (!dstDirectory.mkdirs())
            throw new FileNotFoundException("Could not create \"" + getName(dstDirectory) + "\"");
      }
      else
         if (!dstDirectory.isDirectory())
            throw new FileNotFoundException("The file \"" + getName(dstDirectory) + "\" is not a directory.");
      
      List<File> subDirs = new ArrayList<File>();
      if (!srcDirectory.exists())
         throw new FileNotFoundException("The directory \"" + getName(srcDirectory) + "\" doesn't exist.");

      if (!srcDirectory.isDirectory())
         throw new FileNotFoundException("The file \"" + getName(srcDirectory) + "\" is not a directory.");

      File[] files = srcDirectory.listFiles();
      if (files == null) {
         if (failed != null) {
            failed.println("# The following is an entire directory failure:");
            failed.println( getName(srcDirectory) + " !=> " + getName(dstDirectory));
         }
         out.println("FAIL: Failed to copy directory " + getName(srcDirectory));
         files = new File[0];
      }
      
      for (File file : files) {
         if (file.isDirectory())
            subDirs.add(file);
         else
            conditionalCopyTo(file,new File(dstDirectory,file.getName()),copyFilter, md52files, files2Md5, md5os, failed, dups, 0);
      }
      
      md5os.flush();

      if (subDirs.size() > 0) {
         for (File subdir : subDirs) {
            if (copyFilter == null || copyFilter.test(subdir)) {
               File newDestDirectory = new File(dstDirectory,subdir.getName());
               copyFromTo(subdir,newDestDirectory, copyFilter, md52files, files2Md5, md5os, failed, dups);
            }
            else
               out.println("SKIP: Skipping " + getName(subdir));
         }
      }
   }
      
   static public void conditionalCopyTo(File from, File to, Predicate<File> copyFilter, 
		   Map<String, List<String>> md52files, Map<String, String> files2Md5,
		   PrintWriter md5, PrintWriter failed, String copyDupFolder, int dupCount) throws IOException
   {
      if (copyFilter != null && !copyFilter.test(from)) {
         out.println("SKIP: Skipping " + getName(from));
         return;
      }
      
      // see if the source file md5 exists
      String md5From = files2Md5.get(from.getAbsolutePath());
      if (md5From == null) {
    	  md5From = MD5.asHex(MD5.getHash(from)).toString();
      }
      
      // now see if it already exists at the destination somewhere
      List<String> dstWithSameMd5 = md52files.get(md5From);
      if (dstWithSameMd5 != null) {
    	  out.println("EXISTS: File \"" + from + "\" already exists in destination at " + dstWithSameMd5);
    	  return;
      }
      
      // check to see if the file already exists and if it's the same size
      if (to.exists() && to.isFile()) {
    	  if (copyDupFolder != null) {
    		  File parent = to.getParentFile();
    		  if (parent.getName().equals(copyDupFolder + (dupCount - 1)))
    			  parent = parent.getParentFile();

    		  File newDestDirectory = new File(parent,copyDupFolder + dupCount);
    		  if (!newDestDirectory.exists()) {
    			  out.println("MKDIR " + newDestDirectory); 
    			  if (!dryrun) newDestDirectory.mkdirs();
    		  }
    		  conditionalCopyTo(from,new File(newDestDirectory,from.getName()),copyFilter,md52files, files2Md5, md5,failed,copyDupFolder, ++dupCount);
    	  } else {
    		  out.println("EXISTS: File \"" + getName(from) + "\" already exists at the destination.");
    	  }
      }
      else {
    	  if (!dryrun) {
    		  if (copyTo(from,to,md5,md5From))
    			  transferAttributes(from, to);
    	  }
      }
   }
   
   static public boolean copyTo(File from, File to, PrintWriter md5, String srcMd5) throws IOException {
	   try { simpleCopyFile(from,to,md5,srcMd5); return true; }
	   catch (IOException ioe) { out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   out.println ("ERROR: Trying again....");
	   try { simpleCopyFile(from,to,md5,srcMd5); return true; }
	   catch (IOException ioe) { out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   out.println ("ERROR: Trying again using memory mapping ....");
	   try { memMapCopyFile(from,to,md5,srcMd5); return true; }
	   catch (IOException ioe) { out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   out.println ("ERROR: Trying again using persistent byte copy ....");
	   try { persistentBytewiseCopyTo(from,to,md5,srcMd5); return true; }
	   catch (IOException ioe) { out.println("ERROR: Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   
	   // if we got here then this failed... .so we should remove the to file.
	   if (to.exists())
	      try { Files.delete(to.toPath()); } catch (IOException ioe) {
	         out.println("ERROR: Failed to delete " + to.getAbsolutePath() + " " + ioe.getLocalizedMessage());
	   }
	   return false;
   }

   static final public int BUFSIZE = 10*1024*1024;
   static final public long MAX_FAILED_COUNT = 100;
   
   static String getName(File file) {
      try {
         return file.getCanonicalPath();
      }
      catch (IOException ioe) {
         return "/...unknown path.../" + file.getName(); 
      }
   }
   
   static void transferAttributes(File from, File to) {
      try {
         BasicFileAttributes fromAttrs = Files.readAttributes(from.toPath(), BasicFileAttributes.class);
         BasicFileAttributeView v = Files.getFileAttributeView(to.toPath(), BasicFileAttributeView.class);
         BasicFileAttributes toAttrs = v.readAttributes();

         FileTime creationTime = fromAttrs.creationTime();
         if (creationTime.compareTo(toAttrs.creationTime()) > 0)
            creationTime = toAttrs.creationTime();

         FileTime lastModifiedTime = fromAttrs.lastModifiedTime();
         if (lastModifiedTime.compareTo(toAttrs.lastModifiedTime()) > 0)
            lastModifiedTime = toAttrs.creationTime();

         v.setTimes(lastModifiedTime, null, creationTime);
      }
      catch (IOException ioe) {
         out.println("ERROR: Failed to transfer attributes for " + getName(from) + " ... continuing.");
      }
   }
   
   static public void persistentBytewiseCopyTo(File from, File to, PrintWriter md5, String srcMd5) throws IOException {
      out.println("PBCP: \"" + getName(from) + "\" to \"" + getName(to) + "\"");
      
      try (RandomAccessFile fir = new RandomAccessFile(from, "r");
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(to))) {
         byte[] buf = new byte[BUFSIZE];
         int i = 0;
         long pos = 0;
         long failedCount = 0;
         boolean done = false;
         while(!done)
         {
        	 try {
        		 buf[i++] = fir.readByte();
        		 pos++;
        		 failedCount = 0;
        	 }
        	 catch (EOFException eof) { done = true; } // we're done 
        	 catch (IOException ioe) {
        		 out.println("ERROR: Problem reading byte " + pos + " from file.");
        		 fir.seek(pos);
        		 failedCount++;
        		 if (failedCount > MAX_FAILED_COUNT)
        			 throw ioe;
        		 continue;
        	 }

        	 if (i == BUFSIZE || done) {
        		 fos.write(buf, 0, i);
        		 i = 0;
        	 }
         }
         fos.close();
         checkCopy(from, to, srcMd5, md5);
      }
   }
   
   public static void memMapCopyFile(File source, File dest, PrintWriter md5, String srcMd5) throws IOException {
      out.println("MMCP: \"" + getName(source) + "\" to \"" + getName(dest) + "\" using memory mapping");
        try (FileInputStream sourceis = new FileInputStream(source);
              FileOutputStream destos = new FileOutputStream(dest);
              FileChannel in = sourceis.getChannel();
              FileChannel out = destos.getChannel())
        {
             long size = in.size();
             MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
    
             out.write(buf);
        }
        
        checkCopy(source, dest, srcMd5, md5);
   }
   
    static public void simpleCopyFile(File src, File dest, PrintWriter md5, String srcMd5) throws IOException {
  	  out.println("SCOPY: " + src + " => " + dest);
      try(BufferedInputStream fis  = new BufferedInputStream(new FileInputStream(src));
    		  BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dest))) {
    	  byte[] buf = new byte[10*1024*1024];
    	  int i = 0;
    	  while((i=fis.read(buf))!=-1)
    		  fos.write(buf, 0, i);
      }
      checkCopy(src, dest, srcMd5, md5);
    }
    
    static void checkCopy(File src, File dest, String srcMd5, PrintWriter md5) throws IOException {
        String newmd5 = MD5.asHex(MD5.getHash(dest)).toString();
        if (!srcMd5.equals(newmd5)) { 
      	  dest.delete();
      	  throw new IOException("Copying " + src + " to " + dest + " resulted in corrupt file.");
        }
        md5.println(newmd5 + "||" + dest.getAbsolutePath());
    }
}

