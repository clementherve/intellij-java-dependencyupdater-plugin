package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Client for fetching versions from Nexus/Artifactory repositories.
 * Supports both Nexus 2.x and 3.x REST APIs.
 */
public class NexusRepository implements VersionRepository {

    private static final Logger LOGGER = Logger.getInstance(NexusRepository.class);
    private static final int TIMEOUT_MS = 10_000;

    private final String baseUrl;
    private final String username;
    private final String password;

    public NexusRepository(@NotNull String baseUrl,
                       @Nullable String username,
                       @Nullable String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
    }

    @NotNull
    @Override
    public List<String> fetchVersions(@NotNull String group, @NotNull String artifact) throws IOException {
        String groupPath = group.replace('.', '/');
        String metadataUrl = String.format("%s/%s/%s/maven-metadata.xml",
                baseUrl, groupPath, artifact);

        try {
            final String authHeader;
            if (username != null && !username.isEmpty() && password != null) {
                String auth = username + ":" + password;
                authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            } else {
                authHeader = null;
            }

            String xmlContent = HttpRequests.request(metadataUrl)
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .tuner(connection -> {
                        if (authHeader != null) {
                            connection.setRequestProperty("Authorization", authHeader);
                        }
                    })
                    .readString(null);

            return MavenMetadataParser.parseVersions(xmlContent);

        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 401) {
                LOGGER.warn("Authentication failed for Nexus: " + baseUrl);
                throw new IOException("Nexus authentication failed. Please check your credentials.", e);
            } else if (e.getStatusCode() == 404) {
                LOGGER.warn("Artifact not found in Nexus: " + group + ":" + artifact);
                throw new DependencyNotFoundException("Artifact not found in Nexus: " + group + ":" + artifact);
            }
            throw e;
        } catch (IOException e) {
            LOGGER.error("Failed to fetch versions from Nexus for " + group + ":" + artifact, e);
            throw e;
        }
    }

    @NotNull
    @Override
    public String getSourceName() {
        return "Nexus (" + baseUrl + ")";
    }
}
