package com.github.clementherve.intellijjavadependencyupdaterplugin.ui;

import com.github.clementherve.intellijjavadependencyupdaterplugin.actions.UpdateAllDependenciesAction;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;

/**
 * Provides a notification banner at the top of build.gradle files with an "Update All Dependencies" button.
 */
public class DependencyUpdateNotificationProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<FileEditor, JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
        String fileName = file.getName();
        if (!SupportedFilesUtil.isSupportedFile(fileName)) {
            return null;
        }

        // Check if there are any outdated dependencies in this file
        Integer outdatedCount = ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return 0;
            }

            DependencyParser parser = DependencyParserFactory.getParser(psiFile);
            if (parser == null) {
                return 0;
            }

            List<DependencyInfo> dependencies = parser.parseDependencies(psiFile);
            if (dependencies.isEmpty()) {
                return 0;
            }

            DependencyUpdateService service = DependencyUpdateService.getInstance(project);
            int count = 0;
            for (DependencyInfo dependency : dependencies) {
                // Use cache-only check to avoid blocking
                VersionCandidate latest = service.checkForUpdateFromCache(dependency);
                if (latest != null) {
                    count++;
                }
            }
            return count;
        });

        // Only show banner if there are updates available
        if (outdatedCount == null || outdatedCount == 0) {
            return null;
        }

        final int finalCount = outdatedCount;
        return fileEditor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);

            String message = finalCount == 1
                ? "1 dependency update available"
                : finalCount + " dependency updates available";
            panel.setText(message);

            // Add "Update All" button
            panel.createActionLabel("Update all", () -> {
                UpdateAllDependenciesAction action = new UpdateAllDependenciesAction();
                action.actionPerformed(
                        new com.intellij.openapi.actionSystem.AnActionEvent(
                                null,
                                dataId -> {
                                    if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId)) {
                                        return project;
                                    }
                                    if (com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
                                        return file;
                                    }
                                    if (com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE.is(dataId)) {
                                        return com.intellij.psi.PsiManager.getInstance(project).findFile(file);
                                    }
                                    return null;
                                },
                                "",
                                new com.intellij.openapi.actionSystem.Presentation(),
                                com.intellij.openapi.actionSystem.ActionManager.getInstance(),
                                0
                        )
                );
            });

            return panel;
        };
    }
}
