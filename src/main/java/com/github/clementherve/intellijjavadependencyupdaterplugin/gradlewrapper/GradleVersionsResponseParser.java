package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the response of Gradle's <a href="https://services.gradle.org/versions/all">versions
 * API</a>, a JSON array of release objects such as
 * {@code {"version": "8.10", "broken": false, ...}}.
 */
final class GradleVersionsResponseParser {

    private static final Logger LOGGER = Logger.getInstance(GradleVersionsResponseParser.class);
    private static final Gson GSON = new Gson();
    private static final Type RELEASE_LIST_TYPE = new TypeToken<List<GradleRelease>>() {
    }.getType();

    private GradleVersionsResponseParser() {
    }

    /**
     * @return every non-broken version found in the response, in the order the API returned them.
     * Returns an empty list if the response cannot be parsed.
     */
    @NotNull
    static List<String> parseVersions(@NotNull String json) {
        List<GradleRelease> releases;
        try {
            releases = GSON.fromJson(json, RELEASE_LIST_TYPE);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse Gradle versions response", exception);
            return List.of();
        }

        if (releases == null) {
            return List.of();
        }

        List<String> versions = new ArrayList<>();
        for (GradleRelease release : releases) {
            if (release != null && release.version() != null && !release.broken()) {
                versions.add(release.version());
            }
        }
        return versions;
    }

    private record GradleRelease(String version, boolean broken) {
    }
}
