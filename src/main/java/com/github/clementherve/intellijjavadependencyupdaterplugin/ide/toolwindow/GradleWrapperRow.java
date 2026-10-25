package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper.GradleWrapperInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a row in the Gradle wrapper table - one per {@code gradle-wrapper.properties} file
 * found in the project.
 */
public record GradleWrapperRow(GradleWrapperInfo info, VersionCandidate latestVersion, Status status) {

    public enum Status {
        UP_TO_DATE, OUTDATED, ERROR
    }

    public GradleWrapperRow(@NotNull GradleWrapperInfo info, @Nullable VersionCandidate latestVersion, @NotNull Status status) {
        this.info = info;
        this.latestVersion = latestVersion;
        this.status = status;
    }

    /**
     * Builds a row from a completed check ({@code null} latest version means the wrapper is
     * already up to date).
     */
    @NotNull
    public static GradleWrapperRow from(@NotNull GradleWrapperInfo info, @Nullable VersionCandidate latestVersion) {
        return new GradleWrapperRow(info, latestVersion, latestVersion != null ? Status.OUTDATED : Status.UP_TO_DATE);
    }

    /**
     * Builds a row for a wrapper whose check failed (e.g. network error).
     */
    @NotNull
    public static GradleWrapperRow error(@NotNull GradleWrapperInfo info) {
        return new GradleWrapperRow(info, null, Status.ERROR);
    }
}
