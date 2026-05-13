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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
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

        // Only process leaf elements that are configuration names
        if (element.getFirstChild() != null) {
            return;
        }

        String text = element.getText();
        if (text == null || !isConfigurationName(text)) {
            return;
        }

        DependencyParser parser = DependencyParserFactory.getParser(file);
        if (parser == null) {
            LOGGER.debug("Skipping dependency annotator because parser is null");
            return;
        }

        List<DependencyInfo> dependencies = parser.parseDependencies(file);
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);

        // Get the document to check line numbers
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            LOGGER.debug("Document is null");
            return;
        }

        // Get the line number of the configuration name element
        int elementLine = document.getLineNumber(element.getTextOffset());

        for (DependencyInfo dependency : dependencies) {
            // Must match the configuration name
            if (!text.equals(dependency.getConfigurationName())) {
                continue;
            }

            if (dependency.getPsiElementPointer() == null) {
                continue;
            }

            PsiElement depElement = dependency.getPsiElementPointer().getElement();
            if (depElement == null) {
                continue;
            }

            // Check if they're on the same line
            int depLine = document.getLineNumber(depElement.getTextOffset());

            if (elementLine == depLine) {
                LOGGER.debug("Found matching dependency on line {}: {}", elementLine, dependency.getArtifact());

                // Check cache for update (never block on network)
                VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

                if (candidate != null) {
                    // Add inline annotation on the configuration name
                    String message = dependency.getArtifact() + " → " + candidate.getVersion() + " available";
                    LOGGER.debug("Annotating with message: {}", message);

                    holder.newAnnotation(HighlightSeverity.WARNING, message)
                            .range(element.getTextRange())
                            .tooltip(message)
                            .create();
                } else {
                    LOGGER.debug("No cached update for: {}", dependency.getArtifact());
                    // Schedule background fetch for next time
                    service.scheduleCacheWarmup(dependency);
                }

                // Found the matching dependency, no need to continue
                break;
            }
        }
    }

    /**
     * TODO: extract in a util
     */
    private boolean isConfigurationName(@NotNull String text) {
        return text.equals("implementation") ||
                text.equals("api") ||
                text.equals("compileOnly") ||
                text.equals("runtimeOnly") ||
                text.equals("testImplementation") ||
                text.equals("testCompileOnly") ||
                text.equals("testRuntimeOnly") ||
                text.equals("annotationProcessor") ||
                text.equals("kapt");
    }
}
