package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.SemanticVersion;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.github.clementherve.intellijjavadependencyupdaterplugin.update.DependencyVersionWriter;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeClassifier;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeKind;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared behaviour for the intentions that bump a dependency to the latest version of a
 * specific {@link VersionChangeKind} (major, minor or patch). Subclasses only declare which
 * change kind they target and how they label themselves.
 */
public abstract class AbstractUpdateDependencyIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    /**
     * The change kind this intention bumps to.
     */
    @NotNull
    protected abstract VersionChangeKind changeKind();

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        Dependency dependency = DependencyAtCaretFinder.find(file, element);
        if (dependency == null) {
            return;
        }

        VersionCandidate candidate = findUpdate(project, dependency);
        if (candidate == null) {
            return;
        }

        DependencyVersionWriter.applyUpdateInWriteAction(project, dependency, candidate.version());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return false;
        }

        if (!SupportedBuildFile.isSupportedFile(file.getName())) {
            return false;
        }

        Dependency dependency = DependencyAtCaretFinder.find(file, element);
        if (dependency == null) {
            return false;
        }

        return findUpdate(project, dependency) != null;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return DependencyUpdaterBundle.message("intention.familyName");
    }

    @Nullable
    private VersionCandidate findUpdate(@NotNull Project project, @NotNull Dependency dependency) {
        SemanticVersion currentVersion = SemanticVersion.parse(dependency.currentVersion());
        if (currentVersion == null) {
            return null;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        List<VersionCandidate> candidates = service.getAllCandidatesFromCache(dependency);

        return VersionChangeClassifier.findLatestChange(currentVersion, candidates, changeKind());
    }
}
