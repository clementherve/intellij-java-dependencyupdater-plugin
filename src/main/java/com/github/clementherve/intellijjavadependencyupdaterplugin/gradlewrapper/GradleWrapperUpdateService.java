package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicy;
import com.github.clementherve.intellijjavadependencyupdaterplugin.policy.VersionPolicyEvaluator;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.VersionCache;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Checks the current project's Gradle wrapper version against the latest stable Gradle release.
 */
@Service(Service.Level.PROJECT)
public final class GradleWrapperUpdateService {

    // VersionCache is keyed by group:artifact; there's no real Maven coordinate for the Gradle
    // wrapper itself, so a fixed synthetic key is used to get the same TTL-based caching.
    private static final String CACHE_GROUP = "gradle";
    private static final String CACHE_ARTIFACT = "wrapper";

    private final Project project;
    private final VersionCache cache;
    private final VersionPolicyEvaluator policyEvaluator;
    private final GradleVersionRepository repository;

    public GradleWrapperUpdateService(@NotNull Project project) {
        this.project = project;
        this.cache = VersionCache.getInstance();
        this.policyEvaluator = new VersionPolicyEvaluator();
        this.repository = new GradleVersionRepository();
    }

    public static GradleWrapperUpdateService getInstance(@NotNull Project project) {
        return project.getService(GradleWrapperUpdateService.class);
    }

    /**
     * Locates and parses every {@code gradle-wrapper.properties} file in the current project -
     * a project may contain more than one, e.g. separate {@code app/} and {@code test/} modules
     * each with their own wrapper.
     * Safe to call from EDT/read actions - performs no network call.
     */
    @NotNull
    public List<GradleWrapperInfo> findWrapperInfos() {
        return GradleWrapperLocator.findWrapperInfos(project);
    }

    /**
     * Checks whether a newer stable Gradle version is available for the given wrapper.
     * The set of published Gradle versions is the same regardless of which wrapper file is being
     * checked, so it's fetched once and cached across every wrapper in the project.
     * WARNING: This may make network calls - do NOT call from EDT or read actions!
     *
     * @return the best version candidate, or null if the wrapper is already up to date
     */
    @Nullable
    public VersionCandidate checkForUpdate(@NotNull GradleWrapperInfo info) throws IOException {
        List<String> versions = fetchVersionsAndSaveThemToCache();
        if (versions.isEmpty()) {
            return null;
        }

        VersionPolicy stableOnly = VersionPolicy.createDefaultStablePolicy();
        return policyEvaluator.findBestCandidate(versions, info.currentVersion(), stableOnly, "Gradle");
    }

    /**
     * Invalidates the shared cache of published Gradle versions, so the next {@link #checkForUpdate}
     * call re-fetches instead of reusing a stale result. Call this once before force-refreshing
     * every wrapper in the project, rather than per wrapper file.
     */
    public void invalidateCache() {
        cache.invalidate(CACHE_GROUP, CACHE_ARTIFACT);
    }

    @NotNull
    private List<String> fetchVersionsAndSaveThemToCache() throws IOException {
        int ttlMinutes = DependencyUpdaterSettings.getInstance().getCacheTtlMinutes();
        List<String> cachedVersions = cache.getVersions(CACHE_GROUP, CACHE_ARTIFACT, ttlMinutes);
        if (cachedVersions != null) {
            return cachedVersions;
        }

        List<String> versions = repository.fetchVersions();
        if (!versions.isEmpty()) {
            cache.putVersions(CACHE_GROUP, CACHE_ARTIFACT, versions);
        }
        return versions;
    }
}
