package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the Gradle version from a {@code gradle-wrapper.properties} file's {@code distributionUrl}
 * line, e.g. {@code distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip}.
 */
final class GradleWrapperPropertiesParser {

    // The version group is greedy so that it swallows any hyphenated qualifier (e.g.
    // "9.0-milestone-1", "9.0-rc-1") instead of stopping at the first hyphen, backtracking only
    // as far as the last "-bin.zip"/"-all.zip" suffix.
    private static final Pattern DISTRIBUTION_URL_PATTERN =
            Pattern.compile("distributionUrl\\s*=.*gradle-(.+)-(bin|all)\\.zip");

    private GradleWrapperPropertiesParser() {
    }

    /**
     * @param propertiesContent the raw content of a {@code gradle-wrapper.properties} file
     * @return the parsed version and distribution kind, or {@code null} if the file doesn't
     * contain a recognizable {@code distributionUrl}
     */
    @Nullable
    static ParsedDistributionUrl parse(@NotNull String propertiesContent) {
        Matcher matcher = DISTRIBUTION_URL_PATTERN.matcher(propertiesContent);
        if (!matcher.find()) {
            return null;
        }

        String version = matcher.group(1);
        boolean allDistribution = "all".equals(matcher.group(2));
        return new ParsedDistributionUrl(version, allDistribution);
    }

    record ParsedDistributionUrl(@NotNull String version, boolean allDistribution) {
    }
}
