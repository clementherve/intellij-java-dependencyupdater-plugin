package com.github.clementherve.intellijjavadependencyupdaterplugin.services;

import com.github.clementherve.intellijjavadependencyupdaterplugin.cache.VersionCache;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicyEvaluator;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.MavenCentralClient;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.NexusClient;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.VersionRepository;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
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
 * Coordinates cache, repository access, and policy evaluation.
 */
@Service(Service.Level.PROJECT)
public final class DependencyUpdateService {

    private static final Logger LOG = Logger.getInstance(DependencyUpdateService.class);

    private final Project project;
    private final VersionCache cache;
    private final VersionPolicyEvaluator policyEvaluator;
    private final MavenCentralClient mavenCentralClient;

    public DependencyUpdateService(@NotNull Project project) {
        this.project = project;
        this.cache = VersionCache.getInstance(project);
        this.policyEvaluator = new VersionPolicyEvaluator();
        this.mavenCentralClient = new MavenCentralClient();
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
    public VersionCandidate checkForUpdate(@NotNull DependencyInfo dependency) {
        try {
            List<String> versions = getVersions(dependency.getGroup(), dependency.getArtifact());
            if (versions.isEmpty()) {
                return null;
            }

            VersionPolicy policy = getFirstPolicy();
            return policyEvaluator.findBestCandidate(
                versions,
                dependency.getCurrentVersion(),
                policy,
                getRepositorySource()
            );
        } catch (Exception e) {
            LOG.warn("Failed to check for update: " + dependency.getFullCoordinates(), e);
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
    public VersionCandidate checkForUpdateFromCache(@NotNull DependencyInfo dependency) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Check cache only - never fetch from network
        List<String> cachedVersions = cache.getVersions(
            dependency.getGroup(),
            dependency.getArtifact(),
            settings.getCacheTtlMinutes()
        );

        if (cachedVersions == null) {
            return null; // Not in cache
        }

        VersionPolicy policy = getFirstPolicy();
        return policyEvaluator.findBestCandidate(
            cachedVersions,
            dependency.getCurrentVersion(),
            policy,
            getRepositorySource()
        );
    }

    /**
     * Gets all version candidates from cache - SAFE to call from EDT/read actions.
     * Never makes network calls.
     *
     * @param dependency the dependency to check
     * @return list of all version candidates from cache (sorted descending), or empty list if not cached
     */
    @NotNull
    public List<VersionCandidate> getAllCandidatesFromCache(@NotNull DependencyInfo dependency) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Check cache only - never fetch from network
        List<String> cachedVersions = cache.getVersions(
            dependency.getGroup(),
            dependency.getArtifact(),
            settings.getCacheTtlMinutes()
        );

        if (cachedVersions == null) {
            return List.of(); // Not in cache
        }

        VersionPolicy policy = getFirstPolicy();
        return policyEvaluator.evaluate(cachedVersions, policy, getRepositorySource());
    }

    /**
     * Schedules a background task to warm up the cache for a dependency.
     * Safe to call from EDT/read actions.
     *
     * @param dependency the dependency to fetch versions for
     */
    public void scheduleCacheWarmup(@NotNull DependencyInfo dependency) {
        LOG.debug("Scheduling cache warmup for dependency: " + dependency.getFullCoordinates());

        // Show progress in the background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching versions for " + dependency.getArtifact(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Fetching versions from repository...");
                    getVersions(dependency.getGroup(), dependency.getArtifact());
                    // This will populate the cache, and next highlighting pass will show the marker
                } catch (Exception e) {
                    LOG.debug("Background cache warmup failed for " + dependency.getFullCoordinates(), e);
                }
            }
        });
    }

    /**
     * Gets all available versions for a dependency (uses cache if available).
     *
     * @param group the dependency group ID
     * @param artifact the dependency artifact ID
     * @return a list of available versions
     */
    @NotNull
    public List<String> getVersions(@NotNull String group, @NotNull String artifact) throws IOException {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Check cache first
        List<String> cachedVersions = cache.getVersions(group, artifact, settings.getCacheTtlMinutes());
        if (cachedVersions != null) {
            LOG.debug("Using cached versions for " + group + ":" + artifact);
            return cachedVersions;
        }

        // Fetch from repositories
        List<String> versions = fetchVersionsFromRepositories(group, artifact);

        // Cache the result
        if (!versions.isEmpty()) {
            cache.putVersions(group, artifact, versions);
        }

        return versions;
    }

    /**
     * Fetches versions from configured repositories (Nexus first, then Maven Central if fallback is enabled).
     */
    @NotNull
    private List<String> fetchVersionsFromRepositories(@NotNull String group, @NotNull String artifact) throws IOException {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Try Nexus first if configured
        if (!settings.getNexusBaseUrl().isEmpty()) {
            try {
                VersionRepository nexusClient = createNexusClient();
                List<String> versions = nexusClient.fetchVersions(group, artifact);
                if (!versions.isEmpty()) {
                    LOG.info("Fetched " + versions.size() + " versions from Nexus for " + group + ":" + artifact);
                    return versions;
                }
            } catch (IOException e) {
                LOG.warn("Nexus fetch failed for " + group + ":" + artifact + ", error: " + e.getMessage());
                if (!settings.isFallbackToMavenCentral()) {
                    throw e;
                }
            }
        }

        // Fallback to Maven Central
        LOG.info("Fetching from Maven Central for " + group + ":" + artifact);
        List<String> versions = mavenCentralClient.fetchVersions(group, artifact);
        LOG.info("Fetched " + versions.size() + " versions from Maven Central for " + group + ":" + artifact);
        return versions;
    }

    /**
     * Creates a Nexus client with current settings.
     */
    @NotNull
    private VersionRepository createNexusClient() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);
        return new NexusClient(
            settings.getNexusBaseUrl(),
            settings.getNexusUsername(),
            settings.getNexusPassword()
        );
    }

    /**
     * Gets the first version policy from settings.
     */
    @NotNull
    private VersionPolicy getFirstPolicy() {
        List<VersionPolicy> policies = DependencyUpdaterSettings.getInstance(project).getVersionPolicies();
        return policies.isEmpty() ? VersionPolicy.createDefaultStablePolicy() : policies.get(0);
    }

    /**
     * Gets the repository source name for display purposes.
     */
    @NotNull
    private String getRepositorySource() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);
        if (!settings.getNexusBaseUrl().isEmpty()) {
            return "Nexus";
        }
        return "Maven Central";
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
}
