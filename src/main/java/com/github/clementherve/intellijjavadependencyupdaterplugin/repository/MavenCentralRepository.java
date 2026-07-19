package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Client for fetching versions from Maven Central using the maven-metadata.xml endpoint.
 */
public class MavenCentralRepository implements VersionRepository {

    private static final Logger LOG = Logger.getInstance(MavenCentralRepository.class);
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

            return MavenMetadataParser.parseVersions(xmlContent);
        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                LOG.warn("Artifact not found in Maven Central: " + group + ":" + artifact);
                throw new DependencyNotFoundException("Artifact not found in Maven Central: " + group + ":" + artifact);
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
}
