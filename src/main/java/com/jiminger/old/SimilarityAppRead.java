package com.jiminger.old;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class SimilarityAppRead {
    // public static PrintWriter out = new PrintWriter(System.out);
    public static PrintWriter out = null;
    static {
        try {
            out = new PrintWriter(new BufferedOutputStream(new FileOutputStream("C:\\Users\\Jim\\Documents\\junk.out")), true);
        } catch(final IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static char[] lookup = new char[] {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

    public static class HexInputStream extends InputStream {
        final InputStream u;

        public HexInputStream(final InputStream u) {
            this.u = u;
        }

        @Override
        public int read() throws IOException {
            final int r = u.read();
            if(r >= 0) {
                // byte b = (byte) r;
                // System.out.print(Character.toString(lookup[(b >>> 4) & 0xf]) + Character.toString(lookup[b & 0xf]) + " ");
            }
            return r;
        }

        @Override
        public void close() throws IOException {
            u.close();
        }
    }

    public static class CString {
        String str;

        public CString(final byte[] b) {
            int nullTerm = -1;
            for(int i = 0; i < b.length; i++)
                if(b[i] == 0) {
                    nullTerm = i;
                    break;
                }
            if(nullTerm == -1)
                str = new String(b);
            else {
                final byte[] s = new byte[nullTerm];
                System.arraycopy(b, 0, s, 0, nullTerm);
                str = new String(s);
            }
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public static class SString {
        public final String str;

        public SString(final DataInputStream dis) throws IOException {
            final short len = Short.reverseBytes(dis.readShort());
            final byte[] b = new byte[len];
            dis.read(b);
            str = new String(b);
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public static String hex(final byte[] buf) {
        byte b = buf[0];
        final StringBuilder ret = new StringBuilder(Character.toString(lookup[(b >>> 4) & 0xf])).append(Character.toString(lookup[b & 0xf]));

        for(int i = 1; i < buf.length; i++) {
            b = buf[i];
            ret.append(" ").append(Character.toString(lookup[(b >>> 4) & 0xf])).append(Character.toString(lookup[b & 0xf]));
        }
        return ret.toString();
    }

    public static class AudioEntry {
        public final String fileName;
        public final byte[] buf1;
        public final int size;
        public final byte[] buf2;

        public AudioEntry(final DataInputStream dis) throws IOException {
            fileName = new SString(dis).str;
            buf1 = new byte[6];
            dis.read(buf1);
            size = Integer.reverseBytes(dis.readInt());
            buf2 = new byte[15];
            dis.read(buf2);
        }

        @Override
        public String toString() {
            return "AudioEntry(" + fileName + ", " + hex(buf1) + ", size=" + size + ", " + hex(buf2) + ")";
        }
    }

    public static class AudioPairEntry {
        public final String fileName1;
        public final String fileName2;
        public final byte[] buf1;

        public AudioPairEntry(final DataInputStream dis) throws IOException {
            fileName1 = new SString(dis).str;
            fileName2 = new SString(dis).str;
            buf1 = read(dis, 12);
        }

        @Override
        public String toString() {
            return "PairEntry(" + fileName1 + ", " + fileName2 + ", " + hex(buf1) + ")";
        }
    }

    public static byte[] read(final DataInputStream dis, final int len) throws IOException {
        final byte[] ret = new byte[len];
        dis.read(ret);
        return ret;
    }

    public static String[] imageType = {null,"bmp",null,null,"jpg","png","gif","tif"};

    public static String imageType(final byte type) {
        if(type >= imageType.length)
            throw new RuntimeException("Unknown image type " + type);
        final String ret = imageType[type];
        if(ret == null)
            throw new RuntimeException("Unknown image type " + type);
        return ret;
    }

    public static class ImageEntry {
        public final String fileName;
        public final int size;
        public final byte[] buf1;
        public final int width;
        public final int height;
        public final String imageType;
        public final short depth;

        public ImageEntry(final DataInputStream dis) throws IOException {
            fileName = new SString(dis).str;
            size = Integer.reverseBytes(dis.readInt());
            buf1 = read(dis, 4);
            if(buf1[0] != 0 || buf1[1] != 0 || buf1[2] != 0 || buf1[3] != 0)
                throw new RuntimeException();
            width = Integer.reverseBytes(dis.readInt());
            height = Integer.reverseBytes(dis.readInt());
            imageType = imageType(dis.readByte());
            depth = Short.reverseBytes(dis.readShort());
        }

        @Override
        public String toString() {
            return "ImageEntry(" + fileName + ", " + ", size=" + size + ", " + hex(buf1) + ", " + width + " X " + height + ", " + imageType + ", depth=" + depth
                + ")";
        }
    }

    public static class ImagePairEntry {
        public final String fileName1;
        public final String fileName2;
        public final byte[] buf1;

        public ImagePairEntry(final DataInputStream dis) throws IOException {
            fileName1 = new SString(dis).str;
            fileName2 = new SString(dis).str;
            buf1 = read(dis, 8);
        }

        @Override
        public String toString() {
            return "PairEntry(" + fileName1 + ", " + fileName2 + ", " + hex(buf1) + ")";
        }
    }

    public static void main(final String[] args) throws Exception {

        // String file = "C:\\Users\\Jim\\Documents\\Results.similarity";
        final String file = "C:\\Users\\Jim\\Documents\\OneFolder3.similarity";

        final InputStream is = new HexInputStream(new BufferedInputStream(new FileInputStream(file)));

        final DataInputStream dis = new DataInputStream(is);

        final byte[] header = new byte[32];
        dis.read(header);

        out.println();
        out.println(new CString(header));
        out.println();

        boolean done = false;
        while(!done) {
            final int section = dis.readByte();
            switch(section) {
                case 1:
                    out.println(new AudioEntry(dis));
                    break;
                case 2:
                    out.println(new AudioPairEntry(dis));
                    break;
                case 3:
                    out.println(new ImageEntry(dis));
                    break;
                case 12:
                    out.println(new ImagePairEntry(dis));
                    break;
                default:
                    out.println("unknown type:" + section);
                    done = true;
                    break;
            }
        }

    }

}
