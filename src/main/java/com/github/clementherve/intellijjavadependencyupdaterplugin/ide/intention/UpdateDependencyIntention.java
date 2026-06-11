package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.github.clementherve.intellijjavadependencyupdaterplugin.update.DependencyVersionWriter;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action to update a dependency to the latest version.
 */
public class UpdateDependencyIntention extends PsiElementBaseIntentionAction implements IntentionAction, PriorityAction {

    private VersionCandidate cachedCandidate;
    private Dependency cachedDependency;

    @NotNull
    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

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

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = cachedCandidate != null ? cachedCandidate
                : service.getFromCache(dependency);

        if (candidate == null) {
            service.scheduleCacheWarmup(dependency);
            return;
        }

        DependencyVersionWriter.applyUpdateInWriteAction(project, dependency, candidate.version());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        if (!SupportedBuildFile.isSupportedFile(file.getName())) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        Dependency dependency = DependencyAtCaretFinder.find(file, element);
        if (dependency == null) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = service.getFromCache(dependency);

        cachedDependency = dependency;
        cachedCandidate = candidate;

        return true;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return DependencyUpdaterBundle.message("intention.familyName");
    }

    @NotNull
    @Override
    public String getText() {
        if (cachedDependency != null) {
            if (cachedCandidate != null) {
                return DependencyUpdaterBundle.message("intention.updateDependency",
                        cachedDependency.artifact(),
                        cachedCandidate.version());
            } else {
                return DependencyUpdaterBundle.message("intention.checkForUpdates", cachedDependency.artifact());
            }
        }
        return DependencyUpdaterBundle.message("intention.checkForDependencyUpdates");
    }
}
