package com.jiminger;

import static com.jiminger.VfsConfig.createVfs;
import static net.dempsy.util.Functional.uncheck;

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
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.commands.DeleteCommand;
import com.jiminger.commands.HandleImageDuplicate;

import net.dempsy.serialization.jackson.JsonUtils;
import net.dempsy.util.UriUtils;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

public class RunCommandFile {

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

        if(args == null || args.length != 2) {
            System.err.println("usage: java -cp [] " + RunCommandFile.class.getName() + "path/to/config.json path/to/commandfile");
            System.exit(1);
            return;
        }

        final Config c = Config.load(args[0]);
        vfs = createVfs(c == null ? new String[0] : c.passwordsToTry);
        c.applyGlobalSettings();

        final String commandFileStr = args[1];

        final List<DeleteCommand> commands = new ArrayList<>();
        final List<HandleImageDuplicate> idCommands = new ArrayList<>();
        {
            // make sure no "matches" entries are ever deleted.
            System.out.println("Validating the command file.");
            try(final BufferedReader br = new BufferedReader(new FileReader(commandFileStr))) {
                String line;
                while((line = br.readLine()) != null) {
                    final var cmd = om.readValue(line, Command.class);
                    if(DeleteCommand.TYPE.equals(cmd.type)) {
                        // do delete
                        commands.add(om.readValue(line, DeleteCommand.class));
                    } else if(HandleImageDuplicate.TYPE.equals(cmd.type)) {
                        // stage for delete duplicate images
                        idCommands.add(om.readValue(line, HandleImageDuplicate.class));
                    } else
                        throw new IllegalStateException("Invalid command type \"" + cmd.type + "\" for record: " + line);
                }
            }
        }

