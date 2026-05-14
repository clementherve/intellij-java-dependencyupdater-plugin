package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SemanticVersionTest {

    @Test
    public void test_parse_simple_version() {
        SemanticVersion version = SemanticVersion.parse("1.2.3");
        assertNotNull(version);
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
        assertTrue(version.isStable());
    }

    @Test
    public void test_parse_major_minor_only() {
        SemanticVersion version = SemanticVersion.parse("2.0");
        assertNotNull(version);
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getPatch());
    }

    @Test
    public void test_parse_major_only() {
        SemanticVersion version = SemanticVersion.parse("3");
        assertNotNull(version);
        assertEquals(3, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getPatch());
    }

    @Test
    public void test_parse_with_alpha() {
        SemanticVersion version = SemanticVersion.parse("1.0.0-alpha");
        assertNotNull(version);
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getPatch());
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_with_alpha_version() {
        SemanticVersion version = SemanticVersion.parse("2.0.0-alpha.1");
        assertNotNull(version);
        assertEquals(2, version.getMajor());
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_with_beta() {
        SemanticVersion version = SemanticVersion.parse("1.5.0-beta");
        assertNotNull(version);
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_with_rc() {
        SemanticVersion version = SemanticVersion.parse("3.0.0-rc.1");
        assertNotNull(version);
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_with_snapshot() {
        SemanticVersion version = SemanticVersion.parse("1.0.0-SNAPSHOT");
        assertNotNull(version);
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_with_milestone() {
        SemanticVersion version = SemanticVersion.parse("2.0.0-M1");
        assertNotNull(version);
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_invalid_version_returns_null() {
        assertNull(SemanticVersion.parse("not-a-version"));
        assertNull(SemanticVersion.parse(""));
        assertNull(SemanticVersion.parse(null));
        assertNull(SemanticVersion.parse("abc.def.ghi"));
    }

    @Test
    public void test_comparison_major_version() {
        SemanticVersion v1 = SemanticVersion.parse("1.0.0");
        SemanticVersion v2 = SemanticVersion.parse("2.0.0");
        assertNotNull(v1);
        assertNotNull(v2);
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v1) > 0);
    }

    @Test
    public void test_comparison_minor_version() {
        SemanticVersion v1 = SemanticVersion.parse("1.5.0");
        SemanticVersion v2 = SemanticVersion.parse("1.10.0");
        assertNotNull(v1);
        assertNotNull(v2);
        assertTrue(v1.compareTo(v2) < 0);
    }

    @Test
    public void test_comparison_patch_version() {
        SemanticVersion v1 = SemanticVersion.parse("1.0.5");
        SemanticVersion v2 = SemanticVersion.parse("1.0.10");
        assertNotNull(v1);
        assertNotNull(v2);
        assertTrue(v1.compareTo(v2) < 0);
    }

    @Test
    public void test_comparison_qualifier_alpha_less_than_beta() {
        SemanticVersion alpha = SemanticVersion.parse("1.0.0-alpha");
        SemanticVersion beta = SemanticVersion.parse("1.0.0-beta");
        assertNotNull(alpha);
        assertNotNull(beta);
        assertTrue(alpha.compareTo(beta) < 0);
    }

    @Test
    public void test_comparison_qualifier_beta_less_than_rc() {
        SemanticVersion beta = SemanticVersion.parse("1.0.0-beta");
        SemanticVersion rc = SemanticVersion.parse("1.0.0-rc");
        assertNotNull(beta);
        assertNotNull(rc);
        assertTrue(beta.compareTo(rc) < 0);
    }

    @Test
    public void test_comparison_qualifier_rc_less_than_release() {
        SemanticVersion rc = SemanticVersion.parse("1.0.0-rc");
        SemanticVersion release = SemanticVersion.parse("1.0.0");
        assertNotNull(rc);
        assertNotNull(release);
        assertTrue(rc.compareTo(release) < 0);
    }

    @Test
    public void test_comparison_release_less_than_snapshot() {
        SemanticVersion release = SemanticVersion.parse("1.0.0");
        SemanticVersion snapshot = SemanticVersion.parse("1.0.0-SNAPSHOT");
        assertNotNull(release);
        assertNotNull(snapshot);
        assertTrue(release.compareTo(snapshot) < 0);
    }

    @Test
    public void test_comparison_qualifier_versions() {
        SemanticVersion alpha1 = SemanticVersion.parse("1.0.0-alpha.1");
        SemanticVersion alpha2 = SemanticVersion.parse("1.0.0-alpha.2");
        assertNotNull(alpha1);
        assertNotNull(alpha2);
        assertTrue(alpha1.compareTo(alpha2) < 0);
    }

    @Test
    public void test_equals_same_version() {
        SemanticVersion v1 = SemanticVersion.parse("1.2.3");
        SemanticVersion v2 = SemanticVersion.parse("1.2.3");
        assertNotNull(v1);
        assertNotNull(v2);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    public void test_equals_different_versions() {
        SemanticVersion v1 = SemanticVersion.parse("1.2.3");
        SemanticVersion v2 = SemanticVersion.parse("1.2.4");
        assertNotNull(v1);
        assertNotNull(v2);
        assertNotEquals(v1, v2);
    }

    @Test
    public void test_sorting_versions() {
        List<SemanticVersion> versions = Arrays.asList(
            SemanticVersion.parse("2.0.0"),
            SemanticVersion.parse("1.0.0-alpha"),
            SemanticVersion.parse("1.0.0-beta"),
            SemanticVersion.parse("1.0.0"),
            SemanticVersion.parse("1.10.0"),
            SemanticVersion.parse("1.5.0"),
            SemanticVersion.parse("1.0.0-rc"),
            SemanticVersion.parse("3.0.0")
        );

        Collections.sort(versions);

        // Expected order: alpha < beta < rc < 1.0.0 < 1.5.0 < 1.10.0 < 2.0.0 < 3.0.0
        assertEquals("1.0.0-alpha", versions.get(0).toString());
        assertEquals("1.0.0-beta", versions.get(1).toString());
        assertEquals("1.0.0-rc", versions.get(2).toString());
        assertEquals("1.0.0", versions.get(3).toString());
        assertEquals("1.5.0", versions.get(4).toString());
        assertEquals("1.10.0", versions.get(5).toString());
        assertEquals("2.0.0", versions.get(6).toString());
        assertEquals("3.0.0", versions.get(7).toString());
    }

    @Test
    public void test_toString_returns_original_version() {
        String original = "1.2.3-beta.1";
        SemanticVersion version = SemanticVersion.parse(original);
        assertNotNull(version);
        assertEquals(original, version.toString());
    }

    @Test
    public void test_parse_with_dot_separator() {
        SemanticVersion version = SemanticVersion.parse("1.0.0.alpha.1");
        assertNotNull(version);
        assertFalse(version.isStable());
    }

    @Test
    public void test_parse_case_insensitive_qualifiers() {
        SemanticVersion upper = SemanticVersion.parse("1.0.0-ALPHA");
        SemanticVersion lower = SemanticVersion.parse("1.0.0-alpha");
        assertNotNull(upper);
        assertNotNull(lower);
        assertEquals(upper, lower);
    }
}
