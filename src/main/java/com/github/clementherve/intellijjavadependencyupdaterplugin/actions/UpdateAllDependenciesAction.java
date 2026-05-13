package com.github.clementherve.intellijjavadependencyupdaterplugin.actions;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action to update all outdated dependencies in the current file.
 */
public class UpdateAllDependenciesAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || file == null) {
            return;
        }

        String fileName = file.getName();
        if (!"build.gradle".equals(fileName) && !"build.gradle.kts".equals(fileName)) {
            Messages.showInfoMessage(
                project,
                "This action only works on build.gradle or build.gradle.kts files.",
                "Update All Dependencies"
            );
            return;
        }

        // Run in background with progress
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking for Dependency Updates", true) {
            private final Map<DependencyInfo, VersionCandidate> updates = new HashMap<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Parsing dependencies...");

                // Parse dependencies in a read action
                List<DependencyInfo> dependencies = ReadAction.compute(() -> {
                    DependencyParser parser = DependencyParserFactory.getParser(file);
                    if (parser == null) {
                        return new ArrayList<>();
                    }
                    return parser.parseDependencies(file);
                });

                if (dependencies.isEmpty()) {
                    return;
                }

                DependencyUpdateService service = DependencyUpdateService.getInstance(project);
                indicator.setText("Checking for updates...");

                for (int i = 0; i < dependencies.size(); i++) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    DependencyInfo dependency = dependencies.get(i);
                    indicator.setFraction((double) i / dependencies.size());
                    indicator.setText2("Checking " + dependency.getArtifact() + "...");

                    VersionCandidate candidate = service.checkForUpdateFromCache(dependency);
                    if (candidate == null) {
                        // Fetch from network
                        candidate = service.checkForUpdate(dependency);
                    }

                    if (candidate != null) {
                        updates.put(dependency, candidate);
                    }
                }
            }

            @Override
            public void onSuccess() {
                if (updates.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "All dependencies are up to date!",
                        "Update All Dependencies"
                    );
                    return;
                }

                // Show confirmation dialog
                StringBuilder message = new StringBuilder("The following dependencies will be updated:\n\n");
                for (Map.Entry<DependencyInfo, VersionCandidate> entry : updates.entrySet()) {
                    DependencyInfo dep = entry.getKey();
                    VersionCandidate candidate = entry.getValue();
                    message.append(String.format("%s: %s → %s\n",
                        dep.getArtifact(),
                        dep.getCurrentVersion(),
                        candidate.getVersion()));
                }

                int result = Messages.showOkCancelDialog(
                    project,
                    message.toString(),
                    "Update " + updates.size() + " Dependencies",
                    "Update All",
                    "Cancel",
                    Messages.getQuestionIcon()
                );

                if (result == Messages.OK) {
                    // Apply updates
                    for (Map.Entry<DependencyInfo, VersionCandidate> entry : updates.entrySet()) {
                        VersionReplacer.applyUpdate(
                            project,
                            entry.getKey(),
                            entry.getValue().getVersion()
                        );
                    }

                    Messages.showInfoMessage(
                        project,
                        "Successfully updated " + updates.size() + " dependencies!",
                        "Update All Dependencies"
                    );
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                Messages.showErrorDialog(
                    project,
                    "Failed to check for updates: " + error.getMessage(),
                    "Update All Dependencies"
                );
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = file != null &&
            ("build.gradle".equals(file.getName()) || "build.gradle.kts".equals(file.getName()));
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
