//package com.jiminger.old;
//
//import static com.jiminger.records.FileRecord.readFileRecords;
//import static net.dempsy.util.Functional.chain;
//import static net.dempsy.util.Functional.uncheck;
//
//import java.net.URI;
//import java.security.MessageDigest;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.function.Consumer;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import com.jiminger.Config;
//import com.jiminger.Md5File;
//import com.jiminger.old.Organize3.FileRecordNode;
//import com.jiminger.records.FileRecord;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import net.dempsy.util.HexStringUtil;
//import net.dempsy.util.MutableInt;
//import net.sf.sevenzipjbinding.SevenZip;
//
//public class Organize3 {
//    private static final Logger LOGGER = LoggerFactory.getLogger(Organize3.class);
//
//    public static void usage() {
//        System.err.println("Usage: java -cp [classpath] " + Md5File.class.getName() + " path/to/config.json");
//    }
//
//    public static class FileRecordNode {
//        final FileRecord frecord;
//        final String[] path;
//        final String[] compositeScheme;
//        final String[] parentPath;
//        final List<FileRecordNode> children;
//        final boolean isDirectory;
//        FileRecordNode parentNode;
//        String md5 = null;
//        long numBytes = 0;
//
//        public FileRecordNode(final FileRecord rec) {
//            this.frecord = rec;
//            final String[] split = rec.uri().split("/", -1);
//            final String scheme;
//            if(split[0].endsWith(":")) {
//                // then there is no part of the path in the scheme.
//                path = Arrays.copyOfRange(split, 1, split.length);
//                scheme = split[0].substring(0, split[0].length() - 1); // chop off the trailing ':'
//            } else {
//                final String[] schemeSplit = split[0].split(":", -1);
//                scheme = String.join(":", Arrays.copyOfRange(schemeSplit, 0, schemeSplit.length - 1));
//                path = new String[split.length];
//                System.arraycopy(split, 1, path, 1, split.length - 1);
//                path[0] = schemeSplit[schemeSplit.length - 1];
//            }
//            final var tmpl = Arrays.asList(scheme.split(":", -1));
//            Collections.reverse(tmpl);
//            compositeScheme = tmpl.toArray(String[]::new);
//            this.children = new ArrayList<>();
//            this.isDirectory = false;
//            this.parentPath = path.length == 1 ? new String[0] : Arrays.copyOfRange(path, 0, path.length - 1);
//        }
//
//        public FileRecordNode(final String[] path) {
//            frecord = new FileRecord(String.join("/", path), 0, null, 0, null, null);
//            this.path = Arrays.copyOf(path, path.length);
//            this.compositeScheme = new String[0];
//            this.children = new ArrayList<>();
//            this.isDirectory = true;
//            this.parentPath = path.length == 1 ? new String[0] : Arrays.copyOfRange(path, 0, path.length - 1);
//        }
//
//        public FileRecordNode() { // root node
//            frecord = new FileRecord("", 0, null, 0, null, null);
//            this.path = new String[0];
//            this.parentPath = new String[0];
//            this.compositeScheme = new String[0];
//            this.children = new ArrayList<>();
//            this.isDirectory = true;
//        }
//
//        public boolean isRoot() {
//            return isDirectory && path.length == 0;
//        }
//
//        public boolean place(final FileRecordNode frn) {
//            if(isChild(frn)) {
//                final String[] nodesLocation = frn.isDirectory ? frn.path : frn.parentPath;
//                if(Arrays.equals(path, nodesLocation)) {
//                    // this it's a direct immediate descendant.
//                    children.add(frn);
//                    frn.parentNode = this;
//                    return true;
//                } else { // otherwise it's a child of a child.
//                    final String[] remainder = path.length == nodesLocation.length ? new String[0]
//                        : Arrays.copyOfRange(nodesLocation, path.length, nodesLocation.length);
//                    // if the result here has no elements then it SHOULD have matched the above
//                    // condition .... but we'll double check here.
//                    if(remainder.length < 1)
//                        throw new IllegalStateException("WTF!");
//                    final String subdir = remainder[0];
//                    // see if we already have a subdir with that name.
//                    FileRecordNode child = null;
//                    final String[] subdirPath = Stream.concat(Arrays.stream(path), Stream.of(subdir)).toArray(String[]::new);
//                    for(final FileRecordNode cur: children) {
//                        if(cur.isDirectory) {
//                            // once again the cur.path should be an exact superset of this path
//                            if(Arrays.equals(subdirPath, cur.path)) {
//                                child = cur;
//                                break;
//                            }
//                        }
//                    }
//                    if(child == null) {
//                        child = new FileRecordNode(subdirPath);
//                        children.add(child);
//                        child.parentNode = this;
//                    }
//                    return child.place(frn);
//                }
//            } else
//                return false;
//        }
//
//        public boolean isChild(final FileRecordNode n) {
//            if(!isDirectory)
//                return false;
//
//            if(isRoot())
//                return true;
//
//            // if my path is the prefix for the given node's path (parent if it's not a dir) then it's a child.
//            final String[] nodesLocation = n.isDirectory ? n.path : n.parentPath;
//            // check if my path is a prefix for the nodesLocation
//            if(nodesLocation.length < path.length)
//                return false; /// if the nodesLocation is less than my location then it's higher in the tree
//            for(int i = 0; i < path.length; i++) {
//                if(!path[i].equals(nodesLocation[i]))
//                    return false;
//            }
//            return true;
//        }
//
//        public void finalizeTree() {
//            if(!isDirectory) {
//                md5 = frecord.md5();
//                numBytes = frecord.size();
//                return;
//            }
//
//            final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
//            if(children.size() == 0) {
//                md5 = HexStringUtil.bytesToHex(md.digest());
//                numBytes = 0;
//                return;
//            }
//
//            for(final var c: children) {
//                c.finalizeTree();
//            }
//
//            if(children.size() > 1) {
//                for(final var c: children) {
//                    md.update(c.md5.getBytes());
//                    numBytes += c.numBytes;
//                }
//                md5 = HexStringUtil.bytesToHex(md.digest());
//            } else { // num children == 1.
//                final FileRecordNode child = children.get(0);
//                md5 = child.md5;
//                numBytes = child.numBytes;
//            }
//        }
//
//        @Override
//        public String toString() {
//            return getClass().getSimpleName() + ":" + Arrays.toString(path);
//        }
//
//        public void depthFirst(final Consumer<FileRecordNode> traverseMe) {
//            if(!isDirectory && children.size() > 0) {
//                LOGGER.warn("The node at " + Arrays.toString(path) + " is not a directory yet has children.");
//            }
//
//            if(children.size() > 0) {
//                for(final var c: children) {
//                    c.depthFirst(traverseMe);
//                }
//            }
//
//            traverseMe.accept(this);
//        }
//    }
//
//    static public void main(final String[] args) throws Exception {
//
//        final String osArch = System.getProperty("os.arch");
//        System.out.println("OS Arch:" + osArch);
//        if("aarch64".equals(osArch))
//            SevenZip.initSevenZipFromPlatformJAR("Linux-arm64");
//
//        final FileRecordNode root = new FileRecordNode();
//
//        if(args == null || args.length != 1) {
//            usage();
//            System.exit(1);
//        }
//
//        final Config config = Config.load(args[0]);
//
//        final Map<String, FileRecordNode> file2FileRecords = readFileRecords(
//            Stream.concat(Stream.of(config.md5FileToWrite), Arrays.stream(Optional.ofNullable(config.md5FilesToRead).orElse(new String[0])))
//                .toArray(String[]::new)
//
//        ).stream()
//            .filter(fr -> "file".equals(uncheck(() -> new URI(fr.uri())).getScheme()))
//            // .peek(fr -> System.out.println(fr.path))
//            .collect(Collectors.toMap(fs -> fs.uri(), fs -> new FileRecordNode(fs), (fr1, fr2) -> {
//                if(fr1.equals(fr2))
//                    return fr1;
//                throw new IllegalStateException("Duplicate keys for " + fr1 + " and " + fr2 + " that can't be merged.");
//            }));
//
//        file2FileRecords.forEach((path, fr) -> {
//
//            // need to find the place in the tree.
//            root.place(fr);
//
//        });
//
//        file2FileRecords.clear(); // help the poor gc
//
//        // now cascade calculate md5s for directories.
//        root.finalizeTree();
//
//        final MutableInt recordCount = new MutableInt(0);
//        root.depthFirst(frn -> recordCount.val++);
//
//        // build an index to find the best merges.
//        final Map<String, List<FileRecordNode>> md5ToNode = new HashMap<>();
//        root.depthFirst(fr -> md5ToNode.compute(fr.md5, (k, v) -> chain(v == null ? new ArrayList<>() : v, l -> l.add(fr))));
//
//        // prune to just dirs.
//        final Map<String, List<FileRecordNode>> md5ToDirNode = new HashMap<>();
//        md5ToNode.forEach((k, v) -> {
//            final var newVal = v.stream()
//                .filter(f -> f.isDirectory)
//                .filter(f -> f.children.size() != 1) // if the directory has only 1 child then it's just a copy
//                .collect(Collectors.toList());
//            if(newVal.size() > 0)
//                md5ToDirNode.put(k, newVal);
//        });
//
//        final List<List<FileRecordNode>> toSort = new ArrayList<>();
//        // now go through each md5 and rank them by the amount of duplication (or the
//        // number of bytes saved if collapsed)
//        md5ToDirNode.forEach((k, v) -> {
//            toSort.add(v);
//        });
//
//        toSort.sort((l, r) -> rank(l) - rank(r));
//
//        System.out.println();
//    }
//
//    public static int rank(final List<FileRecordNode> frns) {
//        final var first = frns.get(0);
//        return (int)(first.numBytes * (frns.size() - 1));
//    }
//}
