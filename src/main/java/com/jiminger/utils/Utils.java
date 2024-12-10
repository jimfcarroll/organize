package com.jiminger.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jiminger.records.FileRecord;

import org.apache.commons.lang3.tuple.Pair;

import net.dempsy.util.UriUtils;

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

    private static boolean isSymbol(final char c) {
        return !Character.isDigit(c) && !Character.isLetter(c) && c != '.';
    }

    // Custom comparator
    private static final Comparator<String> CUSTOM_PATH_STRING_COMPARATOR = (s1, s2) -> {
        final int len1 = s1.length();
        final int len2 = s2.length();
        final int minLen = Math.min(len1, len2);

        // Compare character by character
        for(int i = 0; i < minLen; i++) {
            final char c1 = s1.charAt(i);
            final char c2 = s2.charAt(i);

            // Special case for '.'
            if(c1 == '.' && c2 != '.') return -1;
            if(c2 == '.' && c1 != '.') return 1;

            // Symbols come before numbers
            if(isSymbol(c1) && !isSymbol(c2)) return -1;
            if(isSymbol(c2) && !isSymbol(c1)) return 1;

            // Default natural order for characters
            if(c1 != c2) return Character.compare(c1, c2);
        }

        // If one string is a prefix of the other, shorter string comes first
        return Integer.compare(len1, len2);
    };

    private static final Comparator<FileRecord> CUSTOM_FILERECORD_PATH_COMPARATOR = (f1, f2) -> {
        return CUSTOM_PATH_STRING_COMPARATOR.compare(f1.uri().toString(), f2.uri().toString());
    };

    public static Comparator<FileRecord> getGroupingCompliantFileRecordComparator() {
        return CUSTOM_FILERECORD_PATH_COMPARATOR;
    }

    /**
     * Requires the list of records to be sorted lexographically so that members of groups appear
     * in succession. It will group successive records whose full path with no file extension are
     * identical.
     */
    public static List<List<FileRecord>> groupByBaseFnameSimilarity(final List<FileRecord> records) {
        final List<List<FileRecord>> grouped = new ArrayList<>();
        String matchFname = null;
        List<FileRecord> curGroup = new ArrayList<>();
        for(final var cur: records) {
            final String curFilename = filenameNoExtention(cur.uri().toString());
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
        return grouped.stream()
            .filter(g -> g.size() >= 2)
            .collect(Collectors.toList());
    }

    public static record ImageMatch(double cc, FileRecord imageFr) {}

    public static Pair<FileRecord[], List<FileRecord>> separateMainImagesFromRemainder(final List<FileRecord> sortedX) {
        // prefer .cr2 and/or .dng as the primary images.
        if(sortedX.size() == 0)
            return null;
        if(sortedX.size() == 1)
            return Pair.of(new FileRecord[] {sortedX.get(0)}, Collections.emptyList());
        else {
            final FileRecord[] tmpFrs = sortedX.stream()
                .filter(f -> f.uri().toString().toLowerCase().endsWith(".cr2") || f.uri().toString().toLowerCase().endsWith(".dng"))
                .toArray(FileRecord[]::new);
            final FileRecord[] mainFrs = tmpFrs.length > 0 ? tmpFrs : new FileRecord[] {sortedX.get(0)};

            if(mainFrs.length == 0)
                return null;

            if((sortedX.size() - mainFrs.length) == 0)
                return Pair.of(mainFrs, Collections.emptyList());

            final Set<URI> mfrSet = Arrays.stream(mainFrs)
                .map(fr -> fr.uri())
                .collect(Collectors.toSet());

            final List<FileRecord> sorted = sortedX.stream()
                .filter(fr -> !mfrSet.contains(fr.uri()))
                .collect(Collectors.toList());

            return Pair.of(mainFrs, sorted);
        }
    }
}
