package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.IgnoredVersion;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DependencyUpdaterSettingsTest {

    private DependencyUpdaterSettings settings;

    @Before
    public void setUp() {
        settings = new DependencyUpdaterSettings();
    }

    @Test
    public void test_version_is_not_ignored_by_default() {
        assertFalse(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
    }

    @Test
    public void test_ignore_version_marks_it_as_ignored() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");

        assertTrue(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
    }

    @Test
    public void test_ignore_version_does_not_affect_other_versions_or_artifacts() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");

        assertFalse(settings.isVersionIgnored("com.example", "artifact", "2.0.0"));
        assertFalse(settings.isVersionIgnored("com.example", "other-artifact", "1.0.0"));
        assertFalse(settings.isVersionIgnored("com.other", "artifact", "1.0.0"));
    }

    @Test
    public void test_ignoring_the_same_version_twice_does_not_duplicate_it() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");
        settings.ignoreVersion("com.example", "artifact", "1.0.0");

        assertEquals(1, settings.getIgnoredVersions().size());
    }

    @Test
    public void test_unignore_version_removes_it() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");
        settings.unignoreVersion("com.example", "artifact", "1.0.0");

        assertFalse(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
        assertTrue(settings.getIgnoredVersions().isEmpty());
    }

    @Test
    public void test_unignore_version_only_removes_matching_entry() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");
        settings.ignoreVersion("com.example", "artifact", "2.0.0");

        settings.unignoreVersion("com.example", "artifact", "1.0.0");

        assertFalse(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
        assertTrue(settings.isVersionIgnored("com.example", "artifact", "2.0.0"));
    }

    @Test
    public void test_set_ignored_versions_replaces_the_list() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");

        List<IgnoredVersion> replacement = Arrays.asList(
                new IgnoredVersion("com.other", "artifact", "3.0.0"));
        settings.setIgnoredVersions(replacement);

        assertFalse(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
        assertTrue(settings.isVersionIgnored("com.other", "artifact", "3.0.0"));
        assertEquals(1, settings.getIgnoredVersions().size());
    }

    @Test
    public void test_get_ignored_versions_returns_a_defensive_copy() {
        settings.ignoreVersion("com.example", "artifact", "1.0.0");

        List<IgnoredVersion> ignoredVersions = settings.getIgnoredVersions();
        ignoredVersions.clear();

        assertTrue(settings.isVersionIgnored("com.example", "artifact", "1.0.0"));
    }
}
