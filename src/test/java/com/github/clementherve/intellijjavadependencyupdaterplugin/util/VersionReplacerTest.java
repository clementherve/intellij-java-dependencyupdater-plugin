package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.GradlePsiParser;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Comparator;
import java.util.List;

/**
 * Platform tests for {@link VersionReplacer}.
 */
public class VersionReplacerTest extends BasePlatformTestCase {

    private static final String BUILD_GRADLE = """
            dependencies {
                implementation 'io.swagger.core.v3:swagger-annotations:2.2.43'
                implementation "com.google.code.findbugs:jsr305:3.0.2"
                implementation "com.fasterxml.jackson.core:jackson-core:2.21.2"
                implementation "com.fasterxml.jackson.core:jackson-annotations:2.21"
                implementation "com.fasterxml.jackson.core:jackson-databind:2.21.2"
            }
            """;

    private static final String FULL_BUILD_GRADLE = """
            plugins {
                id 'java-library'
                id 'eclipse'
                id "com.github.ben-manes.versions" version "0.53.0"
            }

            repositories {
                mavenCentral()
                maven { url "https://repo1.maven.org/maven2" }
            }

            apply plugin: 'java'

            java {
                toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            }

            ext {
                junit_version = "5.13.4"
            }

            dependencies {
                implementation 'io.swagger.core.v3:swagger-annotations:2.2.43'
                implementation "com.google.code.findbugs:jsr305:3.0.2"
                implementation "com.fasterxml.jackson.core:jackson-core:2.21.2"
                implementation "com.fasterxml.jackson.core:jackson-annotations:2.21"
                implementation "com.fasterxml.jackson.core:jackson-databind:2.21.2"
                implementation "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2"
                implementation "org.openapitools:jackson-databind-nullable:0.2.10"
                implementation "jakarta.annotation:jakarta.annotation-api:3.0.0"
                implementation "javax.annotation:javax.annotation-api:1.3.2"
                testImplementation "org.junit.jupiter:junit-jupiter-api:$junit_version"
                testRuntimeOnly "org.junit.jupiter:junit-jupiter-engine:$junit_version"
                testRuntimeOnly "org.junit.platform:junit-platform-launcher"
            }
            """;

