package com.jiminger.utils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.img_hash.ImgHashBase;
import org.opencv.img_hash.PHash;
import org.opencv.imgcodecs.Imgcodecs;

import ai.kognition.pilecv4j.image.CvMat;

public class PHashBasedImageIndex {

    static {
        CvMat.initOpenCv();
    }

    public static void main(final String[] args) {
        // Load the images
        final Mat image1 = Imgcodecs.imread("image1.jpg");
        final Mat image2 = Imgcodecs.imread("image2.jpg");

        if(image1.empty() || image2.empty()) {
            System.out.println("Error: One or both images could not be loaded.");
            return;
        }

        // Create the PHash object
        final ImgHashBase pHash = PHash.create();

        // Compute perceptual hashes
        final Mat hash1 = new Mat();
        final Mat hash2 = new Mat();
        pHash.compute(image1, hash1);
        pHash.compute(image2, hash2);

        // Display hashes (for debugging or inspection)
        System.out.println("Hash1: " + hash1.dump());
        System.out.println("Hash2: " + hash2.dump());

        // Compare the hashes using a distance metric
        final double distance = Core.norm(hash1, hash2, Core.NORM_HAMMING);

        // Normalize the distance to a similarity score (0.0 to 1.0)
        final int maxBits = 64; // Default size for OpenCV PHash (8x8 hash = 64 bits)
        final double similarity = 1.0 - (distance / maxBits);

        // Display the similarity score
        System.out.println("Hamming Distance: " + distance);
        System.out.println("Similarity Score: " + similarity);

        // Set a similarity threshold (e.g., 0.9 for high similarity)
        final double similarityThreshold = 0.9;
        if(similarity >= similarityThreshold) {
            System.out.println("Images are similar.");
        } else {
            System.out.println("Images are different.");
        }
    }
}
