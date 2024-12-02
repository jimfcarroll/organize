package com.jiminger;

import static com.jiminger.VfsConfig.createVfs;
import static com.jiminger.records.FileRecord.readFileRecords;
import static com.jiminger.utils.ImageUtils.DONT_RESIZE;
import static com.jiminger.utils.ImageUtils.loadImageWithCorrectOrientation;
import static com.jiminger.utils.Utils.groupByBaseFnameSimilarity;
import static com.jiminger.utils.Utils.isParentUri;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.drew.imaging.ImageProcessingException;
import com.jiminger.records.FileRecord;
import com.jiminger.records.ImageDetails;
import com.jiminger.utils.ImageUtils;
import com.jiminger.utils.Sci;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;

import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;

public class SlideshowGenerator {

    public static void usage() {
        System.err
            .println(
                "Usage: java -cp [classpath] " + SlideshowGenerator.class.getName() + " path/to/config.json uri/to/src /path/to/dest");
        System.exit(1);
    }

    static public void main(final String[] args) throws Exception {
        if(args == null || args.length != 3)
            usage();

        final Config conf = Config.load(args[0]);
        final var vfs = createVfs(conf.passwordsToTry);
        final boolean avoidMemMap = conf.avoidMemMap;

        final URI srcUri = new URI(args[1]);

        final String dstDirStr = args[2].endsWith("/") ? args[2] : (args[2] + "/");

        final File dstDir = new File(dstDirStr);
        if(dstDir.exists()) {
            System.err.println("The destination directory \"" + dstDirStr + "\" already exists.");
        } else {

            // test that I can make the directory.
            if(!dstDir.mkdirs()) {
                System.err.println("The destination directory \"" + dstDirStr + "\" cannot be created.");
                System.exit(1);
            }

            // cleanup in case we fail before we write the directory
            dstDir.delete();
        }

        final Map<URI, FileRecord> file2FileRecords =

            readFileRecords(
                Stream.concat(Stream.of(conf.md5FileToWrite), Arrays.stream(Optional.ofNullable(conf.md5FilesToRead).orElse(new String[0])))
                    .toArray(String[]::new)

            ).stream()
                .collect(Collectors.toMap(fs -> fs.uri(), fs -> fs, (fr1, fr2) -> {
                    if(fr1.equals(fr2))
                        return fr1;
                    throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
                }));

        // get all image records.
        final List<FileRecord> records = new LinkedList<>(file2FileRecords.values().stream()
            .filter(fr -> isParentUri(srcUri, fr.uri()))
            .filter(fr -> fr.mime() != null)
            .filter(fr -> fr.mime().startsWith("image/"))
            .sorted((fr1, fr2) -> fr1.uri().compareTo(fr2.uri()))
            .collect(Collectors.toList()));

        System.out.println("Total number of images: " + records.size());

        final List<List<FileRecord>> grouped = groupByBaseFnameSimilarity(records);// new ArrayList<>();

//        final ImageDisplay id = new ImageDisplay.Builder()
//            .windowName("Rotated")
//            // .dim(new Size(640, 480))
//            .build();
//        final ImageDisplay preRotId = new ImageDisplay.Builder()
//            .windowName("Pre Rot")
//            // .dim(new Size(640, 480))
//            .build();

        for(final var group: grouped) {
            if(group.size() == 0)
                continue;

            if(group.size() < 2) {
                copyForSlideshow(group.get(0), srcUri, dstDir, vfs, avoidMemMap);
                continue;
            }

            // if there's a .CR2 (we'll use the mime type to check) and there's more than 2, we need to check.
            final long largestSize = group.stream().mapToLong(f -> f.size()).max().orElse(0);
            final var sorted = new ArrayList<>(group.stream()
                .sorted((fr1, fr2) -> Long.compare(modifiedSize(fr2, largestSize), modifiedSize(fr1, largestSize)))
                .collect(Collectors.toList()));

            final FileRecord[] toCopy = selectImagesFromGroup(sorted, 0.99, vfs, avoidMemMap);
            if(toCopy != null && toCopy.length > 0) {
                Arrays.stream(toCopy)
                    .forEach(fr -> uncheck(() -> copyForSlideshow(fr, srcUri, dstDir, vfs, avoidMemMap)));
            }
        }
    }

    private static double[][] correlationMatric(final List<FileRecord> src, final Vfs vfs, final boolean avoidMemMap) {
        try(final Closer closer = new Closer();) {
            final CvMat[] mats = src.stream()
                .map(fr -> uncheck(() -> closer.add(fr.fileAccess(vfs, avoidMemMap))))
                .map(fr -> closer.add(uncheck(() -> loadImageWithCorrectOrientation(fr, DONT_RESIZE))))
                .toArray(CvMat[]::new);

            final double[][] ret = new double[src.size()][src.size()];
            for(int i = 0; i < src.size(); i++) {
                for(int j = i + 1; j < src.size(); j++) {
                    final CvMat m1 = mats[i];
                    final CvMat m2 = mats[j];
                    if(m1 == null || m2 == null)
                        ret[i][j] = 0.0;
                    else
                        ret[i][j] = Sci.correlationCoef(m1, m2, true, ImageDetails.NORM_MIN_MAX_LOW, ImageDetails.NORM_MIN_MAX_HIGH);
                }
            }
            // fill in the rest
            for(int i = 0; i < src.size(); i++)
                ret[i][i] = 1.0;
            for(int j = 0; j < src.size(); j++) {
                for(int i = j + 1; i < src.size(); i++) {
                    ret[i][j] = ret[j][i];
                }
            }
            return ret;
        }
    }

