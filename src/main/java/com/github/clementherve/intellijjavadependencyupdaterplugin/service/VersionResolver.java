package com.github.clementherve.intellijjavadependencyupdaterplugin.service;

import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.GradlePluginPortalRepository;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.MavenCentralRepository;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.NexusRepository;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.RepositorySource;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Decides which repository a dependency's versions come from and fetches them.
 * <ul>
 *     <li>Plugins (empty group) resolve from the Gradle Plugin Portal.</li>
 *     <li>Regular dependencies resolve from Nexus when it is configured and the dependency
 *     matches the configured filter, otherwise (or as a fallback) from Maven Central.</li>
 * </ul>
 * The same Nexus-filter decision drives both the fetch and the {@link RepositorySource}
 * reported for display, so the two can never disagree.
 */
final class VersionResolver {

    private static final Logger LOGGER = Logger.getInstance(VersionResolver.class);

    private final MavenCentralRepository mavenCentral = new MavenCentralRepository();
    private final GradlePluginPortalRepository pluginPortal = new GradlePluginPortalRepository();

    /**
     * Fetches all available versions for a dependency from the appropriate repository.
     */
    @NotNull
    List<String> fetchVersions(@NotNull String group, @NotNull String artifact) throws IOException {
        if (group.isEmpty()) {
            return pluginPortal.fetchVersions(group, artifact);
        }

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        if (shouldUseNexus(group, artifact, settings)) {
            try {
                List<String> versions = createNexusRepository(settings).fetchVersions(group, artifact);
                if (CollectionUtils.isNotEmpty(versions)) {
                    return versions;
                }
            } catch (IOException exception) {
                LOGGER.error("Nexus fetch failed for " + group + ":" + artifact + ", error: " + exception.getMessage());
                if (!settings.isFallbackToMavenCentral()) {
                    throw exception;
                }
            }
        }

        return mavenCentral.fetchVersions(group, artifact);
    }

    /**
     * Returns the repository source reported for display for the given dependency.
     */
    @NotNull
    RepositorySource resolveSource(@NotNull String group, @NotNull String artifact) {
        if (group.isEmpty()) {
            return RepositorySource.GRADLE_PLUGIN_PORTAL;
        }

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        if (shouldUseNexus(group, artifact, settings)) {
            return RepositorySource.NEXUS;
        }

        return RepositorySource.MAVEN_CENTRAL;
    }

    private boolean shouldUseNexus(@NotNull String group, @NotNull String artifact,
                                   @NotNull DependencyUpdaterSettings settings) {
        if (StringUtils.isBlank(settings.getNexusBaseUrl())) {
            return false;
        }
        String nexusDependencyRegex = settings.getNexusDependencyRegex();
        return nexusDependencyRegex.isEmpty() || (group + ":" + artifact).matches(nexusDependencyRegex);
    }

    @NotNull
    private NexusRepository createNexusRepository(@NotNull DependencyUpdaterSettings settings) {
        return new NexusRepository(
                settings.getNexusBaseUrl(),
                settings.getNexusUsername(),
                settings.getNexusPassword()
        );
    }
}
