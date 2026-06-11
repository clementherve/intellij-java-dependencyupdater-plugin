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
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Annotator that shows inline hints for available dependency updates.
 * Draws a squiggly line below the dependencies (plugin block or dependency block)
 */
public class DependencyAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder annotation) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        if (!SupportedFilesUtil.isSupportedFile(file.getName())) {
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

        DependencyParser parser = DependencyParserFactory.getParser(file);
        if (parser == null) {
            return;
        }

        List<DependencyInfo> dependencies = CachedValuesManager.getCachedValue(file,
                () -> CachedValueProvider.Result.create(parser.parseDependencies(file), file));

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);

        for (DependencyInfo dependency : dependencies) {
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
