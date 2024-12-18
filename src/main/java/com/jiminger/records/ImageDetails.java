package com.jiminger.records;

import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.utils.FileAccess;
import com.jiminger.utils.ImageUtils;
import com.jiminger.utils.ImageUtils.Dims;
import com.jiminger.utils.Sci;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.Vfs;

public record ImageDetails(int rows, int cols, int channels, float[] histogram) {

    public static void main(final String[] args) throws Exception {
        try(Vfs vfs = new Vfs();
            final FileAccess fa = new FileAccess(new FileSpec(vfs.toPath(URI.create("file:/share/Media/GingerCameraBackup/DCIM/100CANON/IMG_0252.CR2"))),
                false);) {
            final var id = checkImageDetails(fa, null, null, null, null);
            System.out.println(id);
        }
    }

    public static final String KEY = "image_details";
    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();
    public static double NORM_MIN_MAX_LOW = 0.0;
    // I think this should be 256.0 rather than 255 like most of the examples. It's a post and rail problem
    // and I'm binning to 256 anyway so it should provide a better distribution. Otherwise it seems like
    // the highest bin will be under utilized.
    public static double NORM_MIN_MAX_HIGH = 256.0;
    public static int HIST_NUM_BINS = 256;

    @Override
    public String toString() {
        return "ImageDetails [rows=" + rows + ", cols=" + cols + ", channels=" + channels + ", histogram=" + Arrays.toString(histogram) + "]";
    }

    public static boolean hasHistogram(final FileRecord fr) {
        final var cur = getFrom(fr);

        return cur != null && cur.histogram != null && !allZero(cur.histogram);
    }

    private static boolean allZero(final float[] h) {
        for(int i = 0; i < h.length; i++)
            if(h[i] != 0.0f)
                return false;
        return true;
    }

    public static ImageDetails getFrom(final FileRecord fr) {
        if(fr == null)
            return null;

        final var additional = fr.additional();
        if(additional == null)
            return null;

        final var entry = additional.get(KEY);
        if(entry == null)
            return null;
        if(entry instanceof Map)
            return om.convertValue(entry, ImageDetails.class);
        if(entry instanceof ImageDetails)
            return (ImageDetails)entry;

        throw new IllegalStateException("Expected a " + ImageDetails.class.getSimpleName() + " but got a " + entry.getClass().getName()
            + " in the additional details for the file record: " +
            uncheck(() -> om.writeValueAsString(fr)));
    }

    public static ImageDetails calculate(final Dims dims, final Mat img, final boolean canDisposeOfImage) {
        return new ImageDetails(dims.rows(), dims.cols(), CvType.channels(dims.type()),
            Sci.computeNormalizedHistogram(img, HIST_NUM_BINS, NORM_MIN_MAX_LOW, NORM_MIN_MAX_HIGH, canDisposeOfImage));
    }

    public static ImageDetails checkImageDetails(final FileAccess fSpec, final FileRecord existingFr, final PrintWriter failed, final String md5,
        final FileRecordDb<String> db) throws IOException {
        if(ImageUtils.isDecodableImage(fSpec) && !ImageDetails.hasHistogram(existingFr)) {
            // before we load the image, see if we have it in the db.
            final FileRecord[] matching = db.find(md5);
            if(matching != null && matching.length > 0) {
                // find one with a comlpete ImageDetails
                final var hasIt = Arrays.stream(matching)
                    .filter(fr -> ImageDetails.hasHistogram(fr))
                    .findFirst()
                    .orElse(null);

                if(hasIt != null)
                    return ImageDetails.getFrom(hasIt);
            }
            try(var limg = ImageUtils.loadImage(fSpec, 4096);) {
                if(limg == null || limg.image().dataAddr() == 0L)
                    return null;
                return ImageDetails.calculate(limg.dims(), limg.image(), true);
            }
        }
        return null;
    }

    public static FileRecord fix(final FileRecord fr) {
        final ImageDetails id = getFrom(fr);
        if(id != null)
            fr.additional().put(ImageDetails.KEY, id);
        return fr;
    }
}
