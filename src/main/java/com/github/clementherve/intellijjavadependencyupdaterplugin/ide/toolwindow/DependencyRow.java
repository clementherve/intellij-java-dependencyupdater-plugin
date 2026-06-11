package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeClassifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a row in the dependency table.
 */
public record DependencyRow(Dependency dependency, VersionCandidate latestVersion, String status,
                            String updateType, String projectName) {

    private static final String UP_TO_DATE = "Up to date";
    private static final String OUTDATED = "Outdated";

    public DependencyRow(@NotNull Dependency dependency,
                         @Nullable VersionCandidate latestVersion,
                         @NotNull String status,
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
        String status;
        String updateType = null;

        if (latestVersion == null) {
            status = UP_TO_DATE;
        } else {
            status = OUTDATED;
            updateType = VersionChangeClassifier.describe(dependency.currentVersion(), latestVersion.version());
        }

        return new DependencyRow(dependency, latestVersion, status, updateType, projectName);
    }
}
