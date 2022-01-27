package com.jiminger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MimeUtils {
    private static final Map<String, String> mimeToScheme = Map.of(
        "application/gzip", "gz",
        "application/x-compress", "Z",
        "application/x-bzip2", "bz2",
        "application/x-gtar", "tar",
        "application/zip", "zip",
        "application/x-7z-compressed", "7z",
        "application/x-xz", "xz"

    );
    private static final Set<String> schemeIsCompressed = new HashSet<>(Arrays.asList(
        "gz",
        "Z",
        "xz",
        "7z",
        "b2z"

    ));

    private static final Set<String> schemeIsArchve = new HashSet<>(Arrays.asList(
        "file",
        "tar",
        "zip"

    ));

    public static boolean isArchive(final String mime) {
        return Optional.ofNullable(mimeToScheme.get(mime)).map(s -> schemeIsArchve.contains(s)).orElse(false);
    }

    public static boolean isCompressed(final String mime) {
        return Optional.ofNullable(mimeToScheme.get(mime)).map(s -> schemeIsCompressed.contains(s)).orElse(false);
    }

    public static String recurseScheme(final String mime) {
        return mimeToScheme.get(mime);
    }
}
