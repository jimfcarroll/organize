package com.jiminger;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.Tika;

import net.dempsy.util.UriUtils;
import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.util.io.MegaByteBufferInputStream;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class FileSpec {

    private static final Set<String> schemeSupportsMemMapping = new HashSet<>(Arrays.asList(
        "file"

    ));

    private final Path path;
    private final URI uri;
    private String mime = null;

    public static class ByteBufferResource implements Closeable {
        private final MegaByteBuffer mbb;
        private final RandomAccessFile raf;

        private ByteBufferResource(final File file, final long sizeToMap) throws IOException {
            raf = new RandomAccessFile(file, "r");
            final FileChannel channel = raf.getChannel();
            mbb = MegaByteBuffer.allocateMaped(0L, sizeToMap, channel, FileChannel.MapMode.READ_ONLY);
        }

        public MegaByteBuffer getBuffer() {
            return mbb;
        }

        @Override
        public void close() throws IOException {
            if(raf != null)
                raf.close();
        }
    }

    public FileSpec(final Path path) {
        this.path = path;
        this.uri = path.uri();
    }

    public URI uri() {
        return uri;
    }

    public boolean exists() throws IOException {
        return path.exists();
    }

    public boolean isDirectoryX() throws IOException {
        return path.isDirectory();
    }

    public boolean isRecursable() throws IOException {
        if(isDirectoryX())
            return true;

        final String lmime = mimeType();
        return(lmime != null && MimeUtils.recurseScheme(lmime) != null);
    }

    public long lastModifiedTime() throws IOException {
        return path.lastModifiedTime();
    }

    public long size() throws IOException {
        return path.length();
    }

    public boolean supportsMemoryMap() throws IOException {
        return schemeSupportsMemMapping.contains(uri.getScheme()) && !isDirectoryX();
    }

    public InputStream getStandardInputStream() throws IOException {
        return new BufferedInputStream(path.read());
    }

    public InputStream getEfficientInputStream() throws IOException {
        return getEfficientInputStream(path.length());
    }

    public InputStream getEfficientInputStream(final long numBytes) throws IOException {
        if(supportsMemoryMap()) {
            final ByteBufferResource resource = mapFile(numBytes);
            return new MegaByteBufferInputStream(resource.mbb) {

                @Override
                public void close() throws IOException {
                    resource.close();
                    super.close();
                }
            };
        } else
            return getStandardInputStream();
    }

    public ByteBufferResource mapFile() throws IOException {
        return mapFile(path.length());
    }

    public ByteBufferResource mapFile(final long bytesToMap) throws IOException {
        if(!supportsMemoryMap())
            throw new UnsupportedOperationException("The file system \"" + uri.getScheme() + "\" doesn't support memory mapping.");
        return new ByteBufferResource(toFile(), Math.min(path.length(), bytesToMap));
    }

    public File toFile() throws IOException {
        final File file;
        if("file".equals(uri.getScheme()))
            file = new File(uri.getPath());
        else
            file = path.toFile();
        return file;
    }

    public String mimeType() throws IOException {
        return mimeType(null);
    }

    public String mimeType(final String defaultValue) throws IOException {
        if(mime == null) {
            try(var is = getEfficientInputStream(4096);) {
                final Tika tika = new Tika();
                final String ret = tika.detect(is, UriUtils.getName(uri));
                mime = ret == null ? defaultValue : ret;
            }
        }
        return mime;
    }

    public void delete() throws IOException {
        path.delete();
    }

    public FileSpec[] list(final Vfs vfs) throws IOException {
        if(path.isDirectory())
            return Arrays.stream(path.list())
                .map(p -> new FileSpec(p))
                .toArray(FileSpec[]::new);
        else {
            final String recurseScheme = MimeUtils.recurseScheme(mimeType());
            if(recurseScheme == null)
                return new FileSpec[0];
            final URI newPath = uncheck(() -> UriUtils.prependScheme(recurseScheme, path.uri()));
            return new FileSpec[] {new FileSpec(vfs.toPath(newPath))};
        }
    }

    public FileSpec[] listSorted(final Vfs vfs) throws IOException {
        if(path.isDirectory())
            return Arrays.stream(path.list())
                .map(p -> new FileSpec(p))
                .sorted((o1, o2) -> o1.uri().toString().compareTo(o2.uri().toString()))
                .toArray(FileSpec[]::new);
        else {
            final String recurseScheme = MimeUtils.recurseScheme(mimeType());
            if(recurseScheme == null)
                return new FileSpec[0];
            final URI newPath = uncheck(() -> UriUtils.prependScheme(recurseScheme, path.uri()));
            return new FileSpec[] {new FileSpec(vfs.toPath(newPath))};
        }
    }

    public BasicFileAttributes getAttr() throws IOException {
        if(!supportsMemoryMap())
            return null;
        return Files.readAttributes(
            toFile().toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public String toString() {
        return uri.toString();
    }

}
