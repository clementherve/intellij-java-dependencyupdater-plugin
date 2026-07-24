package com.github.clementherve.intellijjavadependencyupdaterplugin.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a semantic version with support for major.minor.patch versioning
 * and common qualifiers like alpha, beta, rc, snapshot, feat, pr.
 */
public class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?" +                    // major.minor.patch
        "(?:[-.]?(alpha|beta|milestone|m|rc|snapshot|feat|pr)(?![a-zA-Z]))?" + // qualifier
        "(?:[-.]?(\\d+))?" +                                       // qualifier version
        "(.*)$",                                                   // unrecognized remainder
        Pattern.CASE_INSENSITIVE
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final Qualifier qualifier;
    private final int qualifierVersion;
    private final String originalVersion;

    public enum Qualifier {
        ALPHA(1),
        BETA(2),
        MILESTONE(3),
        RC(4),
        // Unrecognized branch/build suffix (e.g. "-develop", "-abc123"). Ranked below FEAT/PR
        // since we can't tell what it represents; ties among these are broken by publish order
        // in VersionPolicyEvaluator rather than here.
        UNRECOGNIZED(5),
        FEAT(6),
        PR(7),
        RELEASE(8),
        SNAPSHOT(9);

        private final int priority;

        Qualifier(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }

        public static Qualifier fromString(@Nullable String str) {
            if (str == null || str.isEmpty()) {
                return RELEASE;
            }
            String lower = str.toLowerCase();
            return switch (lower) {
                case "alpha", "a" -> ALPHA;
                case "beta", "b" -> BETA;
                case "milestone", "m" -> MILESTONE;
                case "rc" -> RC;
                case "feat" -> FEAT;
                case "pr" -> PR;
                case "snapshot" -> SNAPSHOT;
                default -> RELEASE;
            };
        }
    }

    private SemanticVersion(int major, int minor, int patch, Qualifier qualifier,
                           int qualifierVersion, String originalVersion) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
        this.qualifierVersion = qualifierVersion;
        this.originalVersion = originalVersion;
    }

    /**
     * Parses a version string into a SemanticVersion object.
     *
     * @param version the version string to parse
     * @return a SemanticVersion object, or null if parsing fails
     */
    @Nullable
    public static SemanticVersion parse(@Nullable String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }

        String trimmed = version.trim();
        Matcher matcher = VERSION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            return null;
        }

        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

            String qualifierStr = matcher.group(4);
            Qualifier qualifier;
            int qualifierVersion = 0;
            if (qualifierStr != null) {
                qualifier = Qualifier.fromString(qualifierStr);
                qualifierVersion = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
            } else {
                String remainder = matcher.group(6);
                qualifier = (remainder != null && !remainder.isEmpty()) ? Qualifier.UNRECOGNIZED : Qualifier.RELEASE;
            }

            return new SemanticVersion(major, minor, patch, qualifier, qualifierVersion, trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public Qualifier getQualifier() {
        return qualifier;
    }

    public String getOriginalVersion() {
        return originalVersion;
    }

    public boolean isStable() {
        return qualifier == Qualifier.RELEASE;
    }

    @Override
    public int compareTo(@NotNull SemanticVersion other) {
        // Compare major version
        int result = Integer.compare(this.major, other.major);
        if (result != 0) return result;

        // Compare minor version
        result = Integer.compare(this.minor, other.minor);
        if (result != 0) return result;

        // Compare patch version
        result = Integer.compare(this.patch, other.patch);
        if (result != 0) return result;

        // Compare qualifier (alpha < beta < rc < unrecognized < feat < pr < release < snapshot)
        result = Integer.compare(this.qualifier.getPriority(), other.qualifier.getPriority());
        if (result != 0) return result;

        // Compare qualifier version (e.g., alpha.1 < alpha.2)
        return Integer.compare(this.qualifierVersion, other.qualifierVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticVersion that = (SemanticVersion) o;
        return major == that.major &&
               minor == that.minor &&
               patch == that.patch &&
               qualifierVersion == that.qualifierVersion &&
               qualifier == that.qualifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, qualifier, qualifierVersion);
    }

    @Override
    public String toString() {
        return originalVersion;
    }
}
