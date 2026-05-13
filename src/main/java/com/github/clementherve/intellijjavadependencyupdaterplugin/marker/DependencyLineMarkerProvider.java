package com.github.clementherve.intellijjavadependencyupdaterplugin.marker;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides gutter icons for dependencies with available updates.
 */
public class DependencyLineMarkerProvider implements LineMarkerProvider {

    private static final Logger LOG = Logger.getInstance(DependencyLineMarkerProvider.class);

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Early return for non-relevant files
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        String fileName = containingFile.getName();
        final boolean isNotBuildGradle = !"build.gradle".equals(fileName);
        if (isNotBuildGradle) {
            return null;
        }

        // Check if gutter icons are enabled
        Project project = element.getProject();
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);
        if (!settings.isShowGutterIcons()) {
            return null;
        }

        // We want to mark the first element of method calls (the configuration name)
        // For performance, we only check specific element types
        if (!isRelevantElement(element)) {
            return null;
        }

        try {
            // Parse dependencies from the file
            DependencyParser parser = DependencyParserFactory.getParser(containingFile);
            if (parser == null) {
                return null;
            }

            List<DependencyInfo> dependencies = parser.parseDependencies(containingFile);

            // Find if this element corresponds to a dependency
            for (DependencyInfo dependency : dependencies) {
                if (isElementForDependency(element, dependency)) {
                    return createLineMarkerForDependency(element, dependency, project);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error creating line marker", e);
        }

        return null;
    }

    /**
     * Checks if the element is relevant for line marker creation.
     * We target LEAF elements (identifiers) for performance.
     */
    private boolean isRelevantElement(@NotNull PsiElement element) {
        // IMPORTANT: Only target leaf elements for performance
        if (element.getFirstChild() != null) {
            return false; // Not a leaf element
        }

        String text = element.getText();
        return text != null && (
            text.equals("implementation") ||
            text.equals("api") ||
            text.equals("compileOnly") ||
            text.equals("runtimeOnly") ||
            text.equals("testImplementation") ||
            text.equals("testCompileOnly") ||
            text.equals("testRuntimeOnly") ||
            text.equals("annotationProcessor") ||
            text.equals("kapt")
        );
    }

    /**
     * Checks if the element corresponds to the given dependency.
     */
    private boolean isElementForDependency(@NotNull PsiElement element, @NotNull DependencyInfo dependency) {
        return element.getText().equals(dependency.getConfigurationName());
    }

    /**
     * Creates a line marker info for a dependency with available update.
     */
    @Nullable
    private LineMarkerInfo<?> createLineMarkerForDependency(@NotNull PsiElement element,
                                                            @NotNull DependencyInfo dependency,
                                                            @NotNull Project project) {
        // IMPORTANT: Only check cache synchronously - NEVER make network calls from line marker provider!
        // Network calls would block the EDT and violate IntelliJ threading model
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

        if (candidate == null) {
            // No cached result - schedule background fetch but return null for now
            // Next highlighting pass will pick up the result
            service.scheduleCacheWarmup(dependency);
            return null;
        }

        // Create gutter icon
        String tooltipText = DependencyUpdaterBundle.message(
            "gutter.updateAvailable",
            dependency.getArtifact(),
            candidate.getVersion()
        );

        return new LineMarkerInfo<>(
            element,
            element.getTextRange(),
            AllIcons.Gutter.Unique, // Using a built-in icon
            psiElement -> tooltipText,
            null, // No navigation handler for now
            GutterIconRenderer.Alignment.RIGHT,
            () -> tooltipText
        );
    }
}
