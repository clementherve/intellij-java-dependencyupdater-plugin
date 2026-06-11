package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.model;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a row in the dependency table.
 */
public record DependencyRow(DependencyInfo dependency, VersionCandidate latestVersion, String status,
                            String updateType, String projectName) {
    public DependencyRow(@NotNull DependencyInfo dependency,
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
}
