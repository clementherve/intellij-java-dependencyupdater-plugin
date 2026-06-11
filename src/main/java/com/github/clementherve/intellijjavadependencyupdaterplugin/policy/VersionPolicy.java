package com.github.clementherve.intellijjavadependencyupdaterplugin.policy;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a version policy with include/exclude patterns.
 */
public record VersionPolicy(String name, List<String> includePatterns, List<String> excludePatterns) {
    public VersionPolicy(@NotNull String name,
                         @NotNull List<String> includePatterns,
                         @NotNull List<String> excludePatterns) {
        this.name = name;
        this.includePatterns = new ArrayList<>(includePatterns);
        this.excludePatterns = new ArrayList<>(excludePatterns);
    }

    @Override
    @NotNull
    public String name() {
        return name;
    }

    @Override
    @NotNull
    public List<String> includePatterns() {
        return Collections.unmodifiableList(includePatterns);
    }

    @Override
    @NotNull
    public List<String> excludePatterns() {
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

    @NotNull
    @Override
    public String toString() {
        return "VersionPolicy{" +
                "name='" + name + '\'' +
                ", includes=" + includePatterns.size() +
                ", excludes=" + excludePatterns.size() +
                '}';
    }
}
