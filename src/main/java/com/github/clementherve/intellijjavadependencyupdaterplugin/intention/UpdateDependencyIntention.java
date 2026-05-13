package com.github.clementherve.intellijjavadependencyupdaterplugin.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
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

import java.util.List;

/**
 * Intention action to update a dependency to the latest version.
 */
public class UpdateDependencyIntention extends PsiElementBaseIntentionAction implements IntentionAction, PriorityAction {

    private VersionCandidate cachedCandidate;
    private DependencyInfo cachedDependency;

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

        // Find the dependency at cursor position
        DependencyInfo dependency = findDependencyAtElement(file, element);
        if (dependency == null) {
            return;
        }

        // Get the best version candidate (use cached if available)
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = cachedCandidate != null ? cachedCandidate
                : service.checkForUpdateFromCache(dependency);

        if (candidate == null) {
            // Fetch in background - this will warm the cache
            // The user will need to invoke the intention again after cache is warm
            service.scheduleCacheWarmup(dependency);
            return;
        }

        // Apply the update (we're already in a write action due to startInWriteAction())
        VersionReplacer.applyUpdateInWriteAction(project, dependency, candidate.version());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        if (!SupportedFilesUtil.isSupportedFile(file.getName())) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        // Check if there's a dependency at cursor
        DependencyInfo dependency = findDependencyAtElement(file, element);
        if (dependency == null) {
            cachedCandidate = null;
            cachedDependency = null;
            return false;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

        // Cache for getText() method
        cachedDependency = dependency;
        cachedCandidate = candidate;

        // Show intention even if cache is empty - we'll fetch in background
        return true;
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
        if (cachedDependency != null) {
            if (cachedCandidate != null) {
                return String.format("Update '%s' to %s",
                        cachedDependency.artifact(),
                        cachedCandidate.version());
            } else {
                return String.format("Check for updates to '%s'", cachedDependency.artifact());
            }
        }
        return "Check for dependency updates";
    }

    /**
     * Finds the dependency info at the given element position.
     */
    private DependencyInfo findDependencyAtElement(@NotNull PsiFile file, @NotNull PsiElement element) {
        DependencyParser parser = DependencyParserFactory.getParser(file);
        if (parser == null) {
            return null;
        }

        List<DependencyInfo> dependencies = parser.parseDependencies(file);

        // Find the dependency that contains this element
        int offset = element.getTextOffset();
        for (DependencyInfo dependency : dependencies) {
            if (dependency.psiElementPointer() != null) {
                PsiElement depElement = dependency.psiElementPointer().getElement();
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
}
