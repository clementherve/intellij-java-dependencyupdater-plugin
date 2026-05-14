package com.github.clementherve.intellijjavadependencyupdaterplugin.cache;

import com.intellij.openapi.components.Service;
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
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public static VersionCache getInstance(@NotNull Project project) {
        return project.getService(VersionCache.class);
    }

    /**
     * Gets cached versions for a dependency if available and not expired.
     *
     * @param group      the dependency group ID
     * @param artifact   the dependency artifact ID
     * @param ttlMinutes the TTL in minutes
     * @return the cached list of versions, or null if not cached or expired
     */
    @Nullable
    public List<String> getVersions(@NotNull String group, @NotNull String artifact, int ttlMinutes) {
        String key = createKey(group, artifact);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return null;
        }

        long ttlMillis = ttlMinutes * 60L * 1000L;
        long age = System.currentTimeMillis() - entry.timestamp;

        if (age > ttlMillis) {
            cache.remove(key);
            return null;
        }

        return entry.versions;
    }

    /**
     * Puts versions into the cache.
     *
     * @param group    the dependency group ID
     * @param artifact the dependency artifact ID
     * @param versions the list of versions to cache
     */
    public void putVersions(@NotNull String group, @NotNull String artifact, @NotNull List<String> versions) {
        String key = createKey(group, artifact);
        cache.put(key, new CacheEntry(versions, System.currentTimeMillis()));
    }

    /**
     * Invalidates the cache entry for a specific dependency.
     *
     * @param group    the dependency group ID
     * @param artifact the dependency artifact ID
     */
    public void invalidate(@NotNull String group, @NotNull String artifact) {
        String key = createKey(group, artifact);
        cache.remove(key);
    }

    /**
     * Invalidates all cache entries.
     */
    public void invalidateAll() {
        cache.clear();
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
    private record CacheEntry(List<String> versions, long timestamp) {
        private CacheEntry(@NotNull List<String> versions, long timestamp) {
            this.versions = versions;
            this.timestamp = timestamp;
        }
    }
}
