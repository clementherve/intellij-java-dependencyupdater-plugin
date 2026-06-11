package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;

/**
 * Platform tests for GradleBuildFileParser.
 */
public class GradleBuildFileParserTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, "");
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/unused";
    }

    public void test_parse_simple_string_notation() {
        String content = """
                dependencies {
                    implementation 'com.google.guava:guava:31.1-jre'
                    api 'org.apache.commons:commons-lang3:3.12.0'
                    testImplementation 'junit:junit:4.13.2'
                }
                """;

        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        myFixture.setTestDataPath("src/test/testData");

        GradleBuildFileParser parser = new GradleBuildFileParser();
        List<Dependency> dependencies = parser.parseDependencies(file);

        assertEquals(3, dependencies.size());

        Dependency guava = dependencies.getFirst();
        assertEquals("com.google.guava", guava.group());
        assertEquals("guava", guava.artifact());
        assertEquals("31.1-jre", guava.currentVersion());
        assertEquals("implementation", guava.configurationName());
        assertFalse(guava.isVersionVariable());

        Dependency commonsLang = dependencies.get(1);
        assertEquals("org.apache.commons", commonsLang.group());
        assertEquals("commons-lang3", commonsLang.artifact());
        assertEquals("3.12.0", commonsLang.currentVersion());
        assertEquals("api", commonsLang.configurationName());

        Dependency junit = dependencies.get(2);
        assertEquals("junit", junit.group());
        assertEquals("junit", junit.artifact());
        assertEquals("4.13.2", junit.currentVersion());
        assertEquals("testImplementation", junit.configurationName());
    }

    public void test_parse_map_notation() {
        String content = """
                dependencies {
                    implementation group: 'com.google.guava', name: 'guava', version: '31.1-jre'
                    api group: 'org.springframework.boot', name: 'spring-boot-starter', version: '2.7.0'
                }
                """;
        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        GradleBuildFileParser parser = new GradleBuildFileParser();

        List<Dependency> dependencies = parser.parseDependencies(file);

        assertEquals(2, dependencies.size());

        Dependency guava = dependencies.getFirst();
        assertEquals("com.google.guava", guava.group());
        assertEquals("guava", guava.artifact());
        assertEquals("31.1-jre", guava.currentVersion());
        assertEquals("implementation", guava.configurationName());

        Dependency springBoot = dependencies.get(1);
        assertEquals("org.springframework.boot", springBoot.group());
        assertEquals("spring-boot-starter", springBoot.artifact());
        assertEquals("2.7.0", springBoot.currentVersion());
        assertEquals("api", springBoot.configurationName());
    }

    public void test_parse_with_variables() {
        String content = """
                ext {
                    guavaVersion = '31.1-jre'
                    test_version = '3.12.0'
                }
                
                dependencies {
                implementation "com.google.guava:guava:$guavaVersion"
                    implementation "fr.test.extension:extension:${test_version}"
                    api 'org.apache.commons:commons-lang3:3.12.0'
                }
                """;

        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        GradleBuildFileParser parser = new GradleBuildFileParser();

        List<Dependency> dependencies = parser.parseDependencies(file);

        assertEquals(3, dependencies.size());

        Dependency firstDependency = dependencies.getFirst();
        assertEquals("com.google.guava", firstDependency.group());
        assertEquals("guava", firstDependency.artifact());
        assertTrue(firstDependency.isVersionVariable());
        assertEquals("guavaVersion", firstDependency.variableName());
        assertEquals("31.1-jre", firstDependency.currentVersion());

        Dependency secondDependency = dependencies.get(1);
        assertEquals("fr.test.extension", secondDependency.group());
        assertEquals("extension", secondDependency.artifact());
        assertTrue(secondDependency.isVersionVariable());
        assertEquals("test_version", secondDependency.variableName());
        assertEquals("3.12.0", secondDependency.currentVersion());

        Dependency thirdDependency = dependencies.get(2);
        assertEquals("org.apache.commons", thirdDependency.group());
        assertEquals("commons-lang3", thirdDependency.artifact());
        assertEquals("3.12.0", thirdDependency.currentVersion());
        assertFalse(thirdDependency.isVersionVariable());

    }

    public void test_parse_plugins_block() {
        String content = """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.5.6'
                    id 'io.spring.dependency-management' version '1.1.7'
                    id 'org.graalvm.buildtools.native' version '0.10.5'
                    id "com.github.ben-manes.versions" version '0.53.0'
                }
                """;

        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        GradleBuildFileParser parser = new GradleBuildFileParser();

        List<Dependency> plugins = parser.parseDependencies(file);

        // Should find 4 plugins (java plugin has no version, so it won't be parsed)
        assertEquals(4, plugins.size());

        // Test Spring Boot plugin
        Dependency springBoot = plugins.getFirst();
        assertEquals("", springBoot.group()); // Empty group for plugins
        assertEquals("org.springframework.boot", springBoot.artifact());
        assertEquals("3.5.6", springBoot.currentVersion());
        assertEquals("plugin", springBoot.configurationName());
        assertFalse(springBoot.isVersionVariable());

        // Test Dependency Management plugin
        Dependency depMgmt = plugins.get(1);
        assertEquals("", depMgmt.group());
        assertEquals("io.spring.dependency-management", depMgmt.artifact());
        assertEquals("1.1.7", depMgmt.currentVersion());
        assertEquals("plugin", depMgmt.configurationName());

        // Test GraalVM plugin
        Dependency graalvm = plugins.get(2);
        assertEquals("", graalvm.group());
        assertEquals("org.graalvm.buildtools.native", graalvm.artifact());
        assertEquals("0.10.5", graalvm.currentVersion());
        assertEquals("plugin", graalvm.configurationName());

        // Test Versions plugin (uses double quotes)
        Dependency versions = plugins.get(3);
        assertEquals("", versions.group());
        assertEquals("com.github.ben-manes.versions", versions.artifact());
        assertEquals("0.53.0", versions.currentVersion());
        assertEquals("plugin", versions.configurationName());
    }

    public void test_parse_plugins_and_dependencies_together() {
        String content = """
                plugins {
                    id 'org.springframework.boot' version '3.5.6'
                    id 'io.spring.dependency-management' version '1.1.7'
                }

                dependencies {
                    implementation 'com.google.guava:guava:31.1-jre'
                    testImplementation 'junit:junit:4.13.2'
                }
                """;

        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        GradleBuildFileParser parser = new GradleBuildFileParser();

        List<Dependency> all = parser.parseDependencies(file);

        // Should find 2 plugins + 2 dependencies = 4 total
        assertEquals(4, all.size());

        // Verify plugins are parsed
        long pluginCount = all.stream()
                .filter(dep -> "plugin".equals(dep.configurationName()))
                .count();
        assertEquals(2, pluginCount);

        // Verify regular dependencies are parsed
        long dependencyCount = all.stream()
                .filter(dep -> !"plugin".equals(dep.configurationName()))
                .count();
        assertEquals(2, dependencyCount);
    }
}
