package com.jiminger.utils;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import net.dempsy.util.io.MegaByteBuffer;
import net.dempsy.vfs.FileSpec.ByteBufferResource;

public class MD5 {

    public static byte[] hash(final FileAccess fSpec) throws IOException {
        if(!fSpec.canMemoryMap()) {
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            try(DigestInputStream dis = new DigestInputStream(fSpec.getInputStream(), md);) {
                while(dis.read() >= 0);
            }
            return md.digest();
        }

        return hash(fSpec.mapFile());
    }

    public static byte[] hash(final ByteBufferResource bbr) throws IOException {
        return hash(bbr.getBuffer());
    }

    public static byte[] hash(final MegaByteBuffer mbb) throws IOException {
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        mbb.streamOfByteBuffers()
            .forEach(bb -> md.update(bb));
        return md.digest();
    }
}
