package com.jiminger.old;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import net.dempsy.util.BinaryUtils;
import net.dempsy.util.io.MegaByteBuffer;

public class RecoverMP4 {

    private static int bytesToInt(final byte[] b) {
        int result = 0;
        for(int i = 0; i < Integer.BYTES; i++) {
            result <<= Byte.SIZE;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    static final Set<String> possibleSubTypes = Set.of("avc1", "iso2", "isom", "mmp4", "mp41", "mp42", "mp71", "msnv", "ndas", "ndsc", "ndsh", "ndsm", "ndsp",
        "ndss", "ndxc", "ndxh", "ndxm", "ndxp", "ndxs");

    static final Set<String> possibleTypes = Set.of("ftyp", "mdat", "moov", "pnot", "udta", "uuid", "moof", "free", "skip", "jP2 ", "wide", "load", "ctab",
        "imap", "matt", "kmat", "clip", "crgn", "sync", "chap", "tmcd", "scpt", "ssrc", "PICT");

    static final long mask = (1024 * 1024 * 1024) - 1;

    public static void main(final String[] args) throws IOException {
        final File file = new File("/data/sdcard.img");

        final int pattern = bytesToInt("ftyp".getBytes(StandardCharsets.US_ASCII));

        try(final var raf = new RandomAccessFile(file, "r");) {
            final FileChannel channel = raf.getChannel();
            final MegaByteBuffer mbb = MegaByteBuffer.allocateMaped(0L, file.length(), channel, FileChannel.MapMode.READ_ONLY);

            int count = 0;
            final long len = file.length();
            for(long i = 0; i < len; i++) {
                if((i & mask) == 0)
                    System.out.print(".");
                if(pattern == mbb.getInt(i)) {
                    final byte[] subType = new byte[4];
                    mbb.getBytes(i + 4, subType);
                    final String subTypeStr = new String(subType, StandardCharsets.US_ASCII);
                    System.out.print(subTypeStr);
                    if(possibleSubTypes.contains(subTypeStr)) {
                        scan(mbb, len, i - 4L, count);
                    }
                    count++;
                }
            }

            System.out.println("Found " + count);
        }
    }

    public static boolean scan(final MegaByteBuffer mbb, final long totalLen, final long index, final int fileCount) throws IOException {
        long pos = index;
        final byte[] recordType = new byte[4];

        long end = -1;
        boolean foundMoov = false;

        while(true) {
            final long recordLen = BinaryUtils.longify(mbb.getInt(pos));
            mbb.getBytes(pos + 4L, recordType);
            final String recordTypeStr = new String(recordType, StandardCharsets.US_ASCII);
            if(!possibleTypes.contains(recordTypeStr)) {
                end = pos;
                break;
            }
            if("moov".equals(recordTypeStr))
                foundMoov = true;
            pos += recordLen;
            if(pos >= totalLen)
                return false;
        }

        if(end != -1) {
            {
                final File out = new File("/tmp/recovered-" + fileCount + ".mp4");
                final byte[] buf = new byte[1024 * 1024];

                pos = index;
                try(OutputStream os = new BufferedOutputStream(new FileOutputStream(out));) {
                    do {
                        final long numLeft = end - pos;
                        final int numToRead = numLeft > buf.length ? buf.length : (int)numLeft;
                        mbb.getBytes(pos, buf, 0, numToRead);
                        os.write(buf, 0, numToRead);
                        pos += numToRead;
                    } while(pos < end);
                }
            }

            if(foundMoov == false) {
                // let's just search for the moov atom
                for(long i = index; i < (totalLen - 4L); i++) {
                    mbb.getBytes(i, recordType);
                    final String recordTypeStr = new String(recordType, StandardCharsets.US_ASCII);
                    if("moov".equals(recordTypeStr)) {
                        System.out.println("FOUND MOOV");
                        final int moovRecLen = mbb.getInt(i - 4);
                        end = i - 4L + BinaryUtils.longify(moovRecLen);
                        foundMoov = true;
                        break;
                    } else if("ftyp".equals(recordTypeStr)) {
                        break;
                    }
                }

                if(foundMoov) {
                    final File out = new File("/tmp/recovered-moov-" + fileCount + ".mp4");
                    final byte[] buf = new byte[1024 * 1024];

                    pos = index;
                    try(OutputStream os = new BufferedOutputStream(new FileOutputStream(out));) {
                        do {
                            final long numLeft = end - pos;
                            final int numToRead = numLeft > buf.length ? buf.length : (int)numLeft;
                            mbb.getBytes(pos, buf, 0, numToRead);
                            os.write(buf, 0, numToRead);
                            pos += numToRead;
                        } while(pos < end);
                    }
                }
            }

            return true;
        }
        return false;
    }
}
