package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GradleVersionsResponseParserTest {

    @Test
    public void test_parses_versions_and_skips_broken_releases() {
        String json = """
                [
                    {"version": "9.1", "broken": false},
                    {"version": "9.0.1-broken", "broken": true},
                    {"version": "9.0", "broken": false}
                ]""";

        List<String> versions = GradleVersionsResponseParser.parseVersions(json);

        assertEquals(List.of("9.1", "9.0"), versions);
    }

    @Test
    public void test_malformed_json_returns_empty_list() {
        assertTrue(GradleVersionsResponseParser.parseVersions("not json").isEmpty());
    }

    @Test
    public void test_empty_array_returns_empty_list() {
        assertTrue(GradleVersionsResponseParser.parseVersions("[]").isEmpty());
    }
}
