package com.github.clementherve.intellijjavadependencyupdaterplugin.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SemanticVersion;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionUpdateType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Intention action to update a dependency to the latest minor version.
 */
public class UpdateDependencyToMinorIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        DependencyInfo dependency = findDependencyAtElement(file, element);
        if (dependency == null) {
            return;
        }

        VersionCandidate candidate = findMinorUpdate(project, dependency);
        if (candidate == null) {
            return;
        }

        VersionReplacer.applyUpdate(project, dependency, candidate.getVersion());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return false;
        }

        String fileName = file.getName();
        if (!"build.gradle".equals(fileName) && !"build.gradle.kts".equals(fileName)) {
            return false;
        }

        DependencyInfo dependency = findDependencyAtElement(file, element);
        if (dependency == null) {
            return false;
        }

        return findMinorUpdate(project, dependency) != null;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "Dependency Updater";
    }

    @NotNull
    @Override
    public String getText() {
        return "Update to latest minor version";
    }

    private DependencyInfo findDependencyAtElement(@NotNull PsiFile file, @NotNull PsiElement element) {
        DependencyParser parser = DependencyParserFactory.getParser(file);
        if (parser == null) {
            return null;
        }

        List<DependencyInfo> dependencies = parser.parseDependencies(file);
        int offset = element.getTextOffset();

        for (DependencyInfo dependency : dependencies) {
            if (dependency.getPsiElementPointer() != null) {
                PsiElement depElement = dependency.getPsiElementPointer().getElement();
                if (depElement != null) {
                    int start = depElement.getTextRange().getStartOffset();
                    int end = depElement.getTextRange().getEndOffset();
                    if (offset >= start && offset <= end) {
                        return dependency;
                    }
                }
            }
        }

        return null;
    }

    private VersionCandidate findMinorUpdate(@NotNull Project project, @NotNull DependencyInfo dependency) {
        SemanticVersion currentVersion = SemanticVersion.parse(dependency.getCurrentVersion());
        if (currentVersion == null) {
            return null;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        List<VersionCandidate> candidates = service.getAllCandidatesFromCache(dependency);

        return VersionUpdateType.findLatestByType(currentVersion, candidates, VersionUpdateType.UpdateType.MINOR);
    }
}
