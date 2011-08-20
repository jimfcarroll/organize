package com.jiminger;

import java.io.*;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
import java.util.*;

public class Organize
{
   static public void main(String[] args) throws Exception
   {
//      String srcDirectoryStr = "/media/FreeAgent Primary Drive/";
//      String dstDirectoryStr = "/media/FreeAgent Secondary Drive/";
//      String srcDirectoryStr = "/media/FreeAgent Secondary Drive/";
//      String dstDirectoryStr = "/media/FreeAgent Primary Drive/";
      String dstDirectoryStr = "/home/jim/Desktop/OldMusic";
      String srcDirectoryStr = "/home/jim/Desktop/Ginger's Music";
      
      File srcDirectory = new File(srcDirectoryStr);
      File dstDirectory = new File(dstDirectoryStr);
      
      if (!srcDirectory.exists())
      {
         System.out.println("The directory \"" + srcDirectoryStr + "\" doesn't exist.");
         System.exit(1);
      }
      
      if (!dstDirectory.exists())
      {
         if (!dstDirectory.mkdirs())
         {
            System.out.println("ERROR:Failed to create the destination directory:" + dstDirectoryStr);
            System.exit(1);
         }
      }
      
      copyFromTo(srcDirectory,dstDirectory,null);
   }
   
   public static interface CopyFilter
   {
      public boolean copySourceFile(File sourceFile);
   }
   
   public static void copyFromTo(File srcDirectory, File dstDirectory, CopyFilter copyFilter) throws IOException
   {
      if (!dstDirectory.exists())
      {

         if (!dstDirectory.mkdirs())
            throw new FileNotFoundException("Could not create \"" + dstDirectory.getCanonicalPath() + "\"");
      }
      else
         if (!dstDirectory.isDirectory())
            throw new FileNotFoundException("The file \"" + dstDirectory.getCanonicalPath() + "\" is not a directory."); 

      
      List<File> subDirs = new ArrayList<File>();
      if (!srcDirectory.exists())
         throw new FileNotFoundException("The directory \"" + srcDirectory.getCanonicalPath() + "\" doesn't exist.");

      if (!srcDirectory.isDirectory())
         throw new FileNotFoundException("The file \"" + srcDirectory.getCanonicalPath() + "\" is not a directory.");

      File[] files = srcDirectory.listFiles();
         
      for (File file : files)
      {
         //System.out.println("" + file.getName() + " at " + file.getCanonicalPath());
         if (file.isDirectory())
            subDirs.add(file);
         else
         {
            String name = file.getName();
            File destFile = new File(dstDirectory,name);
            if (copyFilter == null || copyFilter.copySourceFile(file))
            {
               conditionalCopyTo(file,destFile);
            }
            else
               System.out.println("The file " + file.getCanonicalPath() + " is being filtered out.");
         }
      }
      
      if (subDirs.size() > 0)
      {
         for (File subdir : subDirs)
         {
            if (!"RECYCLER".equals(subdir.getName()) && !"$RECYCLE.BIN".equals(subdir.getName())
                  && !"System Volume Information".equals(subdir.getName()))
            {
               File newDestDirectory = new File(dstDirectory,subdir.getName());
               copyFromTo(subdir,newDestDirectory, copyFilter);
            }
         }
      }
   }
   
   static public void conditionalCopyTo(File from, File to) throws IOException
   {
      if ("Thumbs.db".equals(to.getName()) || "ZbThumbnail.info".equals(to.getName()) || 
            "RECYCLER".equals(to.getName()) || "$RECYCLE.BIN".equals(to.getName()))
         return;

      // check to see if the file already exists and if it's the same size
      if (to.exists() && to.isFile())
      {
         if ("Thumbs.db".equals(to.getName()))
         {
            System.out.println("Skipping overwriting Thumbs.db at " + to.getCanonicalPath());
            return;
         }
         if ("ZbThumbnail.info".equals(to.getName()))
         {
            System.out.println("Skipping overwriting ZbThumbnail.info at " + to.getCanonicalPath());
            return;
         }
         
         long fromsize = from.length();
         long tosize = to.length();
         
         if (fromsize != tosize)
//            throw new IOException("The file at \"" + from.getCanonicalPath() + "\" cannot be copied to \"" +
//                  to.getCanonicalPath() + "\" because the file already there is a different size. Please remedy this and run again.");
            System.out.println("ERROR: The file at \"" + from.getCanonicalPath() + "\" cannot be copied to \"" +
               to.getCanonicalPath() + "\" because the file already there is a different size. Please remedy this and run again. skipping for now.");
//         else
//            System.out.println("File \"" + from.getCanonicalPath() + "\" already exists at the destination.");
      }
      else
         copyTo(from,to);
   }
   
   static public void copyTo(File from, File to) throws IOException
   {
//        long size = from.length();
//        if (size >= Integer.MAX_VALUE)
           simpleCopyFile(from,to);
//        else
//           quickCopyFile(from,to);
   }

//   static public void copyTo(File from, File to) throws IOException
//   {
//      System.out.println("Copying \"" + from.getCanonicalPath() + "\" to \"" + to.getCanonicalPath() + "\"");
//      FileChannel ic = null;
//      FileChannel oc = null;
//      try
//      {
//         ic = new FileInputStream(from).getChannel();
//         oc = new FileOutputStream(to).getChannel();
//         ic.transferTo(0, ic.size(), oc);
//      }
//      finally
//      {
//         if (ic != null) try { ic.close(); } catch (Throwable th){}
//         if (oc != null) try { oc.close(); } catch (Throwable th){}
//      }
//   }
   
//   public static void quickCopyFile(File source, File dest) throws IOException {
//      System.out.println("Copying \"" + source.getCanonicalPath() + "\" to \"" + dest.getCanonicalPath() + "\"");
//        FileChannel in = null, out = null;
//        try {          
//             in = new FileInputStream(source).getChannel();
//             out = new FileOutputStream(dest).getChannel();
//    
//             long size = in.size();
//             MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
//    
//             out.write(buf);
//    
//        } finally {
//             if (in != null)          in.close();
//             if (out != null)     out.close();
//        }
//   }
   
    static public void simpleCopyFile(File in, File out) throws IOException 
    {
      System.out.println("Copying \"" + in.getCanonicalPath() + "\" to \"" + out.getCanonicalPath() + "\"");
        BufferedInputStream fis  = null;
        BufferedOutputStream fos = null;
        try
        {
           fis  = new BufferedInputStream(new FileInputStream(in));
           fos = new BufferedOutputStream(new FileOutputStream(out));
           byte[] buf = new byte[10*1024*1024];
           int i = 0;
           while((i=fis.read(buf))!=-1)
              fos.write(buf, 0, i);
        }
        finally
        {
           if (fis != null) try { fis.close(); } catch (Throwable th) {}
           if (fos != null) try { fos.close(); } catch (Throwable th) {}
        }
    }
}

