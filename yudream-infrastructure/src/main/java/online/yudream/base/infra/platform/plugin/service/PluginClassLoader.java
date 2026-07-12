package online.yudream.base.infra.platform.plugin.service;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

final class PluginClassLoader extends URLClassLoader {

    private final List<ClassLoader> dependencyClassLoaders;

    PluginClassLoader(URL[] urls, ClassLoader parent, List<? extends ClassLoader> dependencyClassLoaders) {
        super(urls, parent);
        this.dependencyClassLoaders = List.copyOf(dependencyClassLoaders);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = loadFromParent(name);
            }
            if (loaded == null) {
                loaded = loadFromDependencies(name);
            }
            if (loaded == null) {
                loaded = findClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> loadFromParent(String name) {
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Class<?> loadFromDependencies(String name) {
        for (ClassLoader classLoader : dependencyClassLoaders) {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                // Try the next declared dependency before resolving from this plugin JAR.
            }
        }
        return null;
    }
}
