package com.github.clementherve.intellijjavadependencyupdaterplugin.cache;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class VersionCacheTest {

    private VersionCache cache;

    @Before
    public void setUp() {
        cache = new VersionCache();
    }

    @Test
    public void test_put_and_get_versions() {
        List<String> versions = Arrays.asList("1.0.0", "1.1.0", "2.0.0");

        cache.putVersions("com.example", "artifact", versions);
        List<String> retrieved = cache.getVersions("com.example", "artifact", 30);

        assertNotNull(retrieved);
        assertEquals(3, retrieved.size());
        assertEquals(versions, retrieved);
    }

    @Test
    public void test_get_returns_null_when_not_cached() {
        List<String> retrieved = cache.getVersions("com.example", "not-cached", 30);
        assertNull(retrieved);
    }

    @Test
    public void test_cache_expiration() throws InterruptedException {
        List<String> versions = Arrays.asList("1.0.0", "2.0.0");

        cache.putVersions("com.example", "artifact", versions);

        // Should be cached with 1 second TTL
        List<String> retrieved1 = cache.getVersions("com.example", "artifact", 0); // 0 minutes TTL
        assertNotNull(retrieved1);

        // Wait a bit
        Thread.sleep(100);

        // Should now be expired
        List<String> retrieved2 = cache.getVersions("com.example", "artifact", 0);
        assertNull(retrieved2);
    }

    @Test
    public void test_invalidate_specific_entry() {
        List<String> versions = Arrays.asList("1.0.0");

        cache.putVersions("com.example", "artifact1", versions);
        cache.putVersions("com.example", "artifact2", versions);

        assertEquals(2, cache.size());

        cache.invalidate("com.example", "artifact1");

        assertNull(cache.getVersions("com.example", "artifact1", 30));
        assertNotNull(cache.getVersions("com.example", "artifact2", 30));
        assertEquals(1, cache.size());
    }

    @Test
    public void test_invalidate_all() {
        cache.putVersions("com.example", "artifact1", Arrays.asList("1.0.0"));
        cache.putVersions("com.example", "artifact2", Arrays.asList("2.0.0"));
        cache.putVersions("com.example", "artifact3", Arrays.asList("3.0.0"));

        assertEquals(3, cache.size());

        cache.invalidateAll();

        assertEquals(0, cache.size());
        assertNull(cache.getVersions("com.example", "artifact1", 30));
        assertNull(cache.getVersions("com.example", "artifact2", 30));
        assertNull(cache.getVersions("com.example", "artifact3", 30));
    }

    @Test
    public void test_cache_key_uniqueness() {
        cache.putVersions("com.example", "artifact", Arrays.asList("1.0.0"));
        cache.putVersions("com.other", "artifact", Arrays.asList("2.0.0"));

        List<String> versions1 = cache.getVersions("com.example", "artifact", 30);
        List<String> versions2 = cache.getVersions("com.other", "artifact", 30);

        assertNotNull(versions1);
        assertNotNull(versions2);
        assertEquals("1.0.0", versions1.get(0));
        assertEquals("2.0.0", versions2.get(0));
    }

    @Test
    public void test_concurrent_access() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String artifact = "artifact" + (index % 3);
                List<String> versions = Arrays.asList("1.0." + index);
                cache.putVersions("com.example", artifact, versions);
                cache.getVersions("com.example", artifact, 30);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Should have 3 unique artifacts cached (artifact0, artifact1, artifact2)
        assertTrue(cache.size() <= 3);
    }

    @Test
    public void test_ttl_with_large_value() {
        List<String> versions = Arrays.asList("1.0.0");
        cache.putVersions("com.example", "artifact", versions);

        // With a large TTL, should still be cached
        List<String> retrieved = cache.getVersions("com.example", "artifact", 1440); // 24 hours
        assertNotNull(retrieved);
    }
}
