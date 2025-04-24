public class PluginInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        ConfigurableEnvironment env = ctx.getEnvironment();

        String className = env.getProperty("abstraction.implementation");
        String jarPath = env.getProperty("abstraction.jar-path");

        if (className == null || jarPath == null) {
            throw new IllegalStateException("Plugin class name and jar path must be set in configuration.");
        }

        try {
            URL jarUrl = new File(jarPath).toURI().toURL();
            URLClassLoader pluginLoader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());

            Class<?> pluginClass = Class.forName(className, true, pluginLoader);
            Object pluginInstance = pluginClass.getDeclaredConstructor().newInstance();

            ((GenericApplicationContext) ctx).registerBean((Class<Object>) pluginClass, () -> pluginInstance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin class", e);
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
