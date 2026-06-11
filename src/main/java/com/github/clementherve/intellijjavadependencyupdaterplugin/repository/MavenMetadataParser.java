package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the version strings from a Maven {@code maven-metadata.xml} document.
 * Shared by every repository that exposes the standard Maven metadata layout:
 * <pre>
 * &lt;metadata&gt;
 *   &lt;versioning&gt;
 *     &lt;versions&gt;
 *       &lt;version&gt;1.0.0&lt;/version&gt;
 *       &lt;version&gt;1.1.0&lt;/version&gt;
 *     &lt;/versions&gt;
 *   &lt;/versioning&gt;
 * &lt;/metadata&gt;
 * </pre>
 */
public final class MavenMetadataParser {

    private static final Logger LOGGER = Logger.getInstance(MavenMetadataParser.class);
    private static final String OPEN_TAG = "<version>";
    private static final String CLOSE_TAG = "</version>";

    private MavenMetadataParser() {
    }

    /**
     * Returns the versions declared in the given metadata document, in document order.
     * Returns an empty list when the document contains no versions or cannot be read.
     */
    @NotNull
    public static List<String> parseVersions(@NotNull String xmlContent) {
        List<String> versions = new ArrayList<>();

        try {
            int position = 0;
            while (true) {
                int versionStart = xmlContent.indexOf(OPEN_TAG, position);
                if (versionStart == -1) break;

                int versionEnd = xmlContent.indexOf(CLOSE_TAG, versionStart);
                if (versionEnd == -1) break;

                String version = xmlContent.substring(versionStart + OPEN_TAG.length(), versionEnd).trim();
                if (!version.isEmpty()) {
                    versions.add(version);
                }

                position = versionEnd + CLOSE_TAG.length();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse Maven metadata XML", exception);
        }

        return versions;
    }
}
