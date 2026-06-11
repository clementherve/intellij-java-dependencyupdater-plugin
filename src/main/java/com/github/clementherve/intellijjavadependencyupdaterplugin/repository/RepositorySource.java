package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import org.jetbrains.annotations.NotNull;

/**
 * The repository a dependency's versions are resolved from. The display name is what the
 * tool window and version candidates show to the user.
 */
public enum RepositorySource {
    GRADLE_PLUGIN_PORTAL("Gradle Plugin Portal"),
    NEXUS("Nexus"),
    MAVEN_CENTRAL("Maven Central");

    private final String displayName;

    RepositorySource(@NotNull String displayName) {
        this.displayName = displayName;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }
}
