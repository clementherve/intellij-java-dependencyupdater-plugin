package com.github.clementherve.intellijjavadependencyupdaterplugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a version policy with include/exclude patterns.
 */
public class VersionPolicy {
    private final String name;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    public VersionPolicy(@NotNull String name,
                        @NotNull List<String> includePatterns,
                        @NotNull List<String> excludePatterns) {
        this.name = name;
        this.includePatterns = new ArrayList<>(includePatterns);
        this.excludePatterns = new ArrayList<>(excludePatterns);
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public List<String> getIncludePatterns() {
        return Collections.unmodifiableList(includePatterns);
    }

    @NotNull
    public List<String> getExcludePatterns() {
        return Collections.unmodifiableList(excludePatterns);
    }

    /**
     * Creates the default "Stable only" policy that excludes pre-release versions.
     */
    @NotNull
    public static VersionPolicy createDefaultStablePolicy() {
        List<String> includePatterns = Collections.singletonList("^\\d+\\.\\d+(\\.\\d+)?.*$");
        List<String> excludePatterns = Collections.singletonList("(?i).*(alpha|beta|rc|snapshot|milestone|m\\d+).*");
        return new VersionPolicy("Stable only", includePatterns, excludePatterns);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionPolicy that = (VersionPolicy) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(includePatterns, that.includePatterns) &&
               Objects.equals(excludePatterns, that.excludePatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, includePatterns, excludePatterns);
    }

    @Override
    public String toString() {
        return "VersionPolicy{" +
               "name='" + name + '\'' +
               ", includes=" + includePatterns.size() +
               ", excludes=" + excludePatterns.size() +
               '}';
    }
}
