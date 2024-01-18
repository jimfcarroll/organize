package com.jiminger;

import static com.jiminger.ImageUtils.loadImageWithCorrectOrientation;
import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.drew.imaging.ImageProcessingException;

import net.dempsy.util.UriUtils;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;

public class Utils {

    public static boolean isParentUri(final URI parentURI, final URI childURI) {
        try {
            if(parentURI.getScheme().equals(childURI.getScheme())) {
                URI cur = childURI;
                final URI parentUriNoSlash = parentURI.getPath().endsWith("/") ? getParent(parentURI.resolve("ZX")) : parentURI;
                for(boolean done = false; !done;) {
                    cur = getParent(cur);
                    if(parentUriNoSlash.equals(cur))
                        return true;
                    if(cur == null)
                        done = true;
                }
            }

            return false;
        } catch(final Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static URI getParent(final URI uri) {
        final String pathPart = uri.getPath();
        if(pathPart == null)
            return null;
        final String newpath = UriUtils.getParent(pathPart);
        if(newpath == null) // we're at the root
            return null;
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), newpath, uri.getQuery(), uri.getFragment());
        } catch(final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String filenameNoExtention(final String path) {
        final String fnameWithExtention = path;

        final int dotIndex = fnameWithExtention.lastIndexOf(".");
        if(dotIndex < 0)
            return fnameWithExtention;

        return fnameWithExtention.substring(0, dotIndex);
    }

    public static List<List<FileRecord>> groupByBaseFnameSimilarity(final List<FileRecord> records) {
        final List<List<FileRecord>> grouped = new ArrayList<>();
        String matchFname = null;
        List<FileRecord> curGroup = new ArrayList<>();
        for(final var cur: records) {
            final String curFilename = filenameNoExtention(cur.path());
            if(matchFname == null) {
                // ==================
                matchFname = curFilename;
                if(curGroup.size() > 0)
                    grouped.add(curGroup);
                curGroup = new ArrayList<>();
                curGroup.add(cur);
                // ==================
                continue;
            }
            if(curFilename.startsWith(matchFname)) {
                curGroup.add(cur);
            } else {
                if(curGroup.size() > 0)
                    grouped.add(curGroup);
                // ==================
                matchFname = curFilename;
                curGroup = new ArrayList<>();
                curGroup.add(cur);
                // ==================
            }
        }
        return grouped;
    }

    public static record ImageMatch(double cc, FileRecord imageFr) {}

    public static List<ImageMatch> matches(final List<FileRecord> sorted, final Vfs vfs)
        throws ImageProcessingException, IOException, URISyntaxException {
        final FileRecord[] tmpFrs = sorted.stream()
            .filter(f -> f.path().toLowerCase().endsWith(".cr2") || f.path().toLowerCase().endsWith(".dng"))
            .toArray(FileRecord[]::new);
        final FileRecord[] mainFrs = tmpFrs.length > 0 ? tmpFrs : new FileRecord[] {sorted.get(0)};

        final List<ImageMatch> matches = new ArrayList<>();
        try(Closer closer = new Closer();) {
            final CvMat[] mains = Arrays.stream(mainFrs)
                .map(f -> closer.add(uncheck(() -> loadImageWithCorrectOrientation(f, vfs))))
                .filter(m -> m != null)
                .toArray(CvMat[]::new);

            if(mains.length == 0)
                return matches;

            for(int i = 1; i < sorted.size(); i++) {
                final FileRecord cur = sorted.get(i);
                try(final CvMat mat = closer.add(loadImageWithCorrectOrientation(cur, vfs));) {
                    if(mat == null)
                        continue;

                    final double cc = ImageUtils.correlationCoef(mains, mat);
                    matches.add(new ImageMatch(cc, cur));
                }
            }
        }
        return matches;
    }

}
