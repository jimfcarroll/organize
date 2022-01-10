package com.jiminger;

// import java.io.BufferedReader;
// import java.io.File;
// import java.io.FileNotFoundException;
// import java.io.FileReader;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;

public class Execute {
    // private static List<String> readActions(String actionsFile) throws IOException {
    // File file = new File(actionsFile);
    // if (!file.exists())
    // throw new FileNotFoundException("Actions file " + actionsFile + " doesn't exist.");
    // List<String> ret = new ArrayList<>();
    // try (BufferedReader br = new BufferedReader(new FileReader(file));) {
    // for (String line = br.readLine(); line != null; line = br.readLine())
    // ret.add(line);
    // }
    // return ret;
    // }
    //
    //
    // public static void main(String[] args) throws Exception {
    // List<String> commands = readActions(Config.actionsFileName);
    //
    // commands.stream().filter(c -> !c.trim().startsWith("#")).filter(c -> !" ".equals(c.trim())).forEach(c -> {
    // String cmd = c.trim();
    // int index = cmd.indexOf(' ');
    // String key = cmd.substring(0, index);
    // String parm = cmd.substring(index + 1);
    // switch (key) {
    // case "KP":
    // System.out.println("Keeping " + parm);
    // break;
    // case "RM":
    // System.out.println("Removing " + parm);
    // File f = new File(parm);
    // if (f.exists()) {
    // if (!f.delete())
    // throw new RuntimeException("Failed to delete " + parm + " in responde to command " + cmd);
    // } else {
    // System.out.println("File \"" + parm + "\" doesn't exist. Skipping.");
    // }
    // break;
    // default:
    // System.out.println("Unknown command " + cmd);
    // }
    // });
    // }
}
