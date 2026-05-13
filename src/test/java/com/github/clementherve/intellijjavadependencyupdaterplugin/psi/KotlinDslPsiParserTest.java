package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * Platform tests for KotlinDslPsiParser.
 */
public class KotlinDslPsiParserTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void test_parse_simple_string_notation() {
        PsiFile file = myFixture.configureByFile("parsing/simple.gradle.kts");
        KotlinDslPsiParser parser = new KotlinDslPsiParser();

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertEquals(3, dependencies.size());

        DependencyInfo guava = dependencies.get(0);
        assertEquals("com.google.guava", guava.getGroup());
        assertEquals("guava", guava.getArtifact());
        assertEquals("31.1-jre", guava.getCurrentVersion());
        assertEquals("implementation", guava.getConfigurationName());
        assertFalse(guava.isVersionVariable());

        DependencyInfo commonsLang = dependencies.get(1);
        assertEquals("org.apache.commons", commonsLang.getGroup());
        assertEquals("commons-lang3", commonsLang.getArtifact());
        assertEquals("3.12.0", commonsLang.getCurrentVersion());
        assertEquals("api", commonsLang.getConfigurationName());

        DependencyInfo junit = dependencies.get(2);
        assertEquals("junit", junit.getGroup());
        assertEquals("junit", junit.getArtifact());
        assertEquals("4.13.2", junit.getCurrentVersion());
        assertEquals("testImplementation", junit.getConfigurationName());
    }

    public void test_parse_with_variables() {
        PsiFile file = myFixture.configureByFile("parsing/variables.gradle.kts");
        KotlinDslPsiParser parser = new KotlinDslPsiParser();

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertEquals(1, dependencies.size());

        DependencyInfo guava = dependencies.get(0);
        assertEquals("com.google.guava", guava.getGroup());
        assertEquals("guava", guava.getArtifact());
        assertEquals("$guavaVersion", guava.getCurrentVersion());
        assertTrue(guava.isVersionVariable());
        assertEquals("guavaVersion", guava.getVariableName());
    }

    public void test_canParse_returns_true_for_build_gradle_kts() {
        PsiFile file = myFixture.configureByFile("parsing/simple.gradle.kts");
        KotlinDslPsiParser parser = new KotlinDslPsiParser();

        assertTrue(parser.canParse(file));
    }

    public void test_empty_file_returns_empty_list() {
        PsiFile file = myFixture.configureByText("build.gradle.kts", "");
        KotlinDslPsiParser parser = new KotlinDslPsiParser();

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        assertTrue(dependencies.isEmpty());
    }
}
