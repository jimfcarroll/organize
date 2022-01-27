package com.jiminger;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import com.jiminger.FileSpec.ByteBufferResource;

import net.dempsy.util.io.MegaByteBuffer;

public class MD5 {

    private static boolean avoidMemMap = false;

    public static void avoidMemMap() {
        avoidMemMap(true);
    }

    public static void avoidMemMap(final boolean amm) {
        avoidMemMap = amm;
    }

    public static byte[] hash(final FileSpec fSpec) throws IOException {
        if(avoidMemMap || !fSpec.supportsMemoryMap()) {
            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
            try(DigestInputStream dis = new DigestInputStream(fSpec.getStandardInputStream(), md);) {
                while(dis.read() >= 0);
            }
            return md.digest();
        }

        try(var bbr = fSpec.mapFile();) {
            return hash(bbr);
        }
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
