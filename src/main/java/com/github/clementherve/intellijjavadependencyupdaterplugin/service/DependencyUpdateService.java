package com.github.clementherve.intellijjavadependencyupdaterplugin.service;

import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.VersionCache;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicy;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicyEvaluator;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Main service for checking dependency updates.
 * Coordinates the cache, version resolution, and policy evaluation.
 */
@Service(Service.Level.PROJECT)
public final class DependencyUpdateService {

    private static final Logger LOGGER = Logger.getInstance(DependencyUpdateService.class);

    private final Project project;
    private final VersionCache cache;
    private final VersionPolicyEvaluator policyEvaluator;
    private final VersionResolver versionResolver;

    public DependencyUpdateService(@NotNull Project project) {
        this.project = project;
        this.cache = VersionCache.getInstance();
        this.policyEvaluator = new VersionPolicyEvaluator();
        this.versionResolver = new VersionResolver();
    }

    public static DependencyUpdateService getInstance(@NotNull Project project) {
        return project.getService(DependencyUpdateService.class);
    }

    /**
     * Checks if an update is available for the given dependency.
     * WARNING: This may make network calls - do NOT call from EDT or read actions!
     *
     * @param dependency the dependency to check
     * @return the best version candidate, or null if no update is available
     */
    @Nullable
    public VersionCandidate checkForUpdate(@NotNull Dependency dependency) {
        try {
            List<String> versions = fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
            if (versions.isEmpty()) {
                return null;
            }

            return findBestCandidate(dependency, versions);
        } catch (Exception exception) {
            LOGGER.warn("Failed to check for update: " + dependency.getFullCoordinates(), exception);
            return null;
        }
    }

    /**
     * Checks cache only for available update - SAFE to call from EDT/read actions.
     * Never makes network calls.
     *
     * @param dependency the dependency to check
     * @return the best version candidate from cache, or null if not cached
     */
    @Nullable
    public VersionCandidate getFromCache(@NotNull Dependency dependency) {
        List<String> cachedVersions = getCachedVersions(dependency);

        final boolean isNotInCache = cachedVersions == null;
        if (isNotInCache) {
            return null;
        }

        return findBestCandidate(dependency, cachedVersions);
    }

    /**
     * Gets all version candidates from cache - SAFE to call from EDT/read actions.
     * Never makes network calls.
     *
     * @param dependency the dependency to check
     * @return list of all version candidates from cache (sorted descending), or empty list if not cached
     */
    @NotNull
    public List<VersionCandidate> getAllCandidatesFromCache(@NotNull Dependency dependency) {
        List<String> cachedVersions = getCachedVersions(dependency);

        if (cachedVersions == null) {
            return List.of(); // Not in cache
        }

        VersionPolicy policy = getFirstPolicy();
        return policyEvaluator.evaluate(cachedVersions, policy, repositorySourceName(dependency));
    }

    /**
     * Schedules a background task to warm up the cache for a dependency.
     * Safe to call from EDT/read actions.
     *
     * @param dependency the dependency to fetch versions for
     */
    public void scheduleCacheWarmup(@NotNull Dependency dependency) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching versions for " + dependency.artifact(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Fetching versions from repository...");
                    fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
                    // next highlighting pass will show the marker
                } catch (Exception exception) {
                    LOGGER.debug("Background cache warmup failed for " + dependency.getFullCoordinates(), exception);
                }
            }
        });
    }

    /**
     * Gets all available versions for a dependency (uses cache if available).
     *
     * @param group    the dependency group ID
     * @param artifact the dependency artifact ID
     * @return a list of available versions
     */
    @NotNull
    public List<String> fetchVersionsAndSaveThemToCache(@NotNull String group, @NotNull String artifact) throws IOException {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        List<String> cachedVersions = cache.getVersions(group, artifact, settings.getCacheTtlMinutes());
        if (cachedVersions != null) {
            return cachedVersions;
        }

        List<String> versions = versionResolver.fetchVersions(group, artifact);

        if (!versions.isEmpty()) {
            cache.putVersions(group, artifact, versions);
        }

        return versions;
    }

    /**
     * Invalidates the cache for a specific dependency.
     */
    public void invalidateCache(@NotNull String group, @NotNull String artifact) {
        cache.invalidate(group, artifact);
    }

    /**
     * Invalidates all cached version information.
     */
    public void invalidateAllCache() {
        cache.invalidateAll();
    }

    public VersionCandidate forceCheckForUpdate(final Dependency dependency) throws IOException {
        invalidateCache(dependency.group(), dependency.artifact());

        final List<String> versions = fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());

        return findBestCandidate(dependency, versions);
    }

    @Nullable
    private List<String> getCachedVersions(@NotNull Dependency dependency) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        return cache.getVersions(dependency.group(), dependency.artifact(), settings.getCacheTtlMinutes());
    }

    @Nullable
    private VersionCandidate findBestCandidate(@NotNull Dependency dependency, @NotNull List<String> versions) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        VersionPolicy policy = getFirstPolicy();
        String excludeRegex = settings.getVersionFilterRegex();

        return policyEvaluator.findBestCandidate(
                versions,
                dependency.currentVersion(),
                policy,
                repositorySourceName(dependency),
                excludeRegex
        );
    }

    @NotNull
    private String repositorySourceName(@NotNull Dependency dependency) {
        return versionResolver.resolveSource(dependency.group(), dependency.artifact()).getDisplayName();
    }

    /**
     * Gets the first version policy from settings.
     */
    @NotNull
    private VersionPolicy getFirstPolicy() {
        List<VersionPolicy> policies = DependencyUpdaterSettings.getInstance().getVersionPolicies();
        return policies.isEmpty() ? VersionPolicy.createDefaultStablePolicy() : policies.getFirst();
    }
}
