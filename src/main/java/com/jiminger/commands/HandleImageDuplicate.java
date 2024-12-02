package com.jiminger.commands;

import com.jiminger.records.FileRecord;

public record HandleImageDuplicate(String type, FileRecord duplicate, FileRecord mainImage, double correlationCoef) {

    public static final String TYPE = "HID";

    public HandleImageDuplicate {
        if(!type.equals(TYPE))
            throw new IllegalArgumentException("Cannot construct a " + HandleImageDuplicate.class.getSimpleName() + " with a type of \"" + type + "\"");

        if(duplicate == null)
            throw new NullPointerException("Cannot set the image record to be deleted because it's a duplicate to null");

        if(mainImage == null)
            throw new NullPointerException(
                "Cannot set the matched image records to null.");
    }
}
