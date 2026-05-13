package com.github.clementherve.intellijjavadependencyupdaterplugin.ui;

import com.github.clementherve.intellijjavadependencyupdaterplugin.actions.UpdateAllDependenciesAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * Provides a notification banner at the top of build.gradle files with an "Update All Dependencies" button.
 */
public class DependencyUpdateNotificationProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<FileEditor, JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
        // Only show for build.gradle and build.gradle.kts files
        String fileName = file.getName();
        if (!"build.gradle".equals(fileName) && !"build.gradle.kts".equals(fileName)) {
            return null;
        }

        return fileEditor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
            panel.setText("Gradle dependencies");

            // Add "Update All Dependencies" button
            panel.createActionLabel("Update All Dependencies", () -> {
                UpdateAllDependenciesAction action = new UpdateAllDependenciesAction();
                action.actionPerformed(
                    new com.intellij.openapi.actionSystem.AnActionEvent(
                        null,
                        new com.intellij.openapi.actionSystem.DataContext() {
                            @Override
                            public @Nullable Object getData(@NotNull String dataId) {
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
                            }
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
