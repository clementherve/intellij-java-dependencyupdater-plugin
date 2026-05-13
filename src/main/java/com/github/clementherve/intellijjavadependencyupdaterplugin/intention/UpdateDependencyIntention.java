package com.github.clementherve.intellijjavadependencyupdaterplugin.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
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
 * Intention action to update a dependency to the latest version.
 */
public class UpdateDependencyIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    @Override
    public boolean startInWriteAction() {
        // Return true so IntelliJ manages the write action for us
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

        // Get the best version candidate
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

        if (candidate == null) {
            // Try fetching in background and notify user
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
            return false;
        }

        if (!SupportedFilesUtil.isSupportedFile(file.getName())) {
            return false;
        }

        // Check if there's a dependency at cursor with available update
        DependencyInfo dependency = findDependencyAtElement(file, element);
        if (dependency == null) {
            return false;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        VersionCandidate candidate = service.checkForUpdateFromCache(dependency);

        return candidate != null;
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
        return "Update dependency to latest version";
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
