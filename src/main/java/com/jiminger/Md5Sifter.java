package com.jiminger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Md5Sifter {

    public static void usage() {
        System.err.println("Usage: java -cp [classpath] " + Md5Sifter.class.getSimpleName() + " path/to/config.json");
    }

    public static void main(final String[] args) throws Exception {

        if(args == null || args.length != 1)
            usage();
        else {
            final Config c = Config.load(args[0]);

            final Map<String, List<String>> md52files = new HashMap<>();

            // recheck(() -> Arrays.stream(c.md5FilesToRead).forEach(fileName -> uncheck(() -> readMd5File(md52files, fileName))));
            System.out.println("num duplicate groups: " + md52files.values().stream().filter(l -> l.size() > 1).count());
            // final File actionsFile = new File(c.actionsFileName);
            // try(final PrintWriter actions = new PrintWriter(new BufferedOutputStream(new FileOutputStream(actionsFile)), true);) {
            // md52files.values().stream()
            // .filter(f -> f.size() > 1)
            // .filter(f -> f.stream().filter(v -> v.startsWith("/media/jim/Seagate Expansion Drive/Family Media/Animations")).count() > 0)
            // .forEach(f -> {
            // actions.println("# Disambiguating:");
            // f.stream().forEach(e -> actions.println("# " + e));
            //
            // final List<String> files = new ArrayList<>(f);
            // Collections.sort(files, new Comparator<String>() {
            //
            // @Override
            // public int compare(final String o1, final String o2) {
            // final String p1 = new File(o1).getParent();
            // final String p2 = new File(o2).getParent();
            // if(p1.equals(p2)) {
            // return o1.length() - o2.length();
            // }
            // return ranking(p1) - ranking(p2);
            // }
            // });
            //
            // final String keeper = files.remove(0);
            // actions.println("KP " + keeper);
            // files.stream().forEach(c -> actions.println("RM " + c));
            // });
            // }
        }
    }
}
