package com.jiminger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.twmacinta.util.MD5;
import com.twmacinta.util.MD5InputStream;

public class Organize
{
   static String[] filesToSkipAr = { "Thumbs.db", "Thumbs.db:encryptable", "RECYCLER","ZbThumbnail.info","$RECYCLE.BIN",
      "System Volume Information", "desktop.ini", ".AppleDouble", ".DS_Store",
      "iTunes", "Album Artwork", "Amazon MP3", "Podcasts" };
   static CopyFilter filter = new CopyFilter()
   {
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
   
   static public void main(String[] args) throws Exception
   {
      // F:= Free Agent Secondary
//      String srcDirectoryStr = "F:\\Pictures";
//      String srcDirectoryStr = "F:\\Ginger's Backup Post Virus\\Pictures";
//      String srcDirectoryStr = "F:\\Ginger's Backup Post Virus\\Videos";
//      String srcDirectoryStr = "F:\\Ginger's Desktop\\school photos";
//      String srcDirectoryStr = "F:\\Music";
//      String srcDirectoryStr = "F:\\_CDCollection\\Music";
//      String srcDirectoryStr = "F:\\oldgames";
//      String srcDirectoryStr = "F:\\Videos";
//      String srcDirectoryStr = "F:\\dosgames";
//      String srcDirectoryStr = "F:\\Ginger's Computer's Public Documents - Where BigOven Recepies are Stored\\Documents\\My BigOven Recipes";
//      String srcDirectoryStr = "F:\\Ginger's Computer's Public Documents - Where BigOven Recepies are Stored\\Documents\\My BigOven Recipes.org";
//      String srcDirectoryStr = "F:\\Ginger's Backup Post Virus\\Ginger's Computer's Public Documents - Where BigOven Recepies are Stored\\Documents\\My BigOven Recipes.org";
//      String srcDirectoryStr = "F:\\Ginger's Backup Post Virus\\Ginger's Computer's Public Documents - Where BigOven Recepies are Stored\\Documents\\My BigOven Recipes";
//      String srcDirectoryStr = "F:\\Ginger's Desktop\\2009 school pics";
//      String srcDirectoryStr = "F:\\Ginger's Desktop";
//      String srcDirectoryStr = "F:\\Ginger's Documents";
//      String srcDirectoryStr = "F:\\Mommy's Cooking Backups";
      // I:= 2TB Backup (dual drive with problems)
//      String srcDirectoryStr = "I:\\Old Home Movie Film";
//      String srcDirectoryStr = "I:\\Home Video";
//      String srcDirectoryStr = "I:\\SystemDisks";
//      String srcDirectoryStr = "I:\\Backup\\Music";
//      String srcDirectoryStr = "I:\\Backup\\Pictures";
//      String srcDirectoryStr = "I:\\Backup\\Desktop\\Ginger's Music\\Amazon MP3";
//      String srcDirectoryStr = "I:\\Backup\\Desktop\\Ginger's Music";
//      String srcDirectoryStr = "I:\\Backup\\Desktop";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\Documents\\Finance";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\Documents";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\audio";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\Games";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\MRIs";
//      String srcDirectoryStr = "I:\\Backup\\WindowsDisk\\s8";
//      String srcDirectoryStr = "I:\\Backup\\vmdata";
//      String srcDirectoryStr = "I:\\Backup\\Videos\\Lukas Movie";
//      String srcDirectoryStr = "I:\\Backup\\Audio\\Spoken Word";
//      String srcDirectoryStr = "I:\\Backup\\Documents";
//      String srcDirectoryStr = "I:\\Backup\\oldgames";
//      String srcDirectoryStr = "I:\\ExtGigBackupBackup\\My Videos";
//      String srcDirectoryStr = "I:\\ExtGigBackupBackup\\s8";
//      String srcDirectoryStr = "I:\\WordMP3";
//      String srcDirectoryStr = "I:\\King-Homer-Backup\\C\\utils";
      // Z:=mybooklive
//      String srcDirectoryStr = "Z:\\EmperorHomer\\Users 2012-06-06 11;28;03 (Full)\\Public\\Videos";
//      String srcDirectoryStr = "Z:\\Shared Videos";
//      String srcDirectoryStr = "Z:\\Shared Pictures";
//      String srcDirectoryStr = "Z:\\Danny's music stuff";
//      String srcDirectoryStr = "Z:\\EmperorHomer\\Users 2012-06-06 11;28;03 (Full)\\Public\\Pictures";
//      String srcDirectoryStr = "Z:\\WordMP3";
//      String srcDirectoryStr = "Z:\\Finance";
//      String srcDirectoryStr = "Z:\\roms";
//      String srcDirectoryStr = "Z:\\gingersbackup\\Users\\ginger\\Music\\Music.DONTTAKEME";
//      String srcDirectoryStr = "Z:\\gingersbackup\\Users\\ginger\\_Music";
//      String srcDirectoryStr = "Z:\\gingersbackup\\Users\\ginger\\_Music\\iTunes\\iTunes Media\\Music";
//      String srcDirectoryStr = "Z:\\gingersbackup\\Users\\ginger\\Pictures";
//      String srcDirectoryStr = "Z:\\Home Video";
      //G:=ExtGig
//      String srcDirectoryStr = "G:\\dannysMovie";
//      String srcDirectoryStr = "G:\\Lukas Movie";
//      String srcDirectoryStr = "G:\\My Videos";
//      String srcDirectoryStr = "G:\\s8";
//      String srcDirectoryStr = "G:\\Spoken Word";
//      String srcDirectoryStr = "G:\\Spoken Word\\WordMP3";
//      String srcDirectoryStr = "G:\\vmdata";
//      String srcDirectoryStr = "G:\\olddosgames";
      //W:=4g - Maggie's media drive
//      String srcDirectoryStr = "W:\\1tb-laptop-backup\\00WordMp3 COMPLETE SET 2011";
//      String srcDirectoryStr = "W:\\1tb-laptop-backup\\MusicDisambiguated";
//      String srcDirectoryStr = "W:\\1tb-laptop-backup\\Old Home Movie Film";
//      String srcDirectoryStr = "W:\\1tb-laptop-backup\\WordMp3";
//      String srcDirectoryStr = "W:\\mybooklive\\JoshBackup\\Music\\iTunes\\iTunes Media\\Music";
//      String srcDirectoryStr = "W:\\mybooklive\\JoshBackup\\Music";
      // H: was moved to Y: after transfer to maggie
      // H:=Y:= Standup 4TB Seagate (with problems)
//      String srcDirectoryStr = "H:\\Home Movies";
//      String srcDirectoryStr = "H:\\media";
//      String srcDirectoryStr = "H:\\jim-backup\\Desktop\\Ginger's Music";
//      String srcDirectoryStr = "H:\\jim-backup\\Documents";
//      String srcDirectoryStr = "Y:\\jim-backup\\Music";
//      String srcDirectoryStr = "H:\\jim-backup\\Pictures";
//      String srcDirectoryStr = "H:\\jim-backup\\vmdata";
//      String srcDirectoryStr = "H:\\jim-backup\\olddosgames";
//      String srcDirectoryStr = "H:\\jim-backup\\WindowsDisk\\s8";
//      String srcDirectoryStr = "Y:\\jim-backup\\WindowsDisk\\Documents";
//      String srcDirectoryStr = "Y:\\jim-backup\\WindowsDisk\\Finance";
//      String srcDirectoryStr = "Y:\\jim-backup\\Videos\\Lukas Movie";
//      String srcDirectoryStr = "Y:\\System Backups\\Josh's Backups\\Music\\iTunes\\iTunes Media\\Music";
//      String srcDirectoryStr = "Y:\\System Backups\\Josh's Backups\\Music";
//      String srcDirectoryStr = "Y:\\System Backups\\Josh's Backups\\Music\\Amazon MP3";
//      String srcDirectoryStr = "Y:\\System Backups\\Josh's Backups\\Documents";
//      String srcDirectoryStr = "Y:\\System Backups\\Josh's Backups\\Desktop Backups\\PHONE PICS";

//      String dstDirectoryStr = "J:\\Family Media\\Pictures";
//      String dstDirectoryStr = "J:\\Family Media\\Pictures\\2009 school pics";
//      String dstDirectoryStr = "J:\\Family Media\\Pictures\\Josh - PHONE PICS";
//      String dstDirectoryStr = "J:\\Family Media\\Pictures\\school photos";
//      String dstDirectoryStr = "J:\\Audio\\Music";
//      String dstDirectoryStr = "J:\\Games";
//      String dstDirectoryStr = "J:\\Games\\vmdata";
//      String dstDirectoryStr = "J:\\Audio\\Spoken Word";
//      String dstDirectoryStr = "J:\\Audio\\Spoken Word\\WordMP3";
//      String dstDirectoryStr = "J:\\Audio\\Spoken Word\\WordMP3\\00WordMp3 COMPLETE SET 2011";
//      String dstDirectoryStr = "J:\\Audio\\Spoken Word\\WordMP3\\Word MP3";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Raw Footage";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Old Home Movie Film";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\CamCorder";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Kids Movies\\Josh-2009";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Kids Movies\\Lukas Movie";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Kids Movies\\Mini Matrix";
//      String dstDirectoryStr = "J:\\Family Media\\Videos\\Kids Movies\\dannysMovie";
//      String dstDirectoryStr = "J:\\Family Media\\Videos";
//      String dstDirectoryStr = "J:\\Kids Stuff\\Danny's music stuff";
//      String dstDirectoryStr = "J:\\Finance";
//      String dstDirectoryStr = "J:\\Games\\roms";
//      String dstDirectoryStr = "J:\\Projects\\s8";
//      String dstDirectoryStr = "J:\\Documents";
//      String dstDirectoryStr = "J:\\Documents\\MRIs";
//      String dstDirectoryStr = "J:\\Documents\\Josh's Documents";
//      String dstDirectoryStr = "J:\\Software";
//      String dstDirectoryStr = "J:\\My BigOven Recipes";
      
      // make a backup of the organizied music:
      String srcDirectoryStr = "X:\\Music";
      String dstDirectoryStr = "J:\\Organized Music";
      
      String md5FileStr = "J:\\md5.org-music.txt";
      
      String failedFile = "J:\\didntCopy.txt";
//      String failedFile = null;
      
      String copyDupFolder = "DUPS";
      
      File srcDirectory = new File(srcDirectoryStr);
      File dstDirectory = new File(dstDirectoryStr);
      File md5File = new File(md5FileStr);
      
      if (!srcDirectory.exists())
      {
         System.err.println("The directory \"" + srcDirectoryStr + "\" doesn't exist.");
         System.exit(1);
      }
      
      if (!dstDirectory.exists())
      {
         if (!dstDirectory.mkdirs())
         {
            System.err.println("ERROR:Failed to create the destination directory:" + dstDirectoryStr);
            System.exit(1);
         }
      }
      
      try (@SuppressWarnings("resource")
      PrintWriter md5os = (md5File != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File,true))) : null;
            @SuppressWarnings("resource")
            PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile,true))) : new PrintWriter(System.err)) {
         copyFromTo(srcDirectory,dstDirectory,filter,md5os,failed,copyDupFolder);
      }
      System.out.println("Finished Clean");
   }
   
   public static interface CopyFilter
   {
      public boolean copySourceFile(File sourceFile);
   }
   
   public static void copyFromTo(File srcDirectory, File dstDirectory, CopyFilter copyFilter, PrintWriter md5os, PrintWriter failed, String copyDupFolder) throws IOException
   {
      if (!dstDirectory.exists())
      {
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

      if (subDirs.size() > 0)
      {
         for (File subdir : subDirs)
         {
            if (!filesToSkip.contains(subdir.getName()) && (copyFilter == null || copyFilter.copySourceFile(subdir)))
            {
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
      if (to.exists() && to.isFile())
      {
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
   
   static public boolean copyTo(File from, File to, PrintWriter md5) throws IOException
   {
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
   
   static public void persistentBytewiseCopyTo(File from, File to, PrintWriter md5) throws IOException
   {
      System.out.println("Copying \"" + getName(from) + "\" to \"" + getName(to) + "\"");
      
      try (RandomAccessFile fir = new RandomAccessFile(from, "r");
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(to)))
      {
         
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
        	 catch (IOException ioe)
        	 {
        		 System.err.println("Problem reading byte " + pos + " from file.");
        		 fir.seek(pos);
        		 failedCount++;
        		 if (failedCount > MAX_FAILED_COUNT)
        			 throw ioe;
        		 continue;
        	 }

        	 if (i == BUFSIZE || done)
        	 {
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
   
    static public void simpleCopyFile(File in, File out, PrintWriter md5) throws IOException 
    {
      System.out.println("Copying \"" + getName(in) + "\" to \"" + getName(out) + "\"");
        try(MD5InputStream md5is = new MD5InputStream(new FileInputStream(in));
           BufferedInputStream fis  = new BufferedInputStream(md5is);
           BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(out)))
        {
           byte[] buf = new byte[10*1024*1024];
           int i = 0;
           while((i=fis.read(buf))!=-1)
              fos.write(buf, 0, i);
           md5is.close();
           printHash(md5,md5is.hash(),out);
        }
    }
}