        doDeleteCommands(c, commands);
        doHandleImageDuplicates(c, idCommands);
    }

    private static void doHandleImageDuplicates(final Config c, final List<HandleImageDuplicate> commands) throws IOException {
        if(commands.size() == 0)
            return;

        if(c.toRemoveStore == null)
            throw new NullPointerException("Cannot set Config.toRemoveStore to null. There needs to be a place to move files to eventually be deleted");
        final File destDirFile = new File(c.toRemoveStore);
        if(!destDirFile.exists()) {
            System.out.println("Config.toRemoveStore is set to " + c.toRemoveStore + " but that directory doesn't already exist .... creating");
            if(!c.dryRun) {
                if(!destDirFile.mkdirs()) {
                    System.err.println("ERROR: Failed trying to mkdirs " + c.toRemoveStore);
                    throw new IllegalStateException("ERROR: Failed trying to mkdirs " + c.toRemoveStore);
                }
            }
        }
        // belt and suspenders
        if(!c.dryRun) {
            if(!destDirFile.exists()) {
                System.err.println("ERROR: Failed trying to mkdirs " + c.toRemoveStore);
                throw new IllegalStateException("ERROR: Failed trying to mkdirs " + c.toRemoveStore);
            }

            if(!destDirFile.isDirectory()) {
                System.err.println("ERROR: " + c.toRemoveStore + " exists but is not a directory.");
                throw new IllegalStateException("ERROR: " + c.toRemoveStore + " exists but is not a directory.");
            }
        }

        commands.forEach(hid -> {
            if(c.dryRun)
                System.out.println("[DRYRUN] Moving " + hid.duplicate().path());
            else {
                System.out.println("Moving " + hid.duplicate().path());
                move(hid.duplicate().uri(), destDirFile);
            }
        });
    }

    private static void move(final URI src, final File destDirFile) {
        final File dest;
        {
            int count = 0;
            String suffix = "";
            while(true) {
                final String name = UriUtils.getName(src) + suffix;
                final File tDest = new File(destDirFile, name);
                if(tDest.exists()) {
                    System.err.println("ERROR: the destination to move " + src + " to, \"" + name + "\" already exists.");
//                throw new IllegalStateException(
//                    "ERROR: the destination to move " + src + " to, \"" + tDest.getAbsolutePath() + "\" already exists.");
                    count++;
                    suffix = "_" + count;
                } else {
                    dest = tDest;
                    break;
                }
            }
        }

        final java.nio.file.Path sourcePath = Paths.get(src);

        if(!Files.exists(sourcePath)) {
            System.err.println("ERROR: the source to move " + src + " to, \"" + dest.getAbsolutePath() + "\" doesn't exists.");
            throw new IllegalStateException(
                "ERROR: the source to move " + src + " to, \"" + dest.getAbsolutePath() + "\" doesn't exists.");
        }

        System.out.println("Copying " + src + " to " + dest);
        uncheck(() -> Files.copy(sourcePath, dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES));

        final InputStream[] streamsToCompare = Stream
            .of(uncheck(() -> vfs.toPath(src).read()), new BufferedInputStream(uncheck(() -> new FileInputStream(dest))))
            .toArray(InputStream[]::new);

        System.out.println("Comparing " + src + " and " + dest);
        if(!uncheck(() -> compareStreams(streamsToCompare))) {
            System.err
                .println("The copied file " + src + " and the destination file " + dest + " don't appear to match. Not deleting the source and giving up.");
            throw new IllegalStateException(
                "The copied file " + src + " and the destination file " + dest + " don't appear to match. Not deleting the source and giving up.");
        }
        System.out.println("Deleting " + src);
        uncheck(() -> Files.delete(sourcePath));
    }

    private static void doDeleteCommands(final Config c, final List<DeleteCommand> commands) throws IOException {
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

            // check for the existence of all files.
            Stream.concat(Stream.of(del.toDelete()), del.matches().stream())
                .map(fr -> fr.path())
                .map(p -> uncheck(() -> new URI(p)))
                .map(u -> uncheck(() -> vfs.toPath(u)))
                .forEach(p -> {
                    if(!uncheck(() -> p.exists()))
                        throw new IllegalStateException("The file at \"" + p + "\" from the DEL command \"" + del + "\" doesn't exit");
                });

            ;

            // create an array of input streams using vfs (and potentially mmaping files)
            // and check that the toDelete stream matches byte for byte all of the matching streams.
            // System.out.println(del);
            final InputStream[] streamsToCompare = Stream.concat(Stream.of(del.toDelete()), del.matches().stream())
                .map(fr -> uncheck(() -> fr.read(vfs)))
                .toArray(InputStream[]::new);

            if(streamsToCompare.length < 2)
                throw new IllegalStateException("The DEL record entry doesn't contain anything to match against: " + om.writeValueAsString(del));

            final Path path = vfs.toPath(del.toDelete().uri());
            if(compareStreams(streamsToCompare)) {
                if(c.dryRun)
                    System.out.println("[DRYRUN] Deleting " + path);
                else {
                    System.out.println("Deleting " + path);
                    path.delete();
                }
            } else {
                if(c.dryRun)
                    System.out.println("[DRYRUN] Skipping " + path);
                else
                    System.out.println("Skipping " + path);
            }
        }
    }

    private static int read(final InputStream is, final byte[] buf) throws IOException {
        int readBytes = 0;
        final int numBytes = buf.length;
        boolean readSuccessfully = false;
        while(readBytes < numBytes) {
            final int result = is.read(buf, readBytes, numBytes - readBytes);
            if(result == -1)
                return readSuccessfully ? readBytes : -1;
            readSuccessfully = true;
            readBytes += result;
        }
        return readBytes;
    }

    public static boolean compareStreams(final InputStream[] streams) throws IOException {
        final int bufferSize = 1024 * 1024;

        final byte[] baseBuffer = new byte[bufferSize];
        final byte[] toCompareTo = new byte[bufferSize];

        for(boolean done = false; !done;) {
            final int numBytesBase = read(streams[0], baseBuffer);
            if(numBytesBase < 0)
                done = true;

            for(int i = 1; i < streams.length; i++) {
                final int readBytes = read(streams[i], toCompareTo);
                // numBytesBase is either: bufferSize, some amount less than that if it's the end of the stream, or
                // -1 if the stream is completely drained. In any of these cases the other stream should do the same.
                if(numBytesBase != readBytes)
                    return false;
                if(numBytesBase >= 0 && !java.util.Arrays.equals(baseBuffer, 0, numBytesBase, toCompareTo, 0, readBytes))
                    return false;
            }
        }

        return true;
    }
}
