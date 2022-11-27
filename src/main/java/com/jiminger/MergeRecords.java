package com.jiminger;

import static com.jiminger.Config.FILE_RECORD_FILE_TO_WRITE;
import static com.jiminger.FileRecord.readFileRecords;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dempsy.serialization.jackson.JsonUtils;

public class MergeRecords {

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + MergeRecords.class.getName() + " path/to/config.json");
    }

    private static final ObjectMapper om = JsonUtils.makeStandardObjectMapper();

    static public void main(final String[] args) throws Exception {

        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);

            final String md5FileToWrite = c.md5FileToWrite;
            final String[] md5FilesToRead = c.md5FilesToRead;

            if(md5FileToWrite == null) {
                System.err.println("You must supply a record file to write by setting the field " + FILE_RECORD_FILE_TO_WRITE + " of the config");
                usage();
                System.exit(1);
            }

            System.out.println("Reading file records.");

            final List<FileRecord> frs = readFileRecords(
                Stream.concat(Stream.of(md5FileToWrite), Arrays.stream(Optional.ofNullable(md5FilesToRead).orElse(new String[0]))).toArray(String[]::new)

            );

            System.out.println("Merging file records.");
            // collapse the list.
            final List<FileRecord> resulting = new ArrayList<>(frs.size());
            final Map<String, FileRecord> uniqueRecords = new HashMap<>(frs.size());
            int dupCount = 0;
            for(final FileRecord fr: frs) {
                final FileRecord existing = uniqueRecords.get(fr.path);
                if(existing != null) {
                    if(!existing.equals(fr))
                        throw new IllegalStateException("Several FileRecords are duplicated but they differ. One is " + fr + " and the other is " + existing);
                    dupCount++;
                } else {
                    uniqueRecords.put(fr.path, fr);
                    resulting.add(fr);
                }
            }

            System.out.println("Merged record count: " + resulting.size());
            System.out.println("Total records among all input files: " + frs.size());
            System.out.println("Number of duplicates: " + dupCount);

            if(resulting.size() != (frs.size() - dupCount))
                throw new IllegalStateException("Wrong number of records after merging. Expected " + (frs.size() - dupCount) + " but got " + resulting);

            System.out.println("Writing new file...");
            final File md5File = new File(md5FileToWrite);
            try(PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {
                for(final FileRecord fr: resulting) {
                    md5os.println(om.writeValueAsString(fr));
                }
            }
            System.out.println("Done!");
        }
    }
}
