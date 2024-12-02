package com.jiminger.utils;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.io.FileUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Utils;

public class ImageUtils {
    static {
        CvMat.initOpenCv();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);
    private static final String LINE_SEPARATOR = System.lineSeparator();

    public static final int DONT_RESIZE = Integer.MAX_VALUE;

//    public static final String PPM_MIME_TYPE = "image/x-portable-pixmap";

    private static final Set<String> decodableImageMimes = new HashSet<String>(Set.of(
        "image/bmp", "image/cgm", "image/emf", "image/gif", "image/icns", "image/jp2", "image/jpeg", "image/png", "image/svg+xml", "image/tiff",
        "image/vnd.djvu", "image/vnd.fst", "image/vnd.microsoft.icon", "image/vnd.ms-modi", "image/webp", "image/wmf", "image/x-jp2-codestream",
        "image/x-portable-bitmap", "image/x-portable-graymap", "image/x-portable-pixmap", "image/x-raw-canon", "image/x-raw-panasonic", "image/x-tga",
        "image/x-xbitmap", "image/x-xcf"));

    public static CvMat ppmDecode(final byte[] ppm) {
        try(final CvMat mat = new CvMat(1, ppm.length, CvType.CV_8SC1);) {
            mat.bulkAccess(bb -> {
                bb.rewind();
                bb.put(ppm);
                bb.rewind();
            });

            try(final CvMat ret = CvMat.move(Imgcodecs.imdecode(mat, Imgcodecs.IMREAD_UNCHANGED));) {
                return ret.returnMe();
            }
        }
    }

    public static byte[] ppmEncode(final Mat mat, final boolean rgb) {
        if(mat.channels() != 3 || mat.depth() != CvType.CV_8U)
            throw new IllegalArgumentException(
                "Can only encode a 3 channel, 8 unsigned bits/channel, image to PPM. You passed a " + CvType.typeToString(mat.type()));

        final String header = "P6" + LINE_SEPARATOR + Integer.toString(mat.cols()) + " " + Integer.toString(mat.rows()) + LINE_SEPARATOR + "255"
            + LINE_SEPARATOR;

        final byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        final int headerLen = headerBytes.length;
        final int imageDataLen = (mat.rows() * mat.cols() * 3);
        final int len = headerBytes.length + imageDataLen;
        final byte[] rawBytes = new byte[len];
        System.arraycopy(headerBytes, 0, rawBytes, 0, headerLen);

        try(final CvMat cvmat = rgb ? CvMat.shallowCopy(mat) : new CvMat();) {
            if(!rgb)
                Imgproc.cvtColor(mat, cvmat, Imgproc.COLOR_RGB2BGR);

            cvmat.bulkAccess(bb -> bb.get(rawBytes, headerLen, imageDataLen));
        }
        return rawBytes;
    }

    public static int orientation(final Metadata md) {
        final Directory directory = md.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if(directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
            final int orientation = uncheck(() -> directory.getInt(ExifIFD0Directory.TAG_ORIENTATION));
            return orientation;
        }
        return -1;
    }

    public static CvMat loadImage(final FileAccess fSpec, final int maxDim) throws IOException {
        boolean runOther = false;
        if(fSpec.canMemoryMap()) {
            try(var c = new Closer();
                CvMat bytes = new CvMat(1, (int)fSpec.size(), CvType.CV_8UC1, fSpec.mapFile().streamOfByteBuffers().findFirst().orElseThrow());
                CvMat img = resizeIfTooLarge(c.addMat(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED)), maxDim, maxDim);) {
                if(img == null || img.dataAddr() == 0)
                    runOther = true;
                else
                    return img.returnMe();
            }
        } else
            runOther = true;

