package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.annotator;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Annotator that shows inline hints for available dependency updates.
 * Draws a squiggly line below the dependencies (plugin block or dependency block)
 */
public class DependencyUpdateAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder annotation) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        if (!SupportedBuildFile.isSupportedFile(file.getName())) {
            return;
        }

        Project project = element.getProject();
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();
        final boolean dontShowInlineHints = !settings.isShowInlayHints();
        if (dontShowInlineHints) {
            return;
        }

        String text = element.getText();
        if (text == null) {
            return;
        }

        BuildFileParser parser = BuildFileParserFactory.getParser(file);
        if (parser == null) {
            return;
        }

        List<Dependency> dependencies = CachedValuesManager.getCachedValue(file,
                () -> CachedValueProvider.Result.create(parser.parseDependencies(file), file));

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);

        for (Dependency dependency : dependencies) {
            final SmartPsiElementPointer<PsiElement> elementPointer = dependency.psiElementPointer();
            if (elementPointer == null) {
                continue;
            }

            PsiElement dependencyElement = elementPointer.getElement();
            if (dependencyElement == null) {
                continue;
            }

            if (dependencyElement != element) {
                continue;
            }

            VersionCandidate candidate = service.getFromCache(dependency);

            if (candidate == null) {
                break;
            }

            // todo: extract message to use i18n
            // todo: allow customising the severity
            String message = String.format("%s → %s available", dependency.artifact(), candidate.version());
            annotation.newAnnotation(HighlightSeverity.WARNING, message).range(element.getTextRange()).tooltip(message).create();
            break;
        }
    }
}
