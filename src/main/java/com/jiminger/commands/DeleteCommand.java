package com.jiminger.commands;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.jiminger.records.FileRecord;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public record DeleteCommand(String type, FileRecord toDelete, List<FileRecord> matches) {

    public static final String TYPE = "DEL";

    public DeleteCommand {
        if(!type.equals(TYPE))
            throw new IllegalArgumentException("Cannot construct a " + DeleteCommand.class.getSimpleName() + " with a type of \"" + type + "\"");

        if(toDelete == null)
            throw new NullPointerException("Cannot set the record to be deleted to null");

        if(matches == null || matches.size() == 0)
            throw new NullPointerException(
                "Cannot set the matched records to null or an empty list. In order to delete there MUST be other files with the same contents.");
    }
}
