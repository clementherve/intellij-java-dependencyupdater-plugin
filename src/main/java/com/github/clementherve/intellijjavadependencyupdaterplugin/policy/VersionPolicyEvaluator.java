package com.github.clementherve.intellijjavadependencyupdaterplugin.policy;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SemanticVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Evaluates version candidates against version policies.
 */
public class VersionPolicyEvaluator {

    /**
     * Evaluates a list of version strings against a policy, returning filtered and sorted candidates.
     *
     * @param versions the list of version strings to evaluate
     * @param policy the version policy to apply
     * @param repositorySource the source repository name
     * @return a list of version candidates that pass the policy, sorted in descending order
     */
    @NotNull
    public List<VersionCandidate> evaluate(@NotNull List<String> versions,
                                           @NotNull VersionPolicy policy,
                                           @NotNull String repositorySource) {
        List<VersionCandidate> candidates = new ArrayList<>();

        for (String version : versions) {
            if (matchesPolicy(version, policy)) {
                SemanticVersion semanticVersion = SemanticVersion.parse(version);
                candidates.add(new VersionCandidate(version, semanticVersion, repositorySource));
            }
        }

        // Sort in descending order (highest version first)
        candidates.sort(Collections.reverseOrder());
        return candidates;
    }

    /**
     * Finds the best (highest) version candidate that passes the policy and is greater than the current version.
     *
     * @param versions the list of available version strings
     * @param currentVersion the current version string
     * @param policy the version policy to apply
     * @param repositorySource the source repository name
     * @return the best version candidate, or null if no upgrade is available
     */
    @Nullable
    public VersionCandidate findBestCandidate(@NotNull List<String> versions,
                                               @NotNull String currentVersion,
                                               @NotNull VersionPolicy policy,
                                               @NotNull String repositorySource) {
        List<VersionCandidate> candidates = evaluate(versions, policy, repositorySource);

        SemanticVersion current = SemanticVersion.parse(currentVersion);
        if (current == null) {
            // If we can't parse the current version, return the highest candidate
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        // Find the highest version that is greater than the current version
        for (VersionCandidate candidate : candidates) {
            if (candidate.getSemanticVersion() != null &&
                candidate.getSemanticVersion().compareTo(current) > 0) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Checks if a version string matches the policy rules.
     *
     * @param version the version string to check
     * @param policy the version policy
     * @return true if the version passes the policy
     */
    private boolean matchesPolicy(@NotNull String version, @NotNull VersionPolicy policy) {
        // Check include patterns - at least one must match
        boolean includeMatch = policy.getIncludePatterns().isEmpty();
        for (String pattern : policy.getIncludePatterns()) {
            if (matchesPattern(version, pattern)) {
                includeMatch = true;
                break;
            }
        }

        if (!includeMatch) {
            return false;
        }

        // Check exclude patterns - none must match
        for (String pattern : policy.getExcludePatterns()) {
            if (matchesPattern(version, pattern)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a version string matches a regex pattern.
     *
     * @param version the version string
     * @param patternStr the regex pattern string
     * @return true if the pattern matches
     */
    private boolean matchesPattern(@NotNull String version, @NotNull String patternStr) {
        try {
            Pattern pattern = Pattern.compile(patternStr);
            return pattern.matcher(version).matches();
        } catch (PatternSyntaxException e) {
            // Invalid regex pattern, treat as no match
            return false;
        }
    }
}
