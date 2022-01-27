package com.jiminger;

public class Md5Verify {

    // public static void verifyMd5File(final String verifyOutputFile, final String outFileName, final boolean appendOutfile,
    // final String[] md5FilesToRead, final String[] directoriesToScan, final String failedFile) throws IOException {
    // final Map<String, String> file2md5 = Md5File.readMd5FileLookup(md5FilesToRead);
    //
    // final File verifyOutFile = new File(verifyOutputFile);
    // try(PrintWriter failed = (failedFile != null) ? new PrintWriter(new BufferedOutputStream(new FileOutputStream(failedFile)))
    // : new PrintWriter(System.err);
    // PrintWriter verifyOut = new PrintWriter(new BufferedOutputStream(new FileOutputStream(verifyOutFile)));
    // PrintWriter outWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFileName, appendOutfile)), true);) {
    //
    // // pass to calc md5
    // recheck(() -> Arrays.stream(directoriesToScan).forEach(d -> uncheck(() -> {
    // final File directory = new File(d);
    // if(!directory.exists())
    // failed.println(directory.toURI().toString() + "||" + "doesn't exist");
    // else {
    // doMd5(verifyOut, outWriter, directory, file2md5, failed);
    // }
    // })));
    //
    // outWriter.println("DONE: Finished Clean");
    // }
    // }
    //
    // public static void usage() {
    // System.err.println("Usage: java -cp [classpath] " + Md5Verify.class.getSimpleName() + " path/to/config.json");
    // }
    //
    // static public void main(final String[] args) throws Exception {
    // if(args == null || args.length != 1)
    // usage();
    // else {
    // final Config c = Config.load(args[0]);
    // // deleteDups(Config.md5FileToWrite);
    // verifyMd5File(
    // c.verifyOutputFile,
    // c.outFile, c.appendOutfile,
    // c.md5FilesToRead,
    // c.directoriesToScan,
    // c.failedFile);
    //
    // System.out.println("Finished Clean");
    // }
    // }
    //
    // private static void doMd5(final PrintWriter verifyOut, final PrintWriter outos, final File file, final Map<String, String> existing,
    // final PrintWriter failed) throws IOException {
    // outos.println("VERIFYING: " + file);
    // if(!file.exists())
    // throw new FileNotFoundException("File " + file + " doesn't exist.");
    //
    // if(file.isDirectory()) {
    // final File[] subdirs = file.listFiles();
    // if(subdirs == null) {
    // if(failed != null)
    // failed.println("CANT GET SUBDIRS:" + file.getAbsolutePath());
    // } else
    // recheck(() -> Arrays.stream(subdirs).forEach(f -> uncheck(() -> doMd5(verifyOut, outos, f, existing, failed))));
    // } else {
    // final String existingMd5 = existing.get(file.getAbsolutePath());
    // if(existingMd5 != null) {
    // final String curDigest = bytesToHex(MD5.hash(file)).toString();
    // if(!existingMd5.equals(curDigest)) {
    // verifyOut.println("BADMD5 " + file.getAbsolutePath());
    // verifyOut.flush();
    // }
    // } else {
    // verifyOut.println("MSGMD5 " + file.getAbsolutePath());
    // verifyOut.flush();
    // }
    // }
    // }
    //
    // private static void deleteDups(String... md5FilesToRead) throws IOException {
    // final Map<String,List<String>> md52Files = Md5File.readMd5File(md5FilesToRead);
    //
    // // find dups
    // md52Files.entrySet().stream()
    // .filter(e -> e.getValue().size() > 1)
    // .map(e -> e.getValue())
    // .forEach(fileNames -> {
    // final Set<String> allNames = new HashSet<>();
    // allNames.addAll(fileNames);
    // final List<String> withDups = fileNames.stream().filter(n -> {
    // File f = new File(n);
    // //System.out.println(f.getParentFile().getAbsolutePath());
    // return f.exists() && f.getParentFile().getName().startsWith("DUPS");
    // }).collect(Collectors.toList());
    // withDups.stream()
    // .map(File::new)
    // .forEach(f -> {
    // System.out.println("removing: " + f.getAbsolutePath());
    // f.delete();
    // File p = f.getParentFile();
    // File[] children = p.listFiles();
    // if (children == null || children.length == 0) {
    // System.out.println("deleting: " + p.getAbsolutePath());
    // p.delete();
    // }
    // });
    // });
    //
    // }

}
