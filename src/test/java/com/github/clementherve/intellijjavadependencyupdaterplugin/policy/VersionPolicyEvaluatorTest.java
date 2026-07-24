package com.github.clementherve.intellijjavadependencyupdaterplugin.policy;

import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VersionPolicyEvaluatorTest {

    private VersionPolicyEvaluator evaluator;
    private VersionPolicy stablePolicy;

    @Before
    public void setUp() {
        evaluator = new VersionPolicyEvaluator();
        stablePolicy = VersionPolicy.createDefaultStablePolicy();
    }

    @Test
    public void test_evaluate_filters_alpha_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-alpha",
                "1.2.0",
                "2.0.0-alpha.1"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(2, candidates.size());
        assertEquals("1.2.0", candidates.get(0).version());
        assertEquals("1.0.0", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_filters_beta_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-beta",
                "1.2.0",
                "2.0.0-beta.1"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(2, candidates.size());
        assertEquals("1.2.0", candidates.get(0).version());
        assertEquals("1.0.0", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_filters_rc_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-rc",
                "1.2.0",
                "2.0.0-rc.1"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(2, candidates.size());
    }

    @Test
    public void test_evaluate_filters_snapshot_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-SNAPSHOT",
                "1.2.0",
                "2.0.0-SNAPSHOT"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(2, candidates.size());
        assertEquals("1.2.0", candidates.get(0).version());
        assertEquals("1.0.0", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_filters_milestone_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-M1",
                "1.2.0",
                "2.0.0-milestone.1"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(2, candidates.size());
    }

    @Test
    public void test_evaluate_sorts_in_descending_order() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "3.0.0",
                "2.0.0",
                "1.5.0"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Maven Central");

        assertEquals(4, candidates.size());
        assertEquals("3.0.0", candidates.get(0).version());
        assertEquals("2.0.0", candidates.get(1).version());
        assertEquals("1.5.0", candidates.get(2).version());
        assertEquals("1.0.0", candidates.get(3).version());
    }

    @Test
    public void test_findBestCandidate_returns_highest_newer_version() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.5.0",
                "2.0.0",
                "2.1.0"
        );

        VersionCandidate best = evaluator.findBestCandidate(versions, "1.5.0", stablePolicy, "Maven Central");

        assertNotNull(best);
        assertEquals("2.1.0", best.version());
    }

    @Test
    public void test_findBestCandidate_returns_null_when_already_latest() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.5.0",
                "2.0.0"
        );

        VersionCandidate best = evaluator.findBestCandidate(versions, "2.0.0", stablePolicy, "Maven Central");

        assertNull(best);
    }

    @Test
    public void test_findBestCandidate_skips_unstable_versions() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "2.0.0-alpha",
                "2.0.0-beta",
                "2.0.0-rc"
        );

        VersionCandidate best = evaluator.findBestCandidate(versions, "1.0.0", stablePolicy, "Maven Central");

        assertNull(best);
    }

    @Test
    public void test_findBestCandidate_with_custom_policy_allowing_rc() {
        List<String> includePatterns = Collections.singletonList("^\\d+\\.\\d+(\\.\\d+)?.*$");
        List<String> excludePatterns = Collections.singletonList("(?i).*(alpha|beta|snapshot).*");
        VersionPolicy customPolicy = new VersionPolicy("Allow RC", includePatterns, excludePatterns);

        List<String> versions = Arrays.asList(
                "1.0.0",
                "2.0.0-alpha",
                "2.0.0-rc",
                "2.0.0"
        );

        VersionCandidate best = evaluator.findBestCandidate(versions, "1.0.0", customPolicy, "Maven Central");

        assertNotNull(best);
        assertEquals("2.0.0", best.version());
    }

    @Test
    public void test_evaluate_with_empty_include_patterns_matches_all() {
        List<String> includePatterns = Collections.emptyList();
        List<String> excludePatterns = Collections.singletonList("(?i).*snapshot.*");
        VersionPolicy policy = new VersionPolicy("No SNAPSHOT", includePatterns, excludePatterns);

        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-SNAPSHOT",
                "2.0.0-alpha",
                "2.0.0"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Maven Central");

        assertEquals(3, candidates.size());
        assertTrue(candidates.stream().noneMatch(c -> c.version().contains("SNAPSHOT")));
    }

    @Test
    public void test_evaluate_with_empty_exclude_patterns_only_uses_includes() {
        List<String> includePatterns = Collections.singletonList("^\\d+\\.\\d+\\.\\d+$");
        List<String> excludePatterns = Collections.emptyList();
        VersionPolicy policy = new VersionPolicy("Strict semver", includePatterns, excludePatterns);

        List<String> versions = Arrays.asList(
                "1.0.0",
                "1.1.0-alpha",
                "2.0.0",
                "3.0"
        );

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Maven Central");

        assertEquals(2, candidates.size());
        assertEquals("2.0.0", candidates.get(0).version());
        assertEquals("1.0.0", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_handles_invalid_regex_gracefully() {
        List<String> includePatterns = Collections.singletonList("[invalid regex");
        List<String> excludePatterns = Collections.emptyList();
        VersionPolicy policy = new VersionPolicy("Invalid", includePatterns, excludePatterns);

        List<String> versions = Arrays.asList("1.0.0", "2.0.0");

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Maven Central");

        assertEquals(0, candidates.size());
    }

    @Test
    public void test_findBestCandidate_with_unparseable_current_version_returns_highest() {
        List<String> versions = Arrays.asList(
                "1.0.0",
                "2.0.0",
                "3.0.0"
        );

        VersionCandidate best = evaluator.findBestCandidate(versions, "unknown", stablePolicy, "Maven Central");

        assertNotNull(best);
        assertEquals("3.0.0", best.version());
    }

    @Test
    public void test_evaluate_breaks_ties_by_publish_order_for_unrecognized_suffixes() {
        // Both versions parse to the same SemanticVersion (2.0.17, UNRECOGNIZED qualifier)
        // since neither "develop" nor "hotfix" are recognized qualifiers. Nexus/Maven metadata
        // lists versions oldest-first, so the "hotfix" build (published after "develop") must
        // still end up first.
        List<String> versions = Arrays.asList(
                "2.0.17-develop-4740b57b",
                "2.0.17-hotfix-58c17607"
        );

        List<String> includePatterns = Collections.emptyList();
        List<String> excludePatterns = Collections.emptyList();
        VersionPolicy policy = new VersionPolicy("Allow all", includePatterns, excludePatterns);

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Private Nexus");

        assertEquals(2, candidates.size());
        assertEquals("2.0.17-hotfix-58c17607", candidates.get(0).version());
        assertEquals("2.0.17-develop-4740b57b", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_ranks_pr_above_feat_regardless_of_publish_order() {
        // "feat" is listed after "pr" (i.e. published more recently per Maven's oldest-first
        // convention), but "pr" must still outrank "feat" since that ordering is an explicit
        // qualifier priority, not a publish-order tie-break.
        List<String> versions = Arrays.asList(
                "1.0.3-pr",
                "1.0.3-feat"
        );

        List<String> includePatterns = Collections.emptyList();
        List<String> excludePatterns = Collections.emptyList();
        VersionPolicy policy = new VersionPolicy("Allow all", includePatterns, excludePatterns);

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Private Nexus");

        assertEquals(2, candidates.size());
        assertEquals("1.0.3-pr", candidates.get(0).version());
        assertEquals("1.0.3-feat", candidates.get(1).version());
    }

    @Test
    public void test_evaluate_ranks_release_above_feat_and_pr_suffixes() {
        List<String> versions = Arrays.asList(
                "1.0.3-feat",
                "1.0.3-pr",
                "1.0.3"
        );

        List<String> includePatterns = Collections.emptyList();
        List<String> excludePatterns = Collections.emptyList();
        VersionPolicy policy = new VersionPolicy("Allow all", includePatterns, excludePatterns);

        List<VersionCandidate> candidates = evaluator.evaluate(versions, policy, "Private Nexus");

        assertEquals(3, candidates.size());
        assertEquals("1.0.3", candidates.get(0).version());
        assertEquals("1.0.3-pr", candidates.get(1).version());
        assertEquals("1.0.3-feat", candidates.get(2).version());
    }

    @Test
    public void test_repository_source_is_preserved() {
        List<String> versions = Collections.singletonList("1.0.0");

        List<VersionCandidate> candidates = evaluator.evaluate(versions, stablePolicy, "Private Nexus");

        assertEquals(1, candidates.size());
        assertEquals("Private Nexus", candidates.getFirst().repositorySource());
    }
}
