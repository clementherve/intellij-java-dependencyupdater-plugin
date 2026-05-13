package com.github.clementherve.intellijjavadependencyupdaterplugin.model;

import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SemanticVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a version candidate that passed policy filters.
 */
public class VersionCandidate implements Comparable<VersionCandidate> {
    private final String version;
    private final SemanticVersion semanticVersion;
    private final String repositorySource;

    public VersionCandidate(@NotNull String version,
                           @Nullable SemanticVersion semanticVersion,
                           @NotNull String repositorySource) {
        this.version = version;
        this.semanticVersion = semanticVersion;
        this.repositorySource = repositorySource;
    }

    @NotNull
    public String getVersion() {
        return version;
    }

    @Nullable
    public SemanticVersion getSemanticVersion() {
        return semanticVersion;
    }

    @NotNull
    public String getRepositorySource() {
        return repositorySource;
    }

    @Override
    public int compareTo(@NotNull VersionCandidate other) {
        // If both have semantic versions, compare them
        if (this.semanticVersion != null && other.semanticVersion != null) {
            return this.semanticVersion.compareTo(other.semanticVersion);
        }
        // If only one has semantic version, it comes first
        if (this.semanticVersion != null) return 1;
        if (other.semanticVersion != null) return -1;
        // Otherwise, lexicographic comparison
        return this.version.compareTo(other.version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionCandidate that = (VersionCandidate) o;
        return Objects.equals(version, that.version) &&
               Objects.equals(repositorySource, that.repositorySource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, repositorySource);
    }

    @Override
    public String toString() {
        return version + " (" + repositorySource + ")";
    }
}
