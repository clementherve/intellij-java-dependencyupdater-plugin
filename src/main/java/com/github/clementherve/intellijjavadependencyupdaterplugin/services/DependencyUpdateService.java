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
