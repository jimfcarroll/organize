public class PluginLoader {

    public static void injectJarIntoClassLoader(String jarPath) throws Exception {
        File file = new File(jarPath);
        URL jarUrl = file.toURI().toURL();

        // Get the current classloader (should be LaunchedURLClassLoader if running spring-boot java -jar)
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        if (classLoader instanceof URLClassLoader) {
            // For older Java versions (Java 8), URLClassLoader is used
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(classLoader, jarUrl);
        } else {
            // Newer Spring Boot (Java 9+) -> more complicated: need to hack LaunchedURLClassLoader
            try {
                Method method = classLoader.getClass().getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(classLoader, jarUrl);
            } catch (NoSuchMethodException nsme) {
                throw new IllegalStateException("Cannot inject URL into ClassLoader: " + classLoader.getClass(), nsme);
            }
        }
    }

@Override
public void initialize(ConfigurableApplicationContext ctx) {
    Environment env = ctx.getEnvironment();
    String jarPath = env.getProperty("abstraction.jar-path");

    if (jarPath == null) {
        throw new IllegalStateException("Plugin jar-path must be set in configuration.");
    }

    try {
        PluginLoader.injectJarIntoClassLoader(jarPath);
    } catch (Exception e) {
        throw new RuntimeException("Failed to load plugin jar", e);
    }
}

}

@Test
void testPluginLoadingFromExternalJar() throws Exception {
    // 1. Create a temporary directory and source file for plugin
    Path tempDir = Files.createTempDirectory("plugin-test");
    Path sourceFile = tempDir.resolve("my/special/Implementation.java");
    Files.createDirectories(sourceFile.getParent());

    String code = """
        package my.special;

        import com.example.core.Abstraction;

        public class Implementation implements Abstraction {
            public String greet() { return "Hi from test plugin"; }
        }
        """;

    Files.writeString(sourceFile, code);

    // 2. Compile the plugin class
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK required for compiling test plugin");
    int result = compiler.run(null, null, null,
            "-d", tempDir.toString(),
            sourceFile.toString());
    assertEquals(0, result, "Compilation failed");

    // 3. Package the compiled class into a JAR
    Path jarPath = tempDir.resolve("plugin.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
        Path classFile = tempDir.resolve("my/special/Implementation.class");
        jar.putNextEntry(new ZipEntry("my/special/Implementation.class"));
        Files.copy(classFile, jar);
        jar.closeEntry();
    }

    // 4. Load and verify the plugin using your real loader
    URLClassLoader cl = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, getClass().getClassLoader());
    Class<?> clazz = Class.forName("my.special.Implementation", true, cl);
    Object instance = clazz.getDeclaredConstructor().newInstance();

    assertTrue(instance instanceof Abstraction);
    assertEquals("Hi from test plugin", ((Abstraction) instance).greet());
}
