package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utility for categorizing version updates by type (patch, minor, major).
 */
public class VersionUpdateType {

    public enum UpdateType {
        PATCH,
        MINOR,
        MAJOR
    }

    /**
     * Finds the latest version candidate for a specific update type.
     *
     * @param currentVersion the current version
     * @param candidates all available version candidates (should be sorted descending)
     * @param type the update type to find
     * @return the latest candidate of the specified type, or null if none found
     */
    @Nullable
    public static VersionCandidate findLatestByType(@NotNull SemanticVersion currentVersion,
                                                     @NotNull List<VersionCandidate> candidates,
                                                     @NotNull UpdateType type) {
        for (VersionCandidate candidate : candidates) {
            SemanticVersion candidateVersion = SemanticVersion.parse(candidate.version());
            if (candidateVersion == null) {
                continue;
            }

            // Skip if not newer than current
            if (candidateVersion.compareTo(currentVersion) <= 0) {
                continue;
            }

            // Check if this candidate matches the update type
            switch (type) {
                case PATCH:
                    // Same major and minor, different patch
                    if (candidateVersion.getMajor() == currentVersion.getMajor() &&
                        candidateVersion.getMinor() == currentVersion.getMinor() &&
                        candidateVersion.getPatch() > currentVersion.getPatch()) {
                        return candidate;
                    }
                    break;

                case MINOR:
                    // Same major, different minor
                    if (candidateVersion.getMajor() == currentVersion.getMajor() &&
                        candidateVersion.getMinor() > currentVersion.getMinor()) {
                        return candidate;
                    }
                    break;

                case MAJOR:
                    // Different major
                    if (candidateVersion.getMajor() > currentVersion.getMajor()) {
                        return candidate;
                    }
                    break;
            }
        }

        return null;
    }
}
