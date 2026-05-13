package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for fetching plugin versions from Gradle Plugin Portal.
 * Plugins are backed by Maven artifacts at https://plugins.gradle.org/m2/
 */
public class GradlePluginPortalClient implements VersionRepository {

    private static final String PLUGIN_PORTAL_MAVEN = "https://plugins.gradle.org/m2/";
    private final HttpClient httpClient;

    public GradlePluginPortalClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @NotNull
    @Override
    public String getSourceName() {
        return "Gradle Plugin Portal";
    }

    @NotNull
    @Override
    public List<String> fetchVersions(@NotNull String group, @NotNull String pluginId) throws IOException {
        // Gradle plugins use the plugin ID as the group, with artifact = plugin ID + ".gradle.plugin"
        // Example: com.github.ben-manes.versions -> group: com.github.ben-manes.versions, artifact: com.github.ben-manes.versions.gradle.plugin
        String pluginArtifact = pluginId + ".gradle.plugin";

        // Build URL: https://plugins.gradle.org/m2/{plugin_id_as_path}/{artifact}/maven-metadata.xml
        String pluginIdPath = pluginId.replace('.', '/');
        String url = PLUGIN_PORTAL_MAVEN + pluginIdPath + "/" + pluginArtifact + "/maven-metadata.xml";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/xml")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Plugin Portal returned status " + response.statusCode() + " for plugin: " + pluginId);
            }

            return parseVersionsFromMavenMetadata(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    /**
     * Parses versions from Maven metadata XML.
     */
    @NotNull
    private List<String> parseVersionsFromMavenMetadata(@NotNull String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            NodeList versionNodes = doc.getElementsByTagName("version");
            List<String> versions = new ArrayList<>();

            for (int i = 0; i < versionNodes.getLength(); i++) {
                Element versionElement = (Element) versionNodes.item(i);
                String version = versionElement.getTextContent().trim();
                if (!version.isEmpty()) {
                    versions.add(version);
                }
            }

            // Reverse to get newest first (Maven metadata has oldest first)
            Collections.reverse(versions);
            return versions;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
