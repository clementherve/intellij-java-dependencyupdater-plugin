package com.github.clementherve.intellijjavadependencyupdaterplugin.version;

import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Classifies the difference between version strings: it can both pick the latest
 * candidate of a given {@link VersionChangeKind} and describe the change between
 * two versions for display.
 */
public final class VersionChangeClassifier {

    private static final String UNKNOWN = "Unknown";
    private static final String MAJOR = "Major";
    private static final String MINOR = "Minor";
    private static final String PATCH = "Patch";
    private static final String OTHER = "Other";

    private VersionChangeClassifier() {
    }

    /**
     * Finds the latest version candidate that represents the given change kind relative
     * to the current version.
     *
     * @param currentVersion the current version
     * @param candidates     all available version candidates (should be sorted descending)
     * @param kind           the change kind to find
     * @return the latest candidate of the given kind, or {@code null} if none qualifies
     */
    @Nullable
    public static VersionCandidate findLatestChange(@NotNull SemanticVersion currentVersion,
                                                    @NotNull List<VersionCandidate> candidates,
                                                    @NotNull VersionChangeKind kind) {
        for (VersionCandidate candidate : candidates) {
            SemanticVersion candidateVersion = SemanticVersion.parse(candidate.version());
            if (candidateVersion == null) {
                continue;
            }

            // Skip if not newer than current
            if (candidateVersion.compareTo(currentVersion) <= 0) {
                continue;
            }

            switch (kind) {
                case PATCH:
                    // Same major and minor, different patch
                    if (candidateVersion.getMajor() == currentVersion.getMajor() &&
                        candidateVersion.getMinor() == currentVersion.getMinor() &&
                        candidateVersion.getPatch() > currentVersion.getPatch()) {
                        return candidate;
                    }
                    break;

                case MINOR:
                    // Same major, higher minor
                    if (candidateVersion.getMajor() == currentVersion.getMajor() &&
                        candidateVersion.getMinor() > currentVersion.getMinor()) {
                        return candidate;
                    }
                    break;

                case MAJOR:
                    // Higher major
                    if (candidateVersion.getMajor() > currentVersion.getMajor()) {
                        return candidate;
                    }
                    break;
            }
        }

        return null;
    }

    /**
     * Describes, for display, how the latest version differs from the current one
     * (e.g. {@code "Major"}, {@code "Minor"}, {@code "Patch"}). Returns {@code "Other"}
     * when the versions differ in a way that is not a clean major/minor/patch bump, and
     * {@code "Unknown"} when either version cannot be parsed as numeric components.
     */
    @NotNull
    public static String describe(@NotNull String currentVersion, @NotNull String latestVersion) {
        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");

        if (currentParts.length < 1 || latestParts.length < 1) {
            return UNKNOWN;
        }

        try {
            int currentMajor = Integer.parseInt(currentParts[0]);
            int latestMajor = Integer.parseInt(latestParts[0]);

            if (latestMajor > currentMajor) {
                return MAJOR;
            }

            if (currentParts.length >= 2 && latestParts.length >= 2) {
                int currentMinor = Integer.parseInt(currentParts[1]);
                int latestMinor = Integer.parseInt(latestParts[1]);

                if (latestMinor > currentMinor) {
                    return MINOR;
                }
            }

            if (currentParts.length >= 3 && latestParts.length >= 3) {
                int currentPatch = Integer.parseInt(currentParts[2].split("-")[0]); // Handle versions like 1.2.3-beta
                int latestPatch = Integer.parseInt(latestParts[2].split("-")[0]);

                if (latestPatch > currentPatch) {
                    return PATCH;
                }
            }

            return OTHER;
        } catch (NumberFormatException exception) {
            return UNKNOWN;
        }
    }
}
