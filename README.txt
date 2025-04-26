static Path pluginDir;

@BeforeAll
static void setupPluginJar() throws Exception {
    // 1. Create a fixed directory under target/
    pluginDir = Paths.get("target", "test-plugins", "test-plugin");
    Files.createDirectories(pluginDir); // Safe even if it already exists

    // 2. (Optional) Clean up any previous contents (fresh run)
    if (Files.exists(pluginDir)) {
        try (Stream<Path> walk = Files.walk(pluginDir)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        Files.createDirectories(pluginDir);
    }

    // 3. Create the Java source file
    Path sourceFile = pluginDir.resolve("my/special/Implementation.java");
    Files.createDirectories(sourceFile.getParent());

    String code = """
        package my.special;

        import com.example.core.Abstraction;

        public class Implementation implements Abstraction {
            public String greet() { return "Hello from test plugin!"; }
        }
    """;
    Files.writeString(sourceFile, code);

    // 4. Compile it
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Compiler not available — use a full JDK, not JRE");
    int result = compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", pluginDir.toString(),
            sourceFile.toString());
    assertEquals(0, result, "Compilation failed");

    // 5. Create plugin.jar in fixed location
    Path jarPath = pluginDir.resolve("plugin.jar");
    try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
        Path classFile = pluginDir.resolve("my/special/Implementation.class");
        jarOut.putNextEntry(new ZipEntry("my/special/Implementation.class"));
        Files.copy(classFile, jarOut);
        jarOut.closeEntry();
    }

    // 6. Set up for the test initializer
    TestPluginInitializer.testPluginJarPath = jarPath.toAbsolutePath().toString();
}


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

public class TestPluginInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static String testPluginJarPath;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (testPluginJarPath == null) {
            throw new IllegalStateException("Test plugin jar path must be set before Spring context initialization!");
        }

        try {
            PluginLoader.injectJarIntoClassLoader(testPluginJarPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject test plugin jar", e);
        }
    }
}


@SpringBootTest(
    classes = App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = {TestPluginInitializer.class})
@ActiveProfiles("test")
public class PluginIntegrationTest {

    @Autowired
    Abstraction abstraction;

    @BeforeAll
    static void setupPluginJar() throws Exception {
        Path tempDir = Files.createTempDirectory("plugin-test");

        Path sourceFile = tempDir.resolve("my/special/Implementation.java");
        Files.createDirectories(sourceFile.getParent());

        String code = """
            package my.special;

            import com.example.core.Abstraction;

            public class Implementation implements Abstraction {
                public String greet() { return "Hello from test plugin!"; }
            }
        """;
        Files.writeString(sourceFile, code);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Compiler not available — you must use a JDK, not a JRE");
        int result = compiler.run(null, null, null,
                "-classpath", System.getProperty("java.class.path"),
                "-d", tempDir.toString(),
                sourceFile.toString());
        assertEquals(0, result, "Compilation failed");

        Path jarPath = tempDir.resolve("plugin.jar");
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
            Path classFile = tempDir.resolve("my/special/Implementation.class");
            jarOut.putNextEntry(new ZipEntry("my/special/Implementation.class"));
            Files.copy(classFile, jarOut);
            jarOut.closeEntry();
        }

        // Important: set static field BEFORE Spring context starts
        TestPluginInitializer.testPluginJarPath = jarPath.toAbsolutePath().toString();
    }

    @Test
    void pluginShouldBeInjected() {
        assertNotNull(abstraction);
        assertEquals("Hello from test plugin!", abstraction.greet());
    }
}
