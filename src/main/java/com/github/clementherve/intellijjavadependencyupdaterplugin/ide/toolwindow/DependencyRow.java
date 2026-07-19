package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeClassifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a row in the dependency table.
 */
public record DependencyRow(Dependency dependency, VersionCandidate latestVersion, Status status,
                            String updateType, String projectName) {

    /**
     * Where a dependency stands relative to the repository it was resolved against.
     */
    public enum Status {
        UP_TO_DATE, OUTDATED, NOT_FOUND
    }

    public DependencyRow(@NotNull Dependency dependency,
                         @Nullable VersionCandidate latestVersion,
                         @NotNull Status status,
                         @Nullable String updateType,
                         @NotNull String projectName) {
        this.dependency = dependency;
        this.latestVersion = latestVersion;
        this.status = status;
        this.updateType = updateType;
        this.projectName = projectName;
    }

    /**
     * Builds a row for a dependency, deriving its status and change kind from the latest
     * available version ({@code null} latest version means the dependency is up to date).
     */
    @NotNull
    public static DependencyRow from(@NotNull Dependency dependency,
                                     @Nullable VersionCandidate latestVersion,
                                     @NotNull String projectName) {
        Status status;
        String updateType = null;

        if (latestVersion == null) {
            status = Status.UP_TO_DATE;
        } else {
            status = Status.OUTDATED;
            updateType = VersionChangeClassifier.describe(dependency.currentVersion(), latestVersion.version());
        }

        return new DependencyRow(dependency, latestVersion, status, updateType, projectName);
    }

    /**
     * Builds a row for a dependency that could not be found in the repository it was queried against.
     */
    @NotNull
    public static DependencyRow notFound(@NotNull Dependency dependency, @NotNull String projectName) {
        return new DependencyRow(dependency, null, Status.NOT_FOUND, null, projectName);
    }
}