    private PsiFile configure() {
        return myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, BUILD_GRADLE);
    }

    private PsiFile configureFull() {
        return myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, FULL_BUILD_GRADLE);
    }

    private DependencyInfo findByArtifact(List<DependencyInfo> dependencies, String artifact) {
        return dependencies.stream()
                .filter(d -> artifact.equals(d.artifact()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dependency not found: " + artifact));
    }

    private String update(PsiFile file, DependencyInfo dependency, String newVersion) {
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                VersionReplacer.applyUpdateInWriteAction(getProject(), dependency, newVersion));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return file.getText();
    }

    /**
     * Updating jackson-annotations (2.21) must only touch its own line, not the
     * 2.21.2 lines whose versions start with the same "2.21" prefix.
     */
    public void test_update_version_that_is_a_prefix_of_another_version() {
        PsiFile file = configure();
        GradlePsiParser parser = new GradlePsiParser();
        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        DependencyInfo annotations = findByArtifact(dependencies, "jackson-annotations");
        String result = update(file, annotations, "2.99");

        assertTrue("jackson-annotations should be updated to 2.99",
                result.contains("jackson-annotations:2.99"));
        assertTrue("jackson-core must keep its full 2.21.2 version",
                result.contains("jackson-core:2.21.2"));
        assertTrue("jackson-databind must keep its full 2.21.2 version",
                result.contains("jackson-databind:2.21.2"));
    }

    /**
     * Updating a 2.21.2 dependency must not be affected by the shorter 2.21
     * version present elsewhere in the file.
     */
    public void test_update_longer_version_keeps_other_lines_intact() {
        PsiFile file = configure();
        GradlePsiParser parser = new GradlePsiParser();
        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        DependencyInfo core = findByArtifact(dependencies, "jackson-core");
        String result = update(file, core, "2.22.0");

        assertTrue("jackson-core should be updated to 2.22.0",
                result.contains("jackson-core:2.22.0"));
        assertTrue("jackson-annotations must stay at 2.21",
                result.contains("jackson-annotations:2.21\""));
        assertTrue("jackson-databind must stay at 2.21.2",
                result.contains("jackson-databind:2.21.2"));
    }

    /**
     * Sanity-check that every coordinate is parsed with the exact version.
     */
    public void test_parsing_versions_are_exact() {
        PsiFile file = configure();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        assertEquals("2.2.43", findByArtifact(dependencies, "swagger-annotations").currentVersion());
        assertEquals("3.0.2", findByArtifact(dependencies, "jsr305").currentVersion());
        assertEquals("2.21.2", findByArtifact(dependencies, "jackson-core").currentVersion());
        assertEquals("2.21", findByArtifact(dependencies, "jackson-annotations").currentVersion());
        assertEquals("2.21.2", findByArtifact(dependencies, "jackson-databind").currentVersion());
    }

    /**
     * Reproduces the "Update All" flow: every dependency updated within a single
     * write command, applied in reverse document order, exactly as
     * UpdateAllDependenciesAction does.
     */
    public void test_update_all_dependencies_in_one_write_action() {
        PsiFile file = configure();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        DependencyInfo swagger = findByArtifact(dependencies, "swagger-annotations");
        DependencyInfo jsr305 = findByArtifact(dependencies, "jsr305");
        DependencyInfo core = findByArtifact(dependencies, "jackson-core");
        DependencyInfo annotations = findByArtifact(dependencies, "jackson-annotations");
        DependencyInfo databind = findByArtifact(dependencies, "jackson-databind");

        List<DependencyInfo> reverseOrdered = dependencies.stream()
                .sorted(Comparator.comparingInt(this::offsetOf).reversed())
                .toList();

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            for (DependencyInfo dependency : reverseOrdered) {
                VersionReplacer.applyUpdateInWriteAction(getProject(), dependency, newVersionFor(dependency));
            }
        });
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        String result = file.getText();
        assertTrue(result, result.contains("swagger-annotations:2.2.44"));
        assertTrue(result, result.contains("jsr305:3.0.3"));
        assertTrue(result, result.contains("jackson-core:2.22.0"));
        assertTrue(result, result.contains("jackson-annotations:2.99\""));
        assertTrue(result, result.contains("jackson-databind:2.22.0"));
    }

    /**
     * When the version string also appears earlier in the coordinate (here the
     * artifact "client-2.0" embeds "2.0"), a naive substring replace rewrites the
     * artifact name too. Only the trailing version token must change.
     */
    public void test_version_substring_in_artifact_is_not_corrupted() {
        String content = """
                dependencies {
                    implementation 'org.example:client-2.0:2.0'
                }
                """;
        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        DependencyInfo dependency = new GradlePsiParser().parseDependencies(file).getFirst();

        String result = update(file, dependency, "3.0");

        assertTrue(result, result.contains("org.example:client-2.0:3.0"));
        assertFalse("the artifact name must not be rewritten", result.contains("client-3.0"));
    }

    /**
     * Same failure mode driven by a duplicated version segment: the version "2.0"
     * appears both in the group ("2.0") and as the actual version.
     */
    public void test_version_substring_in_group_is_not_corrupted() {
        String content = """
                dependencies {
                    implementation "com.example.v2:web:2"
                }
                """;
        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        DependencyInfo dependency = new GradlePsiParser().parseDependencies(file).getFirst();

        String result = update(file, dependency, "3");

        assertTrue(result, result.contains("com.example.v2:web:3"));
        assertFalse("the group must not be rewritten", result.contains("com.example.v3"));
    }

    // ---- Tests for the full real-world build.gradle ----------------------------

    /**
     * Documents exactly which declarations the parser detects in the full file.
     * Notably: the standalone `version '0.0.1'`, `apply plugin: ...`, `group ...`
     * and the `maven { url ... }` repo must NOT be picked up, and
     * `junit-platform-launcher` (no version) is silently skipped.
     */
    public void test_full_file_detected_dependencies() {
        PsiFile file = configureFull();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        List<String> coordinates = dependencies.stream()
                .map(d -> d.getCoordinates() + ":" + d.currentVersion())
                .sorted()
                .toList();

        assertEquals(String.join("\n", coordinates), List.of(
                ":com.github.ben-manes.versions:0.53.0",
                "com.fasterxml.jackson.core:jackson-annotations:2.21",
                "com.fasterxml.jackson.core:jackson-core:2.21.2",
                "com.fasterxml.jackson.core:jackson-databind:2.21.2",
                "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2",
                "com.google.code.findbugs:jsr305:3.0.2",
                "io.swagger.core.v3:swagger-annotations:2.2.43",
                "jakarta.annotation:jakarta.annotation-api:3.0.0",
                "javax.annotation:javax.annotation-api:1.3.2",
                "org.junit.jupiter:junit-jupiter-api:5.13.4",
                "org.junit.jupiter:junit-jupiter-engine:5.13.4",
                "org.openapitools:jackson-databind-nullable:0.2.10"
        ), coordinates);

        // The versionless dependency must not be parsed (and must never be corrupted).
        assertTrue(dependencies.stream().noneMatch(d -> "junit-platform-launcher".equals(d.artifact())));
    }

    /**
     * jackson-datatype-jsr310 shares the 2.21.2 version with sibling jackson
     * modules; updating it must only touch its own declaration.
     */
    public void test_full_file_update_jackson_datatype() {
        PsiFile file = configureFull();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        String result = update(file, findByArtifact(dependencies, "jackson-datatype-jsr310"), "2.22.0");

        assertTrue(result, result.contains("jackson-datatype-jsr310:2.22.0"));
        assertTrue("sibling jackson-core must stay at 2.21.2", result.contains("jackson-core:2.21.2"));
        assertTrue("sibling jackson-databind must stay at 2.21.2", result.contains("jackson-databind:2.21.2"));
        assertTrue("jackson-annotations must stay at 2.21", result.contains("jackson-annotations:2.21\""));
    }

    /**
     * Both junit test dependencies resolve their version from the
     * {@code junit_version} ext variable. Updating one must rewrite the ext
     * variable so both usages reflect the new version.
     */
    public void test_full_file_update_variable_version() {
        PsiFile file = configureFull();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        DependencyInfo junitApi = findByArtifact(dependencies, "junit-jupiter-api");
        assertTrue("junit-jupiter-api should be variable-based", junitApi.isVersionVariable());
        assertEquals("junit_version", junitApi.variableName());
        assertEquals("5.13.4", junitApi.currentVersion());

        String result = update(file, junitApi, "5.14.0");

        assertTrue("ext variable should be updated", result.contains("junit_version = \"5.14.0\""));
        assertFalse("old version must be gone", result.contains("5.13.4"));
        // Both usages still reference the variable, so both now resolve to 5.14.0.
        assertTrue(result.contains("junit-jupiter-api:$junit_version"));
        assertTrue(result.contains("junit-jupiter-engine:$junit_version"));
    }

    /**
     * The plugin declaration in the plugins block must update its version literal.
     */
    public void test_full_file_update_plugin_version() {
        PsiFile file = configureFull();
        List<DependencyInfo> dependencies = new GradlePsiParser().parseDependencies(file);

        DependencyInfo plugin = findByArtifact(dependencies, "com.github.ben-manes.versions");
        assertEquals("plugin", plugin.configurationName());

        String result = update(file, plugin, "0.54.0");

        assertTrue(result, result.contains("id \"com.github.ben-manes.versions\" version \"0.54.0\""));
        // The standalone project version must be untouched.
        assertTrue("project version '0.0.1' must be untouched", result.contains("version '0.0.1'"));
    }

    private String newVersionFor(DependencyInfo dependency) {
        return switch (dependency.artifact()) {
            case "swagger-annotations" -> "2.2.44";
            case "jsr305" -> "3.0.3";
            case "jackson-core", "jackson-databind" -> "2.22.0";
            case "jackson-annotations" -> "2.99";
            default -> dependency.currentVersion();
        };
    }

    private int offsetOf(DependencyInfo dependency) {
        SmartPsiElementPointer<PsiElement> pointer = dependency.psiElementPointer();
        PsiElement element = pointer != null ? pointer.getElement() : null;
        return element != null ? element.getTextOffset() : 0;
    }
}
