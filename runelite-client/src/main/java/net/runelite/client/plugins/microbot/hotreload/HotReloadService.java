package net.runelite.client.plugins.microbot.hotreload;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.PluginJarClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Replaces one generation of a development external-plugin JAR with the next. */
final class HotReloadService implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(HotReloadService.class);
    private static final long POLL_MILLIS = 750L;

    private final Path devJar;
    private final PluginManager pluginManager;
    private final EventBus eventBus;
    private final ClassLoader parent;
    private volatile boolean running;
    private JarFingerprint lastSeen;
    private Thread watcher;
    private PluginJarClassLoader currentLoader;
    private List<Plugin> currentPlugins = Collections.emptyList();

    HotReloadService(Path devJar, PluginManager pluginManager, EventBus eventBus, ClassLoader parent)
    {
        this.devJar = devJar;
        this.pluginManager = pluginManager;
        this.eventBus = eventBus;
        this.parent = parent;
    }

    void start()
    {
        running = true;
        watcher = new Thread(this::watch, "microbot-hotreload-watch");
        watcher.setDaemon(true);
        watcher.start();
        LOG.info("Watching development plugin JAR {}", devJar);
    }

    private void watch()
    {
        while (running && !Thread.currentThread().isInterrupted())
        {
            try
            {
                Thread.sleep(POLL_MILLIS);
                if (!Files.isRegularFile(devJar))
                {
                    continue;
                }

                JarFingerprint before = JarFingerprint.read(devJar);
                if (before.equals(lastSeen))
                {
                    continue;
                }

                // Gradle may still be replacing the JAR. Only load a stable generation.
                Thread.sleep(POLL_MILLIS);
                if (!before.equals(JarFingerprint.read(devJar)))
                {
                    continue;
                }

                lastSeen = before;
                reload();
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (Exception | LinkageError ex)
            {
                LOG.warn("Development plugin reload failed; the previous generation remains active", ex);
            }
        }
    }

    private void reload() throws Exception
    {
        Candidate candidate = openCandidate();
        try
        {
            onEventDispatchThread(() -> install(candidate));
        }
        catch (Exception | LinkageError ex)
        {
            candidate.loader.close();
            throw ex;
        }
    }

    private Candidate openCandidate() throws IOException, ClassNotFoundException
    {
        PluginJarClassLoader loader = new PluginJarClassLoader(devJar.toFile(), parent);
        try
        {
            List<Class<?>> pluginClasses = new ArrayList<>();
            try (JarFile jar = new JarFile(devJar.toFile()))
            {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements())
                {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.equals("module-info.class"))
                    {
                        continue;
                    }

                    Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                    if (isPluginClass(type))
                    {
                        if (type.getClassLoader() != loader)
                        {
                            throw new IllegalStateException(type.getName() + " is already on the client classpath");
                        }
                        pluginClasses.add(type);
                    }
                }
            }

            pluginClasses.sort(Comparator.comparing(Class::getName));
            if (pluginClasses.isEmpty())
            {
                throw new IllegalStateException("No @PluginDescriptor plugin classes found in " + devJar);
            }
            return new Candidate(loader, pluginClasses);
        }
        catch (IOException | ClassNotFoundException | RuntimeException | LinkageError ex)
        {
            loader.close();
            throw ex;
        }
    }

    private static boolean isPluginClass(Class<?> type)
    {
        return type.getSuperclass() == Plugin.class
                && type.isAnnotationPresent(PluginDescriptor.class)
                && !Modifier.isAbstract(type.getModifiers());
    }

    private void install(Candidate candidate) throws Exception
    {
        if (!running)
        {
            candidate.loader.close();
            return;
        }

        List<Plugin> previousPlugins = currentPlugins;
        PluginJarClassLoader previousLoader = currentLoader;
        stopPlugins(previousPlugins);

        List<Plugin> incoming = Collections.emptyList();
        try
        {
            incoming = pluginManager.loadPlugins(candidate.pluginClasses, null);
            pluginManager.loadDefaultPluginConfiguration(incoming);
            startEnabledPlugins(incoming);
        }
        catch (Exception | LinkageError ex)
        {
            stopPlugins(incoming);
            restartEnabledPlugins(previousPlugins);
            throw ex;
        }

        currentPlugins = incoming;
        currentLoader = candidate.loader;
        closeQuietly(previousLoader);
        eventBus.post(new ExternalPluginsChanged());
        LOG.info("Reloaded {} plugin(s) from {}", incoming.size(), devJar.getFileName());
    }

    private void startEnabledPlugins(List<Plugin> plugins) throws PluginInstantiationException
    {
        for (Plugin plugin : plugins)
        {
            if (pluginManager.isPluginEnabled(plugin))
            {
                pluginManager.startPlugin(plugin);
            }
        }
    }

    private void restartEnabledPlugins(List<Plugin> plugins)
    {
        try
        {
            for (Plugin plugin : plugins)
            {
                pluginManager.add(plugin);
            }
            startEnabledPlugins(plugins);
        }
        catch (PluginInstantiationException ex)
        {
            LOG.error("Could not restore the previous development plugin generation", ex);
        }
    }

    private void stopPlugins(List<Plugin> plugins)
    {
        List<Plugin> reversed = new ArrayList<>(plugins);
        Collections.reverse(reversed);
        for (Plugin plugin : reversed)
        {
            try
            {
                pluginManager.stopPlugin(plugin);
            }
            catch (Exception ex)
            {
                LOG.warn("Could not stop {} cleanly", plugin.getClass().getName(), ex);
            }
            pluginManager.remove(plugin);
        }
    }

    private void stopCurrent()
    {
        stopPlugins(currentPlugins);
        currentPlugins = Collections.emptyList();
        closeQuietly(currentLoader);
        currentLoader = null;
    }

    private static void closeQuietly(PluginJarClassLoader loader)
    {
        if (loader == null)
        {
            return;
        }
        try
        {
            loader.close();
        }
        catch (IOException ex)
        {
            LOG.warn("Could not close a development plugin classloader", ex);
        }
    }

    private static void onEventDispatchThread(ThrowingRunnable task) throws Exception
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            task.run();
            return;
        }

        FutureTask<Void> future = new FutureTask<>(() ->
        {
            task.run();
            return null;
        });
        SwingUtilities.invokeAndWait(future);
        try
        {
            future.get();
        }
        catch (ExecutionException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            throw (Exception) cause;
        }
    }

    @Override
    public void close()
    {
        running = false;
        if (watcher != null)
        {
            watcher.interrupt();
            watcher = null;
        }
        try
        {
            onEventDispatchThread(this::stopCurrent);
        }
        catch (Exception ex)
        {
            LOG.warn("Could not unload the development plugin generation", ex);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static final class Candidate
    {
        private final PluginJarClassLoader loader;
        private final List<Class<?>> pluginClasses;

        private Candidate(PluginJarClassLoader loader, List<Class<?>> pluginClasses)
        {
            this.loader = loader;
            this.pluginClasses = pluginClasses;
        }
    }

    private static final class JarFingerprint
    {
        private final long lastModified;
        private final long size;

        private JarFingerprint(long lastModified, long size)
        {
            this.lastModified = lastModified;
            this.size = size;
        }

        private static JarFingerprint read(Path path) throws IOException
        {
            return new JarFingerprint(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof JarFingerprint))
            {
                return false;
            }
            JarFingerprint fingerprint = (JarFingerprint) other;
            return lastModified == fingerprint.lastModified && size == fingerprint.size;
        }

        @Override
        public int hashCode()
        {
            return Long.hashCode(lastModified) * 31 + Long.hashCode(size);
        }
    }
}
