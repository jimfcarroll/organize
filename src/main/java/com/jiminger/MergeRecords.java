package com.jiminger;

import static com.jiminger.Config.FILE_RECORD_FILE_TO_WRITE;
import static com.jiminger.VfsConfig.createVfs;
import static com.jiminger.records.FileRecord.makeFileRecordsManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiminger.records.FileRecord;
import com.jiminger.records.FileRecord.Manager;

import net.dempsy.util.MutableInt;
import net.dempsy.vfs.Vfs;

public class MergeRecords {
    private static Vfs vfs;

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + MergeRecords.class.getName() + " path/to/config.json");
    }

    private static final ObjectMapper from = FileRecord.makeStandardObjectMapper();

    static public void main(final String[] args) throws Exception {

        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);
            vfs = createVfs(c.passwordsToTry);

            final String md5FileToWrite = c.md5FileToWrite;
            final String[] md5FilesToRead = c.md5FilesToRead;

            if(md5FileToWrite == null) {
                System.err.println("You must supply a record file to write by setting the field " + FILE_RECORD_FILE_TO_WRITE + " of the config");
                usage();
                System.exit(1);
            }

            System.out.println("Reading file records.");

            try(final Manager frs = makeFileRecordsManager(vfs, md5FileToWrite, md5FilesToRead);) {

                System.out.println("Merging " + frs.size() + " file records.");
                // collapse the list.
                final List<FileRecord> resulting = new ArrayList<>((int)frs.size());
                final Map<URI, FileRecord> uniqueRecords = new HashMap<>((int)frs.size());
                final MutableInt dupCount = new MutableInt(0);
                frs.stream()
                    .forEach(fr -> {
                        final FileRecord existing = uniqueRecords.get(fr.uri());
                        final FileRecord toPut;
                        if(existing != null) {
//                    if(!existing.equals(fr))
                            if(existing.md5().equals(fr.md5()))
                                toPut = fr.additional() != null ? fr : existing;
                            else
                                throw new IllegalStateException(
                                    "Several FileRecords are duplicated but they differ. One is " + fr + " and the other is " + existing);
                            dupCount.val++;
                        } else {
                            toPut = fr;
                            resulting.add(fr);
                        }
                        uniqueRecords.put(toPut.uri(), toPut);
                    });

                System.out.println("Merged record count: " + resulting.size());
                System.out.println("Total records among all input files: " + frs.size());
                System.out.println("Number of duplicates: " + dupCount);

                if(resulting.size() != (frs.size() - dupCount.val))
                    throw new IllegalStateException("Wrong number of records after merging. Expected " + (frs.size() - dupCount.val) + " but got " + resulting);

                System.out.println("Writing new file...");
                final File md5File = new File(md5FileToWrite);
                try(PrintWriter md5os = new PrintWriter(new BufferedOutputStream(new FileOutputStream(md5File)));) {
                    for(final FileRecord fr: resulting) {
                        md5os.println(from.writeValueAsString(fr));
                    }
                }
                System.out.println("Done!");
            }
        }
    }
}
