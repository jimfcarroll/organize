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

