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

        // Quick check: only proceed if we might have cached data
        // This avoids slow operations on EDT by not parsing if cache is likely empty
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);

        // Fast path: check if there are any outdated dependencies using only cached data
        // We limit the parsing to avoid slow operations
        Integer outdatedCount = ReadAction.compute(() -> {
            try {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile == null) {
                    return 0;
                }

                DependencyParser parser = DependencyParserFactory.getParser(psiFile);
                if (parser == null) {
                    return 0;
                }

                // Parse dependencies - this is fast for typical build.gradle files
                List<DependencyInfo> dependencies = parser.parseDependencies(psiFile);
                if (dependencies.isEmpty()) {
                    return 0;
                }

                // Only check cache (never trigger network calls)
                int count = 0;
                for (DependencyInfo dependency : dependencies) {
                    VersionCandidate latest = service.checkForUpdateFromCache(dependency);
                    if (latest != null) {
                        count++;
                    }
                }
                return count;
            } catch (Exception e) {
                // If anything goes wrong, don't show notification
                return 0;
            }
        });

        // Only show banner if there are updates available
        if (outdatedCount == null || outdatedCount == 0) {
            return null;
        }

        final int finalCount = outdatedCount;
        return fileEditor -> createNotificationPanel(project, file, finalCount);
    }

    private EditorNotificationPanel createNotificationPanel(@NotNull Project project,
                                                           @NotNull VirtualFile file,
                                                           int count) {
        EditorNotificationPanel panel = new EditorNotificationPanel(EditorNotificationPanel.Status.Warning);

        String message = count == 1
            ? "1 dependency update available"
            : count + " dependency updates available";
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
    }
}
