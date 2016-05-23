package com.jiminger;

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
import java.util.Set;

import com.twmacinta.util.MD5;
import com.twmacinta.util.MD5InputStream;

import net.dempsy.util.Functional;
import static net.dempsy.util.Functional.*;

public class Organize
{
   static String[] filesToSkipAr = { "Thumbs.db", "Thumbs.db:encryptable", "RECYCLER","ZbThumbnail.info","$RECYCLE.BIN",
      "System Volume Information", "desktop.ini", ".AppleDouble", ".DS_Store",
      "iTunes", "Album Artwork", "Amazon MP3", "Podcasts" };
   
   static CopyFilter filter = new CopyFilter() {
      @Override
      public boolean copySourceFile(final File sourceFile) {
         final String name = sourceFile.getName();
         if (name.startsWith("_"))
            return false;
         return (!(name.startsWith("AlbumArt") && (name.endsWith(".jpg") || name.endsWith(".JPG"))));
      }
   };
   static Set<String> filesToSkip = new HashSet<>();
   static {
      filesToSkip.addAll(Arrays.asList(filesToSkipAr));
   }
   
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

   static public void main(String[] args) throws Exception {
      // make a backup of the organizied music:
      String srcDirectoryStr = "C:\\Users\\Jim\\Pictures\\Pictures.fromBigBU4TBDamaged";
      String dstDirectoryStr = "C:\\Users\\Jim\\Pictures\\Pictures";
      
      String[] md5Files = new String[] { "C:\\Users\\Jim\\Documents\\md5.pics.txt" };
      String failedFile = "C:\\Users\\Jim\\Documents\\didntCopy.txt";
      
      File srcDirectory = new File(srcDirectoryStr);
      File dstDirectory = new File(dstDirectoryStr);
      
      Map<String, List<String>> md52files = readMd5File(md5Files);
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
      
      try (PrintWriter md5os = (md5File != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File,true))) : null;
            PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile,true))) : new PrintWriter(System.err)) {
         copyFromTo(srcDirectory,dstDirectory,filter,md5os,failed);
      }
      System.out.println("Finished Clean");
   }
   
   public static interface CopyFilter {
      public boolean copySourceFile(File sourceFile);
   }
   
   public static void copyFromTo(File srcDirectory, File dstDirectory, CopyFilter copyFilter, PrintWriter md5os, PrintWriter failed, String copyDupFolder) throws IOException
   {
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
         System.out.println("Failed to copy directory " + getName(srcDirectory));
         files = new File[0];
      }
      
      for (File file : files)
      {
         //System.out.println("" + file.getName() + " at " + getName(file());
         if (file.isDirectory())
            subDirs.add(file);
         else
            conditionalCopyTo(file,new File(dstDirectory,file.getName()),copyFilter, md5os, failed, copyDupFolder,0);
      }
      
      md5os.flush();

      if (subDirs.size() > 0) {
         for (File subdir : subDirs) {
            if (!filesToSkip.contains(subdir.getName()) && (copyFilter == null || copyFilter.copySourceFile(subdir))) {
               File newDestDirectory = new File(dstDirectory,subdir.getName());
               copyFromTo(subdir,newDestDirectory, copyFilter, md5os, failed, copyDupFolder);
            }
            else
               System.out.println("Skipping " + getName(subdir));
         }
      }
   }
      
   static public void conditionalCopyTo(File from, File to, CopyFilter copyFilter, PrintWriter md5, PrintWriter failed, String copyDupFolder, int dupCount) throws IOException
   {
      if (filesToSkip.contains(to.getName()) || (copyFilter != null && !copyFilter.copySourceFile(from))) {
         System.out.println("Skipping " + getName(from));
         return;
      }

      // check to see if the file already exists and if it's the same size
      if (to.exists() && to.isFile()) {
         long fromsize = from.length();
         long tosize = to.length();
         
         if (fromsize != tosize) {
            if (copyDupFolder != null) {
               File parent = to.getParentFile();
               if (parent.getName().equals(copyDupFolder + (dupCount - 1)))
                  parent = parent.getParentFile();
               
               File newDestDirectory = new File(parent,copyDupFolder + dupCount);
               if (!newDestDirectory.exists())
                  newDestDirectory.mkdirs();
//               System.out.println("Copying Dup ... ");
               conditionalCopyTo(from,new File(newDestDirectory,from.getName()),copyFilter,md5,failed,copyDupFolder, ++dupCount);
            }
            else {
//            throw new IOException("The file at \"" + getName(from) + "\" cannot be copied to \"" +
//                  getName(to) + "\" because the file already there is a different size. Please remedy this and run again.");
               failed.println( getName(from) + " !=> " + getName(to));
            }
         }
         else {
            System.out.println("File \"" + getName(from) + "\" already exists at the destination.");
            transferAttributes(from, to);
         }
      }
      else {
         if (copyTo(from,to,md5))
            transferAttributes(from, to);
      }
   }
   
   static public boolean copyTo(File from, File to, PrintWriter md5) throws IOException {
	   try { simpleCopyFile(from,to,md5); return true; }
	   catch (IOException ioe) { System.err.println("Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   System.out.println ("Trying again....");
	   try { simpleCopyFile(from,to,md5); return true; }
	   catch (IOException ioe) { System.err.println("Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   System.out.println ("Trying again using memory mapping ....");
	   try { memMapCopyFile(from,to,md5); return true; }
	   catch (IOException ioe) { System.err.println("Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   System.out.println ("Trying again using persistent byte copy ....");
	   try { persistentBytewiseCopyTo(from,to,md5); return true; }
	   catch (IOException ioe) { System.err.println("Failed on attempt to copy \"" + from + "\" to \"" + to + ".\" due to " + ioe.getLocalizedMessage()); }
	   
	   // if we got here then this failed... .so we should remove the to file.
	   if (to.exists())
	      try { Files.delete(to.toPath()); } catch (IOException ioe) {
	         System.err.println("Failed to delete " + to.getAbsolutePath() + " " + ioe.getLocalizedMessage());
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
         System.out.println("Failed to transfer attributes for " + getName(from) + " ... continuing.");
      }
   }
   
   static public void persistentBytewiseCopyTo(File from, File to, PrintWriter md5) throws IOException {
      System.out.println("Copying \"" + getName(from) + "\" to \"" + getName(to) + "\"");
      
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
        		 System.err.println("Problem reading byte " + pos + " from file.");
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
         printHash(md5,to);
      }
   }
   
   private static void printHash(PrintWriter out, File file) throws IOException {
      printHash(out, MD5.getHash(file), file);
   }
   
   private static void printHash(PrintWriter out, byte[] hash, File file) throws IOException {
      out.println(new Md5Hash(hash) + "  " + file.getAbsolutePath());
   }
   
   public static void memMapCopyFile(File source, File dest, PrintWriter md5) throws IOException {
      System.out.println("Copying \"" + getName(source) + "\" to \"" + getName(dest) + "\" using memory mapping");
        try (FileInputStream sourceis = new FileInputStream(source);
              FileOutputStream destos = new FileOutputStream(dest);
              FileChannel in = sourceis.getChannel();
              FileChannel out = destos.getChannel())
        {
             long size = in.size();
             MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
    
             out.write(buf);
        }
        
        printHash(md5,dest);
   }
   
    static public void simpleCopyFile(File in, File out, PrintWriter md5) throws IOException {
      System.out.println("Copying \"" + getName(in) + "\" to \"" + getName(out) + "\"");
      try(MD5InputStream md5is = new MD5InputStream(new FileInputStream(in));
    		  BufferedInputStream fis  = new BufferedInputStream(md5is);
    		  BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(out))) {
    	  byte[] buf = new byte[10*1024*1024];
    	  int i = 0;
    	  while((i=fis.read(buf))!=-1)
    		  fos.write(buf, 0, i);
    	  md5is.close();
    	  printHash(md5,md5is.hash(),out);
      }
    }
}

