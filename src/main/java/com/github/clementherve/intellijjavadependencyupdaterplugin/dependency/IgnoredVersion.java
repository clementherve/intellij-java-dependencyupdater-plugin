package com.github.clementherve.intellijjavadependencyupdaterplugin.dependency;

import org.jetbrains.annotations.NotNull;

/**
 * Identifies a specific version of a dependency that the user has chosen to ignore, so it is
 * never suggested as an update again until un-ignored.
 */
public record IgnoredVersion(String group, String artifact, String version) {
    public IgnoredVersion(@NotNull String group, @NotNull String artifact, @NotNull String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    @Override
    @NotNull
    public String group() {
        return group;
    }

    @Override
    @NotNull
    public String artifact() {
        return artifact;
    }

    @Override
    @NotNull
    public String version() {
        return version;
    }

    @NotNull
    @Override
    public String toString() {
        String coordinates = group.isEmpty() ? artifact : group + ":" + artifact;
        return coordinates + ":" + version;
    }
}
