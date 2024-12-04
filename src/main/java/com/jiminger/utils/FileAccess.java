package com.jiminger.utils;

import static net.dempsy.util.Functional.chain;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.dempsy.util.QuietCloseable;
import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.util.io.MegaByteBufferInputStream;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.FileSpec.ByteBufferResource;

/**
 * This class is NOT thread safe
 */
public class FileAccess implements Closeable {
    private final FileSpec fSpec;
    private final boolean avoidMemMap;

    private ByteBufferResource bbr = null;
    private final List<InputStream> iss = new ArrayList<>();

    public FileAccess(final FileSpec fSpec, final boolean avoidMemMap) {
        this.fSpec = fSpec;
        this.avoidMemMap = avoidMemMap;
    }

    public String mimeType(final String defaultValue) throws IOException {
        return fSpec.mimeType(defaultValue);
    }

    public long size() throws IOException {
        return fSpec.size();
    }

    public long lastModifiedTime() throws IOException {
        return fSpec.lastModifiedTime();
    }

    public void setMime(final String mime) {
        fSpec.setMime(mime);
    }

    public URI uri() {
        return fSpec.uri();
    }

    public boolean canMemoryMap() throws IOException {
        return !avoidMemMap && fSpec.supportsMemoryMap();
    }

    public MegaByteBuffer mapFile() throws IOException {
        return getBbr().getBuffer();
    }

    public File toFile() throws IOException {
        return fSpec.toFile();
    }

    public InputStream getInputStream() throws IOException {
        if(!canMemoryMap())
            return add(fSpec.getStandardInputStream());
        return add(new MegaByteBufferInputStream(getBbr().getBuffer()));
    }

    public QuietCloseable preserveHeader(final int size) {
        return fSpec.preserveHeader(size);
    }

    @Override
    public void close() throws IOException {
        final List<Exception> failures = new ArrayList<>();

        // close the InputStreams first and in reverse order.
        for(int i = iss.size() - 1; i >= 0; i--) {
            final var cur = iss.get(i);
            try {
                cur.close();
            } catch(final IOException | RuntimeException ioe) {
                failures.add(ioe);
            }
        }

        if(bbr != null) {
            System.out.println("Closing " + fSpec.uri());
            try {
                bbr.close();
            } catch(final IOException | RuntimeException ioe) {
                failures.add(ioe);
            }
        }

        if(failures.size() > 0) {
            if(failures.size() == 1) {
                final var failure = failures.get(0);
                if(failures instanceof IOException)
                    throw(IOException)failure;
                else
                    throw(RuntimeException)failure;
            } else {
                throw chain(new IOException("Failed to close " + fSpec, failures.get(0)),
                    e -> failures.subList(1, failures.size()).forEach(e1 -> e.addSuppressed(e1)));
            }
        }
    }

    private InputStream add(final InputStream is) {
        return chain(is, i -> iss.add(i));
    }

    private ByteBufferResource getBbr() throws IOException {
        if(bbr == null) {
            System.out.println("Opening " + fSpec.uri());
            if(!canMemoryMap())
                throw new IllegalStateException(
                    "Cannot request memory mapping on " + fSpec + " because "
                        + (avoidMemMap ? " memory mapping is disabled in the configuration." : " it's not supported by that uri."));

            bbr = fSpec.mapFile();
        }
        return bbr;
    }
}
