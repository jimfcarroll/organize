package com.jiminger;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;

public class MD5 {

    public static byte[] hash(final File file) throws IOException {
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        try(DigestInputStream dis = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), md);) {
            while(dis.read() >= 0);
        }
        return md.digest();
    }
}
