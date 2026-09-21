package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Queries Gradle's public <a href="https://services.gradle.org/versions/all">versions API</a> for
 * every published Gradle release.
 */
final class GradleVersionRepository {

    private static final String VERSIONS_URL = "https://services.gradle.org/versions/all";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * Fetches every non-broken Gradle version published, newest first.
     *
     * @throws IOException if the request fails or the server does not return a successful status
     */
    @NotNull
    List<String> fetchVersions() throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERSIONS_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Gradle versions API returned status " + response.statusCode());
            }

            return GradleVersionsResponseParser.parseVersions(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Gradle versions", exception);
        }
    }
}
