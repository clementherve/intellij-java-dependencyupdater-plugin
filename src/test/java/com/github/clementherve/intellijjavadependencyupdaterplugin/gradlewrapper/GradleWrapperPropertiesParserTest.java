package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GradleWrapperPropertiesParserTest {

    @Test
    public void test_parses_bin_distribution() {
        String content = """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists""";

        GradleWrapperPropertiesParser.ParsedDistributionUrl parsed = GradleWrapperPropertiesParser.parse(content);

        assertEquals("9.7.1", parsed.version());
        assertFalse(parsed.allDistribution());
    }

    @Test
    public void test_parses_all_distribution() {
        String content = "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.10-all.zip";

        GradleWrapperPropertiesParser.ParsedDistributionUrl parsed = GradleWrapperPropertiesParser.parse(content);

        assertEquals("8.10", parsed.version());
        assertTrue(parsed.allDistribution());
    }

    @Test
    public void test_parses_milestone_version() {
        String content = "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.0-milestone-1-bin.zip";

        GradleWrapperPropertiesParser.ParsedDistributionUrl parsed = GradleWrapperPropertiesParser.parse(content);

        assertEquals("9.0-milestone-1", parsed.version());
        assertFalse(parsed.allDistribution());
    }

    @Test
    public void test_returns_null_when_no_distribution_url() {
        String content = "distributionBase=GRADLE_USER_HOME";

        assertNull(GradleWrapperPropertiesParser.parse(content));
    }
}
