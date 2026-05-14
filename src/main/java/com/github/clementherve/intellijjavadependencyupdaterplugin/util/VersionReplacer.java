package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionReplacer {

    private static final Logger LOGGER = Logger.getInstance(VersionReplacer.class);

    public static void applyUpdate(@NotNull Project project,
                                   @NotNull DependencyInfo dependency,
                                   @NotNull String newVersion) {
        if (dependency.isVersionVariable()) {
            int result = Messages.showOkCancelDialog(
                    project,
                    "This version is defined by a variable (" + dependency.variableName() +
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

        WriteCommandAction.runWriteCommandAction(project, "Update Dependency Version", null, () -> performUpdate(dependency, newVersion));
    }

    public static void applyUpdateInWriteAction(@NotNull Project project,
                                                @NotNull DependencyInfo dependency,
                                                @NotNull String newVersion) {
        performUpdate(dependency, newVersion);
    }

    private static void performUpdate(@NotNull DependencyInfo dependency,
                                      @NotNull String newVersion) {
        if (dependency.psiElementPointer() == null) {
            return;
        }

        PsiElement versionElement = dependency.psiElementPointer().getElement();
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

            if (dependency.isVersionVariable() && dependency.variableName() != null) {
                updateVariableInExtBlock(file, document, dependency.variableName(), newVersion);
            } else {
                String oldText = versionElement.getText();
                String newText = oldText.replace(dependency.currentVersion(), newVersion);

                int start = versionElement.getTextRange().getStartOffset();
                int end = versionElement.getTextRange().getEndOffset();
                document.replaceString(start, end, newText);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to update dependency to " + newVersion, e);
        }
    }

    private static void updateVariableInExtBlock(@NotNull PsiFile file,
                                                 @NotNull com.intellij.openapi.editor.Document document,
                                                 @NotNull String variableName,
                                                 @NotNull String newVersion) {
        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(file, GrMethodCall.class)) {
            PsiElement methodElement = methodCall.getInvokedExpression();
            if ("ext".equals(methodElement.getText())) {
                String blockText = methodCall.getText();
                Pattern pattern = Pattern.compile("(" + Pattern.quote(variableName) + "\\s*=\\s*['\"])([^'\"]+)(['\"])");
                Matcher matcher = pattern.matcher(blockText);

                if (matcher.find()) {
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
