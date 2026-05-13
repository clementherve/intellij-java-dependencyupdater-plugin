package com.github.clementherve.intellijjavadependencyupdaterplugin.annotator;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
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

        if (!SupportedFilesUtil.isSupportedFile(file.getName())) {
            LOGGER.debug("Skipping dependency annotator because file is not build.gradle");
            return;
        }

        Project project = element.getProject();
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);
        final boolean dontShowInlineHints = !settings.isShowInlayHints();
        if (dontShowInlineHints) {
            LOGGER.debug("Skipping dependency annotator because showInlineHints is disabled");
            return;
        }

        // Check if this element looks like a version string (either dependency or plugin)
        String text = element.getText();
        if (text == null) {
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

            PsiElement dependencyElement = dependency.psiElementPointer().getElement();
            if (dependencyElement == null) {
                continue;
            }

            if (dependencyElement == element) {
                VersionCandidate candidate = service.getFromCache(dependency);

                if (candidate != null) {
                    String message = dependency.artifact() + " → " + candidate.version() + " available";
                    LOGGER.debug("Annotating with message: {}", message);

                    holder.newAnnotation(HighlightSeverity.WARNING, message)
                            .range(element.getTextRange())
                            .tooltip(message)
                            .create();
                } else {
                    service.scheduleCacheWarmup(dependency);
                }

                break;
            }
        }
    }
}
