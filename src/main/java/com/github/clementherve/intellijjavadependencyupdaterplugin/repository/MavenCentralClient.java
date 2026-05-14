package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for fetching versions from Maven Central using the maven-metadata.xml endpoint.
 */
public class MavenCentralClient implements VersionRepository {

    private static final Logger LOG = Logger.getInstance(MavenCentralClient.class);
    private static final String MAVEN_CENTRAL_BASE_URL = "https://repo1.maven.org/maven2";
    private static final int TIMEOUT_MS = 10000;

    @NotNull
    @Override
    public List<String> fetchVersions(@NotNull String group, @NotNull String artifact) throws IOException {
        // Convert group ID dots to slashes (e.g., com.google.guava -> com/google/guava)
        String groupPath = group.replace('.', '/');
        String metadataUrl = String.format("%s/%s/%s/maven-metadata.xml",
                MAVEN_CENTRAL_BASE_URL, groupPath, artifact);

        LOG.info("Fetching versions from Maven Central: " + metadataUrl);

        try {
            String xmlContent = HttpRequests.request(metadataUrl)
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .readString(null);

            return parseVersionsFromMetadata(xmlContent);
        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                LOG.warn("Artifact not found in Maven Central: " + group + ":" + artifact);
                return Collections.emptyList();
            }
            throw e;
        } catch (IOException e) {
            LOG.warn("Failed to fetch versions from Maven Central for " + group + ":" + artifact, e);
            throw e;
        }
    }

    @NotNull
    @Override
    public String getSourceName() {
        return "Maven Central";
    }

    /**
     * Parses version strings from Maven metadata XML.
     * Expected format:
     * <metadata>
     * <versioning>
     * <versions>
     * <version>1.0.0</version>
     * <version>1.1.0</version>
     * </versions>
     * </versioning>
     * </metadata>
     */
    @NotNull
    private List<String> parseVersionsFromMetadata(@NotNull String xmlContent) {
        List<String> versions = new ArrayList<>();

        try {
            // Simple XML parsing - extract <version>...</version> tags
            int pos = 0;
            while (true) {
                int versionStart = xmlContent.indexOf("<version>", pos);
                if (versionStart == -1) break;

                int versionEnd = xmlContent.indexOf("</version>", versionStart);
                if (versionEnd == -1) break;

                String version = xmlContent.substring(versionStart + 9, versionEnd).trim();
                if (!version.isEmpty()) {
                    versions.add(version);
                }

                pos = versionEnd + 10;
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Maven metadata XML", e);
        }

        return versions;
    }
}