    private static FileRecord[] selectImagesFromGroup(final List<FileRecord> selectFrom, final double thresh, final Vfs vfs, final boolean avoidMemMap) {
        final double[][] corrMat = correlationMatric(selectFrom, vfs, avoidMemMap);
        // anywhere there's a high enough corellation between 2 or more images, they will be considered the same image.
        @SuppressWarnings("unchecked")
        final Set<Integer>[] comb = new Set[selectFrom.size()];
        for(int i = 0; i < selectFrom.size(); i++) {
            comb[i] = new HashSet<Integer>(selectFrom.size());
            for(int j = i + 1; j < selectFrom.size(); j++) {
                if(corrMat[i][j] > thresh)
                    comb[i].add(j);
            }
        }
        // now combine rows
        for(int i = 0; i < comb.length; i++) {
            for(final int match: comb[i]) {
                comb[i].addAll(comb[match]);
                comb[match] = new HashSet<>();
            }
        }
        for(int i = 0; i < comb.length; i++)
            comb[i].add(i);

        return Arrays.stream(comb)
            .filter(s -> s.size() == 0)
            .map(s -> selectImageFromSameImage(s, selectFrom))
            .filter(fr -> fr != null)
            .toArray(FileRecord[]::new);
    }

    private static FileRecord selectImageFromSameImage(final Set<Integer> sameImage, final List<FileRecord> indexed) {
        if(sameImage.size() == 0)
            return null;
        if(sameImage.size() == 1)
//            return null;
            return indexed.get(sameImage.iterator().next());

        final List<FileRecord> toChooseFrom = sameImage.stream().map(i -> indexed.get(i)).collect(Collectors.toList());
        // if there's a dng, we're taking it
        final FileRecord dng = toChooseFrom.stream()
            .filter(fr -> fr.uri().toString().toLowerCase().endsWith(".dng"))
            .findFirst()
            .orElse(null);
        if(dng != null)
            return dng;
        // if there's a JPG assoiciated with a CR2, we're taking that.
        final FileRecord cr2 = toChooseFrom.stream()
            .filter(fr -> fr.uri().toString().toLowerCase().endsWith(".cr2"))
            .findFirst()
            .orElse(null);

        if(cr2 != null) {
            // see if there's a jpg of the same name.
            final int extIndex = cr2.uri().toString().toLowerCase().lastIndexOf(".cr2");
            final String fnameToLookForLc = cr2.uri().toString().toLowerCase().substring(0, extIndex) + ".jpg";
            final FileRecord cr2Jpg = toChooseFrom.stream()
                .filter(fr -> fr.uri().toString().toLowerCase().equals(fnameToLookForLc))
                .findFirst()
                .orElse(null);

            if(cr2Jpg != null)
                return cr2Jpg;
        }

        // just take the biggest.
        return indexed.stream()
            .max((fr1, fr2) -> Long.compare(fr1.size(), fr2.size()))
            .orElseThrow();
    }

    private static long modifiedSize(final FileRecord fr, final long largest) {
        return (fr.uri().toString().contains("_shotwell") || fr.uri().toString().contains("_embedded")) ? (fr.size() - largest) : fr.size();
    }

    private static File replaceExtention(final File file) {
        final String str = file.getAbsolutePath();
        final int index = str.lastIndexOf('.');
        if(index < 0)
            return new File(str + ".JPG");
        return new File(str.substring(0, index) + ".JPG");
    }

    private static void copyForSlideshow(final FileRecord toCopy, final URI srcDir, final File destDirX, final Vfs vfs, final boolean avoidMemMap)
        throws ImageProcessingException, IOException, URISyntaxException, ImageReadException, ImageWriteException {

        final URI toCopyUri = toCopy.uri();
        if(!isParentUri(srcDir, toCopyUri))
            throw new IllegalArgumentException("The directory URI, " + srcDir + " is not a parent of the file to copy: " + toCopy);

        final var srcRelative = srcDir.relativize(toCopy.uri());
        final String destDirStr = destDirX.getAbsolutePath();
        final URI destDirUri = new URI("file:" + (destDirStr.endsWith("/") ? destDirStr : (destDirStr + "/")));
        final URI dest = destDirUri.resolve(srcRelative);
        // validate.
        if(!isParentUri(destDirUri, dest))
            throw new IllegalArgumentException("The destination directory, " + destDirUri + " somehow is not a parent of the file dest: " + dest);

        // make sure the dest file doesn't exist already.
        final File destFile = replaceExtention(vfs.toFile(dest));
        if(destFile.exists()) {
            System.out.println("The destination file, " + destFile.getAbsolutePath() + " already exists.");
            return;
        }

        try(CvMat mat = loadImageWithCorrectOrientation(toCopy.fileAccess(vfs, avoidMemMap), DONT_RESIZE);) {
            if(mat == null) {
                System.err.println("Failed to read image at " + toCopy.uri());
                return;
            }
            if(toCopy.mime().contains("image/jpeg") && mat.rows() <= 1080 && mat.cols() <= 1920) {
                // we can just move the file.
                final java.nio.file.Path sourcePath = Paths.get(toCopy.uri());
                System.out.println("Copying " + toCopy.uri() + " to " + dest);
                destFile.getParentFile().mkdirs();
                uncheck(() -> Files.copy(sourcePath, destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES));
            } else {
                try(CvMat resized = ImageUtils.resizeIfTooLarge(mat, 1080, 1920);) {
                    System.out.println("Copying resized image from " + toCopy.uri() + " to " + destFile);
                    destFile.getParentFile().mkdirs();
                    ImageFile.writeImageFile(resized, destFile.getAbsolutePath());
                    ImageUtils.copyMetadata(vfs.toFile(toCopyUri), destFile);
                }
            }
        }
    }
}
