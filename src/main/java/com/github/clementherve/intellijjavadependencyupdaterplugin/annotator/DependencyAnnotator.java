package com.github.clementherve.intellijjavadependencyupdaterplugin.annotator;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Annotator that shows inline hints for available dependency updates.
 */
public class DependencyAnnotator implements Annotator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyAnnotator.class);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            LOGGER.debug("File is null");
            return;
        }

        String fileName = file.getName();
        final boolean isNotBuildGradleFile = !"build.gradle".equals(fileName) && !"build.gradle.kts".equals(fileName);
        if (isNotBuildGradleFile) {
            LOGGER.debug("Skipping dependency annotator because file is not build.gradle or build.gradle.kts");
            return;
        }

        Project project = element.getProject();
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);
        final boolean dontShowInlineHints = !settings.isShowInlayHints();
        if (dontShowInlineHints) {
            LOGGER.debug("Skipping dependency annotator because showInlineHints is disabled");
            return;
        }

        // Check if this element looks like a dependency string
        String text = element.getText();
        if (text == null || !text.contains(":")) {
            return;
        }

        DependencyParser parser = DependencyParserFactory.getParser(file);
        if (parser == null) {
            return;
        }

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);

        // Check if this element is one of the parsed dependency version strings
        for (DependencyInfo dependency : dependencies) {
            if (dependency.psiElementPointer() == null) {
                continue;
            }

            PsiElement depElement = dependency.psiElementPointer().getElement();
            if (depElement == null) {
                continue;
            }

            // Check if this is the exact element we're looking for (by reference only)
            if (depElement == element) {
                LOGGER.debug("Found matching dependency element: {}", dependency.artifact());

                VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

                if (candidate != null) {
                    // Add inline annotation on the version string itself
                    String message = dependency.artifact() + " → " + candidate.version() + " available";
                    LOGGER.debug("Annotating with message: {}", message);

                    holder.newAnnotation(HighlightSeverity.WARNING, message)
                            .range(element.getTextRange())
                            .tooltip(message)
                            .create();
                } else {
                    LOGGER.debug("No cached update for: {}", dependency.artifact());
                    // Schedule background fetch for next time
                    service.scheduleCacheWarmup(dependency);
                }

                // Found the matching dependency, no need to continue
                break;
            }
        }
    }
}
