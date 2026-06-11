package com.github.clementherve.intellijjavadependencyupdaterplugin.services;

import com.github.clementherve.intellijjavadependencyupdaterplugin.cache.VersionCache;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicyEvaluator;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.GradlePluginPortalClient;
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
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

    private static final Logger LOGGER = Logger.getInstance(DependencyUpdateService.class);

    private final Project project;
    private final VersionCache cache;
    private final VersionPolicyEvaluator policyEvaluator;
    private final MavenCentralClient mavenCentralClient;
    private final GradlePluginPortalClient pluginPortalClient;

    public DependencyUpdateService(@NotNull Project project) {
        this.project = project;
        this.cache = VersionCache.getInstance();
        this.policyEvaluator = new VersionPolicyEvaluator();
        this.mavenCentralClient = new MavenCentralClient();
        this.pluginPortalClient = new GradlePluginPortalClient();
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
            List<String> versions = fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
            if (versions.isEmpty()) {
                return null;
            }

            DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
            VersionPolicy policy = getFirstPolicy();
            String excludeRegex = settings.getVersionFilterRegex();

            return policyEvaluator.findBestCandidate(
                    versions,
                    dependency.currentVersion(),
                    policy,
                    getRepositorySource(dependency.group(), dependency.artifact()),
                    excludeRegex
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to check for update: " + dependency.getFullCoordinates(), e);
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
    public VersionCandidate getFromCache(@NotNull DependencyInfo dependency) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        List<String> cachedVersions = cache.getVersions(
                dependency.group(),
                dependency.artifact(),
                settings.getCacheTtlMinutes()
        );

        final boolean isNotInCache = cachedVersions == null;
        if (isNotInCache) {
            return null;
        }

        VersionPolicy policy = getFirstPolicy();
        String excludeRegex = settings.getVersionFilterRegex();

        return policyEvaluator.findBestCandidate(
                cachedVersions,
                dependency.currentVersion(),
                policy,
                getRepositorySource(dependency.group(), dependency.artifact()),
                excludeRegex
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
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        List<String> cachedVersions = cache.getVersions(
                dependency.group(),
                dependency.artifact(),
                settings.getCacheTtlMinutes()
        );

        if (cachedVersions == null) {
            return List.of(); // Not in cache
        }

        VersionPolicy policy = getFirstPolicy();
        return policyEvaluator.evaluate(cachedVersions, policy, getRepositorySource(dependency.group(), dependency.artifact()));
    }

    /**
     * Schedules a background task to warm up the cache for a dependency.
     * Safe to call from EDT/read actions.
     *
     * @param dependency the dependency to fetch versions for
     */
    public void scheduleCacheWarmup(@NotNull DependencyInfo dependency) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching versions for " + dependency.artifact(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Fetching versions from repository...");
                    fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
                    // next highlighting pass will show the marker
                } catch (Exception e) {
                    LOGGER.debug("Background cache warmup failed for " + dependency.getFullCoordinates(), e);
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


        List<String> versions = fetchVersionsFromRepositories(group, artifact);

        if (!versions.isEmpty()) {
            cache.putVersions(group, artifact, versions);
        }

        return versions;
    }

    /**
     * Fetches versions from configured repositories.
     * For plugins (empty group), uses Gradle Plugin Portal.
     * For regular dependencies, uses Nexus first, then Maven Central if fallback is enabled.
     */
    @NotNull
    private List<String> fetchVersionsFromRepositories(@NotNull String group, @NotNull String artifact) throws IOException {
        if (group.isEmpty()) {
            return pluginPortalClient.fetchVersions(group, artifact);
        }

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        final boolean isNexusConfigured = StringUtils.isNotBlank(settings.getNexusBaseUrl());

        if (isNexusConfigured) {
            String nexusDependencyRegex = settings.getNexusDependencyRegex();
            boolean shouldUseNexus = nexusDependencyRegex.isEmpty() || (group + ":" + artifact).matches(nexusDependencyRegex);

            if (shouldUseNexus) {
                try {
                    VersionRepository nexusClient = createNexusClient();
                    List<String> versions = nexusClient.fetchVersions(group, artifact);

                    if (CollectionUtils.isNotEmpty(versions)) {
                        return versions;
                    }
                } catch (IOException e) {
                    LOGGER.error("Nexus fetch failed for " + group + ":" + artifact + ", error: " + e.getMessage());
                    if (!settings.isFallbackToMavenCentral()) {
                        throw e;
                    }
                }
            }
        }

        return mavenCentralClient.fetchVersions(group, artifact);
    }

    /**
     * Creates a Nexus client with current settings.
     */
    @NotNull
    private VersionRepository createNexusClient() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
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
        List<VersionPolicy> policies = DependencyUpdaterSettings.getInstance().getVersionPolicies();
        return policies.isEmpty() ? VersionPolicy.createDefaultStablePolicy() : policies.getFirst();
    }

    @NotNull
    private String getRepositorySource(@NotNull String group, @NotNull String artifact) {
        if (group.isEmpty()) {
            return "Gradle Plugin Portal"; // todo: create a enum
        }

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        if (StringUtils.isNotBlank(settings.getNexusBaseUrl())) {
            String nexusDependencyRegex = settings.getNexusDependencyRegex();

            boolean matchesNexusFilter = nexusDependencyRegex.isEmpty() || (group + ":" + artifact).matches(nexusDependencyRegex);

            if (matchesNexusFilter) {
                return "Nexus"; // todo: create a enum
            }
        }

        return "Maven Central"; // todo: create a enum
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

    public VersionCandidate forceCheckForUpdate(final DependencyInfo dependency) throws IOException {

        invalidateCache(dependency.group(), dependency.artifact());

        final List<String> versions = fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        VersionPolicy policy = getFirstPolicy();
        String excludeRegex = settings.getVersionFilterRegex();

        return policyEvaluator.findBestCandidate(
                versions,
                dependency.currentVersion(),
                policy,
                getRepositorySource(dependency.group(), dependency.artifact()),
                excludeRegex
        );
    }
}
