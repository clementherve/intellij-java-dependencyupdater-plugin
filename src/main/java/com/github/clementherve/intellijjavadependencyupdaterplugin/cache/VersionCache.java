package com.github.clementherve.intellijjavadependencyupdaterplugin.cache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for dependency version information with TTL support.
 */
@Service(Service.Level.PROJECT)
public final class VersionCache {

    private static final Logger LOG = Logger.getInstance(VersionCache.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Project project;

    public VersionCache(Project project) {
        this.project = project;
    }

    public static VersionCache getInstance(@NotNull Project project) {
        return project.getService(VersionCache.class);
    }

    /**
     * Gets cached versions for a dependency if available and not expired.
     *
     * @param group the dependency group ID
     * @param artifact the dependency artifact ID
     * @param ttlMinutes the TTL in minutes
     * @return the cached list of versions, or null if not cached or expired
     */
    @Nullable
    public List<String> getVersions(@NotNull String group, @NotNull String artifact, int ttlMinutes) {
        String key = createKey(group, artifact);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            LOG.debug("Cache miss for " + key);
            return null;
        }

        long ttlMillis = ttlMinutes * 60L * 1000L;
        long age = System.currentTimeMillis() - entry.timestamp;

        if (age > ttlMillis) {
            LOG.debug("Cache expired for " + key + " (age: " + age + "ms, TTL: " + ttlMillis + "ms)");
            cache.remove(key);
            return null;
        }

        LOG.debug("Cache hit for " + key);
        return entry.versions;
    }

    /**
     * Puts versions into the cache.
     *
     * @param group the dependency group ID
     * @param artifact the dependency artifact ID
     * @param versions the list of versions to cache
     */
    public void putVersions(@NotNull String group, @NotNull String artifact, @NotNull List<String> versions) {
        String key = createKey(group, artifact);
        cache.put(key, new CacheEntry(versions, System.currentTimeMillis()));
        LOG.debug("Cached " + versions.size() + " versions for " + key);
    }

    /**
     * Invalidates the cache entry for a specific dependency.
     *
     * @param group the dependency group ID
     * @param artifact the dependency artifact ID
     */
    public void invalidate(@NotNull String group, @NotNull String artifact) {
        String key = createKey(group, artifact);
        cache.remove(key);
        LOG.debug("Invalidated cache for " + key);
    }

    /**
     * Invalidates all cache entries.
     */
    public void invalidateAll() {
        int size = cache.size();
        cache.clear();
        LOG.info("Invalidated all cache entries (count: " + size + ")");
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of entries in the cache
     */
    public int size() {
        return cache.size();
    }

    @NotNull
    private String createKey(@NotNull String group, @NotNull String artifact) {
        return group + ":" + artifact;
    }

    /**
     * Cache entry with timestamp for TTL checking.
     */
    private static class CacheEntry {
        final List<String> versions;
        final long timestamp;

        CacheEntry(@NotNull List<String> versions, long timestamp) {
            this.versions = versions;
            this.timestamp = timestamp;
        }
    }
}
