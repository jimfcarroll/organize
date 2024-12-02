package com.jiminger.commands;

import static net.dempsy.util.Functional.uncheck;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.records.FileRecord;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public record Command(String type) {

    public Command {
        if(type == null)
            throw new IllegalArgumentException("Cannot construct a " + Command.class.getSimpleName() + " with no type field");
    }

    private static ObjectMapper om = FileRecord.makeStandardObjectMapper();

    public static Stream<Object> readCommands(final String commandFileStr) throws IOException {
        return readCommands(new BufferedInputStream(new FileInputStream(commandFileStr)));
    }

    public static Stream<Object> readCommands(final InputStream is) {
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        return br.lines()
            .map(line -> {
                final var cmd = uncheck(() -> om.readValue(line, Command.class));
                final Object specificCmd;
                if(DeleteCommand.TYPE.equals(cmd.type())) {
                    // do delete
                    specificCmd = uncheck(() -> om.readValue(line, DeleteCommand.class));
                } else if(HandleImageDuplicate.TYPE.equals(cmd.type())) {
                    // stage for delete duplicate images
                    specificCmd = uncheck(() -> om.readValue(line, HandleImageDuplicate.class));
                } else
                    throw new IllegalStateException("Invalid command type \"" + cmd.type() + "\" for record: " + line);
                return specificCmd;
            }).onClose(() -> uncheck(() -> br.close()));
    }

}
