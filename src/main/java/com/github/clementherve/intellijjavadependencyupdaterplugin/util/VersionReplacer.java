package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for replacing dependency versions in PSI.
 */
public class VersionReplacer {

    /**
     * Applies a version update to a dependency.
     * This method wraps the update in a WriteCommandAction.
     *
     * @param project the project
     * @param dependency the dependency to update
     * @param newVersion the new version to apply
     */
    public static void applyUpdate(@NotNull Project project,
                                  @NotNull DependencyInfo dependency,
                                  @NotNull String newVersion) {
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
            performUpdate(project, dependency, newVersion);
        });
    }

    /**
     * Applies a version update without starting a write action.
     * Use this when already in a write action (e.g., from an intention).
     *
     * @param project the project
     * @param dependency the dependency to update
     * @param newVersion the new version to apply
     */
    public static void applyUpdateInWriteAction(@NotNull Project project,
                                               @NotNull DependencyInfo dependency,
                                               @NotNull String newVersion) {
        // Skip dialog during preview mode
        if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
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
        }

        performUpdate(project, dependency, newVersion);
    }

    /**
     * Performs the actual version update.
     * Must be called from within a write action.
     */
    private static void performUpdate(@NotNull Project project,
                                     @NotNull DependencyInfo dependency,
                                     @NotNull String newVersion) {
        if (dependency.getPsiElementPointer() == null) {
            return;
        }

        PsiElement versionElement = dependency.getPsiElementPointer().getElement();
        if (versionElement == null) {
            return;
        }

        PsiFile file = versionElement.getContainingFile();
        if (file == null) {
            return;
        }

        try {
            com.intellij.openapi.fileEditor.FileDocumentManager docManager =
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
            com.intellij.openapi.editor.Document document =
                docManager.getDocument(file.getVirtualFile());

            if (document == null) {
                return;
            }

            // If it's a version variable, update the ext block instead
            if (dependency.isVersionVariable() && dependency.getVariableName() != null) {
                updateVariableInExtBlock(file, document, dependency.getVariableName(), newVersion);
            } else {
                // Regular dependency - update the version in place
                String oldText = versionElement.getText();
                String newText = oldText.replace(dependency.getCurrentVersion(), newVersion);

                int start = versionElement.getTextRange().getStartOffset();
                int end = versionElement.getTextRange().getEndOffset();
                document.replaceString(start, end, newText);
            }
        } catch (Exception e) {
            if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
                Messages.showErrorDialog(
                    project,
                    "Failed to update dependency. Please update manually to " + newVersion,
                    "Update Failed"
                );
            }
        }
    }

    /**
     * Updates a variable definition in the ext block.
     */
    private static void updateVariableInExtBlock(@NotNull PsiFile file,
                                                 @NotNull com.intellij.openapi.editor.Document document,
                                                 @NotNull String variableName,
                                                 @NotNull String newVersion) {
        // Find the ext block
        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(file, GrMethodCall.class)) {
            PsiElement methodElement = methodCall.getInvokedExpression();
            if (methodElement != null && "ext".equals(methodElement.getText())) {
                // Found ext block, find the variable assignment
                String blockText = methodCall.getText();
                Pattern pattern = Pattern.compile("(" + Pattern.quote(variableName) + "\\s*=\\s*['\"])([^'\"]+)(['\"])");
                Matcher matcher = pattern.matcher(blockText);

                if (matcher.find()) {
                    // Calculate the position in the document
                    int blockStart = methodCall.getTextRange().getStartOffset();
                    int varStart = blockStart + matcher.start(2);
                    int varEnd = blockStart + matcher.end(2);

                    document.replaceString(varStart, varEnd, newVersion);
                    return;
                }
            }
        }
    }
}
