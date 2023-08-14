package com.jiminger;

import java.io.IOException;

import net.dempsy.vfs.CopyingArchiveFileSystem;
import net.dempsy.vfs.SevenZArchiveFileSystem;
import net.dempsy.vfs.Vfs;
import net.dempsy.vfs.ZCompressedFileSystem;
import net.dempsy.vfs.bz.Bz2FileSystem;
import net.dempsy.vfs.gz.GzFileSystem;
import net.dempsy.vfs.tar.TarFileSystem;
import net.dempsy.vfs.xz.XzFileSystem;
import net.dempsy.vfs.zip.ZipFileSystem;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

public class VfsConfig {

    public static Vfs createVfs(final String[] passwordsToTry) throws IOException {

        final String osArch = System.getProperty("os.arch");
        System.out.println("OS Arch:" + osArch);
        if("aarch64".equals(osArch)) {
            try {
                SevenZip.initSevenZipFromPlatformJAR("Linux-arm64");
            } catch(final SevenZipNativeInitializationException sze) {
                throw new IOException(sze);
            }
        }

        // final var szfs = new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz" /* , "zip" */);
        // if(passwordsToTry != null && passwordsToTry.length > 0)
        // szfs.tryPasswords(passwordsToTry);
        //
        // return new Vfs(
        // new CopyZipFileSystem(),
        // szfs,
        // // new DecompressedFileSystem(new GzFileSystem()),
        // new GzFileSystem(),
        // new ZCompressedFileSystem(),
        // new Bz2FileSystem(),
        // new XzFileSystem()
        //
        // );

        SevenZArchiveFileSystem szfs;

        final var ret = new Vfs(
            // szfs = new SevenZArchiveFileSystem("sevenz", "rar", "tar", "tgz|gz", "tbz2|bz2", "txz|xz", "zip"),
            // new GzFileSystem(),
            // new ZCompressedFileSystem(),
            // new Bz2FileSystem()
            //
            // , new XzFileSystem()

            new CopyingArchiveFileSystem(new TarFileSystem()),
            new GzFileSystem(),
            new CopyingArchiveFileSystem(new ZipFileSystem()),
            new ZCompressedFileSystem(),
            new Bz2FileSystem(),
            new XzFileSystem(),
            szfs = new SevenZArchiveFileSystem()

        );

        if(passwordsToTry != null && passwordsToTry.length > 0)
            szfs.tryPasswords(passwordsToTry);

        return ret;
    }

}
