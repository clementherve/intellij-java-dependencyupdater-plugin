package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;

/**
 * Platform tests for GradlePsiParser.
 */
public class GradlePsiParserTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, "");
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
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

        GradlePsiParser parser = new GradlePsiParser();
        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertEquals(3, dependencies.size());

        DependencyInfo guava = dependencies.getFirst();
        assertEquals("com.google.guava", guava.group());
        assertEquals("guava", guava.artifact());
        assertEquals("31.1-jre", guava.currentVersion());
        assertEquals("implementation", guava.configurationName());
        assertFalse(guava.isVersionVariable());

        DependencyInfo commonsLang = dependencies.get(1);
        assertEquals("org.apache.commons", commonsLang.group());
        assertEquals("commons-lang3", commonsLang.artifact());
        assertEquals("3.12.0", commonsLang.currentVersion());
        assertEquals("api", commonsLang.configurationName());

        DependencyInfo junit = dependencies.get(2);
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
        GradlePsiParser parser = new GradlePsiParser();

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertEquals(2, dependencies.size());

        DependencyInfo guava = dependencies.getFirst();
        assertEquals("com.google.guava", guava.group());
        assertEquals("guava", guava.artifact());
        assertEquals("31.1-jre", guava.currentVersion());
        assertEquals("implementation", guava.configurationName());

        DependencyInfo springBoot = dependencies.get(1);
        assertEquals("org.springframework.boot", springBoot.group());
        assertEquals("spring-boot-starter", springBoot.artifact());
        assertEquals("2.7.0", springBoot.currentVersion());
        assertEquals("api", springBoot.configurationName());
    }

    public void test_parse_with_variables() {
        String content = """
                ext {
                    guavaVersion = '31.1-jre'
                }
                
                dependencies {
                    implementation "com.google.guava:guava:$guavaVersion"
                    api 'org.apache.commons:commons-lang3:3.12.0'
                }
                """;

        PsiFile file = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, content);
        GradlePsiParser parser = new GradlePsiParser();

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertEquals(2, dependencies.size());

        DependencyInfo firstDependency = dependencies.getFirst();
        assertEquals("com.google.guava", firstDependency.group());
        assertEquals("guava", firstDependency.artifact());
        assertTrue(firstDependency.isVersionVariable()); // todo: investigate why it's false
        assertEquals("guavaVersion", firstDependency.variableName());
        assertEquals("31.1-jre", firstDependency.currentVersion());

    }
}
