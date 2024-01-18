package com.jiminger;

import static com.jiminger.VfsConfig.createVfs;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.util.HexStringUtil.bytesToHex;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.commands.DeleteCommand;
import com.jiminger.commands.HandleImageDuplicate;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.vfs.FileSpec;
import net.dempsy.vfs.Vfs;

public class UndoDeleteCommands {

    private static Vfs vfs;

    private static ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_EMPTY)
    public static record Command(String type) {
        public Command {
            if(type == null)
                throw new IllegalArgumentException("Cannot construct a " + Command.class.getSimpleName() + " with no type field");
        }
    }

    static public void main(final String[] args) throws Exception {

        if(args == null || args.length != 3) {
            System.err.println("usage: java -cp [] " + UndoDeleteCommands.class.getName() + "path/to/config.json path/to/commandfile uri-prefix-to-restore");
            System.exit(1);
            return;
        }

        final Config c = Config.load(args[0]);
        vfs = createVfs(c == null ? new String[0] : c.passwordsToTry);
        c.applyGlobalSettings();

        final String commandFileStr = args[1];
        final String prefix = args[2];

        final List<DeleteCommand> commands = new ArrayList<>();
        {
            // make sure no "matches" entries are ever deleted.
            System.out.println("Validating the command file.");
            try(final BufferedReader br = new BufferedReader(new FileReader(commandFileStr))) {
                String line;
                while((line = br.readLine()) != null) {
                    final var cmd = om.readValue(line, Command.class);
                    if(DeleteCommand.TYPE.equals(cmd.type)) {
                        final var delCmd = om.readValue(line, DeleteCommand.class);
                        // do delete
                        if(delCmd.toDelete().uri().toString().startsWith(prefix))
                            commands.add(delCmd);
                    } else if(!HandleImageDuplicate.TYPE.equals(cmd.type))
                        throw new IllegalStateException("Invalid command type \"" + cmd.type + "\" for record: " + line);
                }
            }
        }

        if(commands.size() == 0) {
            System.out.println("WARNING: There are no commands from \"" + commandFileStr + "\" that contain delete commands to delete files that start with \""
                + prefix + "\"");
            return;
        }

        undoDeleteCommands(c, commands);
    }

    private static void undoDeleteCommands(final Config c, final List<DeleteCommand> commands) throws IOException {
        // go through all of the commands and make sure the toDelete is never ALSO among the matches
        {
            final Set<String> toDeleteAbsolutePaths = new HashSet<>();
            for(final var del: commands) {
                if(!"file".equals(del.toDelete().uri().getScheme()))
                    throw new IllegalStateException("Can only handle uris with the scheme \"file:\" being deleted.");

                final File file = vfs.toFile(del.toDelete().uri());
                if(file == null)
                    throw new IllegalStateException("Cannot convert the uri \"" + del.toDelete().uri() + "\" to a file so it can't be deleted or validated.");
                final String toDeleteAbsolutePath = file.getAbsolutePath();
                if(toDeleteAbsolutePaths.contains(toDeleteAbsolutePath))
                    throw new IllegalStateException(
                        "The entry to delete with the uri \"" + del.toDelete().uri() + "\" seems to exist in the command file more than once.");
                toDeleteAbsolutePaths.add(toDeleteAbsolutePath);
            }

            // now go back through and make sure none of the "matches" are also in the list being deleted.
            for(final var del: commands) {
                for(final var match: del.matches()) {
                    if(!"file".equals(match.uri().getScheme()))
                        throw new IllegalStateException("Currently can only handle match uris with the scheme \"file:\".");
                    final File file = vfs.toFile(match.uri());
                    if(file == null)
                        throw new IllegalStateException("Cannot convert the uri \"" + match.uri() + "\" to a file so it can't be validated as a match.");

                    if(toDeleteAbsolutePaths.contains(file.getAbsolutePath()))
                        throw new IllegalStateException(
                            "INVALID COMMAND FILE: The file to delete at \"" + file.getAbsolutePath() + "\" is also being matched against at " + del);
                }
            }
        }

        for(final DeleteCommand del: commands) {
            // we're going to remove del.toDelete but ONLY if the files in del.matches exist and are identical.
            if(del.matches() == null || del.matches().size() == 0)
                throw new IllegalArgumentException("Illegal " + DeleteCommand.class.getSimpleName() + " with no matched records");

            // check for the existence of all matches.
            final var curMatches = del.matches().stream()
                .filter(fr -> uncheck(() -> vfs.toPath(new URI(fr.path())).exists()))
                .collect(Collectors.toList());

            if(curMatches.size() == 0)
                throw new IllegalStateException("Couldn't find any existing files to copy from among " + del.matches());

            // verify the md5 is correct.
            final FileRecord copyBackFrom = curMatches.stream()
                .filter(fr -> {
                    final var fs = new FileSpec(uncheck(() -> vfs.toPath(new URI(fr.path()))));
                    return fr.md5().equals(bytesToHex(uncheck(() -> MD5.hash(fs))));
                })
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Existing files have no matching md5 for " + curMatches));

            final URI src = copyBackFrom.uri();
            final URI dest = del.toDelete().uri();

            final File destFile = new File(dest);
            if(destFile.exists()) {
                try(var srcIs1 = new BufferedInputStream(new FileInputStream(destFile));
                    var srcIs2 = new BufferedInputStream(new FileInputStream(new File(src)));) {

                    System.out.println("File Exists. Comparing \"" + dest + "\" with \"" + src + "\"");
                    if(!RunCommandFile.compareStreams(new InputStream[] {srcIs1,srcIs2}))
                        throw new IllegalStateException("File \"" + dest + "\" doesn't match \"" + src + "\"");
                }
            } else {
                if(c.dryRun) {
                    System.out.println("[DRYRUN] Copying " + src + " to " + dest);
                } else {
                    destFile.getParentFile().mkdirs();
                    System.out.println("Copying " + src + " to " + dest);
                    uncheck(() -> Files.copy(Paths.get(src), Paths.get(dest), StandardCopyOption.COPY_ATTRIBUTES));
                }
            }
        }
    }
}
