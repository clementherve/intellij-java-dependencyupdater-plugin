package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for replacing dependency versions in PSI.
 */
public class VersionReplacer {

    /**
     * Applies a version update to a dependency.
     *
     * @param project the project
     * @param dependency the dependency to update
     * @param newVersion the new version to apply
     */
    public static void applyUpdate(@NotNull Project project,
                                  @NotNull DependencyInfo dependency,
                                  @NotNull String newVersion) {
        if (dependency.getPsiElementPointer() == null) {
            return;
        }

        PsiElement versionElement = dependency.getPsiElementPointer().getElement();
        if (versionElement == null) {
            return;
        }

        // If it's a version variable, warn the user
        if (dependency.isVersionVariable()) {
            int result = Messages.showOkCancelDialog(
                project,
                "This version is defined by a variable (" + dependency.getVariableName() +
                "). Updating it may affect multiple dependencies. Continue?",
                "Update Version Variable",
                "Update",
                "Cancel",
                Messages.getWarningIcon()
            );

            if (result != Messages.OK) {
                return;
            }
        }

        // Perform the replacement in a write action
        WriteCommandAction.runWriteCommandAction(project, "Update Dependency Version", null, () -> {
            String oldText = versionElement.getText();
            String newText = oldText.replace(dependency.getCurrentVersion(), newVersion);

            // For string literals, we need to preserve quotes
            if (oldText.contains("\"")) {
                newText = "\"" + newVersion + "\"";
            } else if (oldText.contains("'")) {
                newText = "'" + newVersion + "'";
            }

            try {
                // For now, use a simple text-based approach
                // In a full implementation, you'd use GroovyPsiElementFactory or KtPsiFactory
                com.intellij.openapi.fileEditor.FileDocumentManager docManager =
                    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
                com.intellij.openapi.editor.Document document =
                    docManager.getDocument(versionElement.getContainingFile().getVirtualFile());

                if (document != null) {
                    int start = versionElement.getTextRange().getStartOffset();
                    int end = versionElement.getTextRange().getEndOffset();
                    document.replaceString(start, end, newText);
                }
            } catch (Exception e) {
                Messages.showErrorDialog(
                    project,
                    "Failed to update dependency. Please update manually to " + newVersion,
                    "Update Failed"
                );
            }
        });
    }
}