        if(runOther) {
            try(var c = new Closer();
                CvMat img = resizeIfTooLarge(c.addMat(ImageFile.readMatFromFile(fSpec.toFile().getAbsolutePath())), maxDim, maxDim);) {
                if(img == null || img.dataAddr() == 0)
                    return null;
                else
                    return img.returnMe();
            }
        }
        return null;
    }

    public static CvMat loadImageWithCorrectOrientation(final FileAccess fSpec, final int maxDim) throws IOException, ImageProcessingException {
        try(var img = loadImage(fSpec, maxDim);) {
            if(img == null)
                return null;
            try {
                final Metadata matMd = ImageMetadataReader.readMetadata(fSpec.getInputStream(), fSpec.size());
                fixOrientation(img, fSpec.mimeType("UNKNOWN"), matMd);
            } catch(final ImageProcessingException ipe) {
                System.out.println("WARNING: Couldn't read metadata so cannot correct orientation for " + fSpec.uri());
            }
            return img.returnMe();
        }
    }

    public static CvMat loadImageWithCorrectOrientationAndNormalizedChannels(final FileAccess cur, final Vfs vfs, final int maxDim, final double alpha,
        final double beta) throws IOException, ImageProcessingException {
        return Sci.normalizeChannels(loadImageWithCorrectOrientation(cur, maxDim), alpha, beta);
    }

    public static void fixOrientation(final Mat image, final String mime, final Metadata md) {
        if(mime.endsWith("/tiff")) // CR2s seem to already be correct.
            return;
        final Directory directory = md.getFirstDirectoryOfType(ExifIFD0Directory.class);

        if(directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
            final int orientation = uncheck(() -> directory.getInt(ExifIFD0Directory.TAG_ORIENTATION));

            // Apply the appropriate transformation based on the orientation value
            switch(orientation) {
                case 1:
                    break; // No rotation needed
                case 2:
                    Core.flip(image, image, 1); // Flip horizontally
                    break;
                case 3:
                    Core.rotate(image, image, Core.ROTATE_180); // Rotate 180°
                    break;
                case 4:
                    Core.flip(image, image, 0); // Flip vertically
                    break;
                case 5:
                    Core.transpose(image, image);
                    Core.flip(image, image, 1); // Transpose and flip horizontally
                    break;
                case 6:
                    Core.rotate(image, image, Core.ROTATE_90_CLOCKWISE); // Rotate 90° clockwise
                    break;
                case 7:
                    Core.transpose(image, image);
                    Core.flip(image, image, 0); // Transpose and flip vertically
                    break;
                case 8:
                    Core.rotate(image, image, Core.ROTATE_90_COUNTERCLOCKWISE); // Rotate 90° counter-clockwise
                    break;
            }
        }

    }

    // public static byte[] convertMatToJpegByteArray(final Mat mat, final boolean isRgb) {
    // try(Closer closer = new Closer();
    // CvMat toEncode = isRgb ? new CvMat() : CvMat.shallowCopy(mat);) {
    // if(isRgb)
    // Imgproc.cvtColor(mat, toEncode, Imgproc.COLOR_RGB2BGR);
    // final MatOfByte mobOut = closer.addMat(new MatOfByte());
    // Imgcodecs.imencode(".jpg", toEncode, mobOut);
    // return mobOut.toArray();
    // }
    // }
    //
    // public static CvMat convertJpegByteArrayToMat(final byte[] img) {
    // try(Closer closer = new Closer();) {
    // final MatOfByte mobOut = closer.addMat(new MatOfByte(img));
    // try(CvMat cvmat = CvMat.move(Imgcodecs.imdecode(mobOut, Imgcodecs.IMREAD_UNCHANGED));) {
    // return cvmat.returnMe();
    // }
    // }
    // }

    /**
     * Given a single {@link Mat}, if it's too large, resize it to the correct size. Either way, return a new, usable {@link CvMat}. Each image cannot be
     * larger than {@param maxRows} by {@param maxCols}.
     *
     * @return a new CvMat. Warning: whatever calls this is responsible for closing it.
     */
    public static CvMat resizeIfTooLarge(final Mat mat, final Size limit) {
        return resizeIfTooLarge(mat, (int)limit.height, (int)limit.width);
    }

    /**
     * Given a single {@link Mat}, if it's too large, resize it to the correct size. Either way, return a new, usable {@link CvMat}. Each image cannot be
     * larger than {@param maxRows} by {@param maxCols}.
     *
     * @return a new CvMat. Warning: whatever calls this is responsible for closing it.
     */
    public static CvMat resizeIfTooLarge(final Mat mat, final int maxRows, final int maxCols) {
        // It's likely that we're going to be getting images that are wider than they are longer
        if(mat == null)
            return null;
        if(mat.cols() > maxCols || mat.rows() > maxRows) {
            final Size maxSize = new Size(maxCols, maxRows);
            final Size newSize = Utils.scaleDownOrNothing(mat, maxSize);
            LOGGER.trace("Image too large. Current size: {}, max size: {}, new size: {}", mat.size(), maxSize, newSize);
            try(final CvMat newImage = new CvMat()) {
                Imgproc.resize(mat, newImage, new Size((int)Math.floor(newSize.width), (int)Math.floor(newSize.height)), 0, 0, Imgproc.INTER_AREA);
                return newImage.returnMe();
            }
        }
        return CvMat.shallowCopy(mat);
    }

    public static double calculateScaleFactorToFitInGivenBytes(final Size originalSize, final int type, final long maxBytes) {
        final long elemSize = CvType.ELEM_SIZE(type);
        final double maxPix = Math.floorDiv(maxBytes, elemSize);
        final double ar = originalSize.width / originalSize.height;
        final double newR = Math.floor(Math.sqrt(maxPix / ar));
        return newR / originalSize.height;
    }

    /**
     * This will only resize of the scale is less than 1 and greater than 0.
     * A scale of greater than 1 means it's not going to shrink it so it already
     * not too large. A scale < 0 is invalid.
     */
    public static CvMat resizeIfTooLarge(final Mat mat, final double scale) {
        if(mat == null)
            return null;
        if(scale < 1 && scale > 0) {
            final Size newSize = new Size(mat.width() * scale, mat.height() * scale);
            try(final CvMat newImage = new CvMat()) {
                Imgproc.resize(mat, newImage, new Size((int)Math.floor(newSize.width), (int)Math.floor(newSize.height)), 0, 0, Imgproc.INTER_AREA);
                return newImage.returnMe();
            }
        }
        return CvMat.shallowCopy(mat);
    }

    public static void copyMetadata(final File srcImage, final File destImage) throws ImageReadException, IOException, ImageWriteException {
        // Copy image metadata
        final ImageMetadata metadata = Imaging.getMetadata(srcImage);
        if(metadata instanceof JpegImageMetadata) {
            final JpegImageMetadata jpegMetadata = (JpegImageMetadata)metadata;
            final TiffImageMetadata exif = jpegMetadata.getExif();
            if(exif != null) {
                final TiffOutputSet outputSet = exif.getOutputSet();
                if(outputSet != null) {
                    final byte[] jpeg = FileUtils.readFileToByteArray(destImage);
                    try(OutputStream os = new BufferedOutputStream(new FileOutputStream(destImage));) {
                        new ExifRewriter().updateExifMetadataLossless(jpeg, os, outputSet);
                    }
                }
            }
        }

        // Copy file system attributes (creation, modification, and access times)
        final Path srcPath = srcImage.toPath();
        final Path destPath = destImage.toPath();
        final BasicFileAttributeView destAttrView = Files.getFileAttributeView(destPath, BasicFileAttributeView.class);
        final FileTime creationTime = (FileTime)Files.getAttribute(srcPath, "creationTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
        final FileTime lastModifiedTime = (FileTime)Files.getAttribute(srcPath, "lastModifiedTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
        final FileTime lastAccessTime = (FileTime)Files.getAttribute(srcPath, "lastAccessTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
        destAttrView.setTimes(lastModifiedTime, lastAccessTime, creationTime);
    }

    public static boolean isDecodableImage(final FileAccess fs) {
        return decodableImageMimes.contains(uncheck(() -> fs.mimeType("UNKNOWN"))) && uncheck(() -> fs.size()) != 0;
    }

}
