package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Interface for fetching available versions from a repository.
 */
public interface VersionRepository {

    /**
     * Fetches all available versions for a given dependency.
     *
     * @param group    the dependency group ID
     * @param artifact the dependency artifact ID
     * @return a list of version strings
     * @throws IOException if the fetch fails
     */
    @NotNull
    List<String> fetchVersions(@NotNull String group, @NotNull String artifact) throws IOException;

    /**
     * Gets the human-readable name of this repository source.
     *
     * @return the repository source name
     */
    @NotNull
    String getSourceName();
}
