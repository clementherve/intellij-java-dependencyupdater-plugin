package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.action;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.github.clementherve.intellijjavadependencyupdaterplugin.update.DependencyVersionWriter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Action to update all outdated dependencies in the current file.
 */
public class UpdateAllDependenciesAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);

        if (project == null || file == null) {
            return;
        }

        if (!SupportedBuildFile.isSupportedFile(file.getName())) {
            Messages.showInfoMessage(
                    project,
                    "This action only works on build.gradle files.",
                    "Update All Dependencies"
            );
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking for dependency updates", true) {
            private final Map<Dependency, VersionCandidate> dependenciesWithUpdateCandidates = new HashMap<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Parsing dependencies...");

                List<Dependency> dependencies = safelyParseDependencies(file);

                if (dependencies.isEmpty()) {
                    return;
                }

                DependencyUpdateService dependencyUpdateService = DependencyUpdateService.getInstance(project);
                indicator.setText("Checking for updates...");

                for (int i = 0; i < dependencies.size(); i++) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    Dependency dependency = dependencies.get(i);
                    indicator.setFraction((double) i / dependencies.size());
                    indicator.setText2("Checking " + dependency.artifact() + "...");

                    VersionCandidate candidate = dependencyUpdateService.getFromCache(dependency);
                    if (candidate == null) {
                        candidate = dependencyUpdateService.checkForUpdate(dependency);
                    }

                    if (candidate != null) {
                        dependenciesWithUpdateCandidates.put(dependency, candidate);
                    }
                }
            }

            @Override
            public void onSuccess() {
                if (dependenciesWithUpdateCandidates.isEmpty()) {
                    Messages.showInfoMessage(
                            project,
                            "All dependencies are up to date!",
                            "Update All Dependencies"
                    );
                    return;
                }

                StringBuilder message = getMessage();
                int result = Messages.showOkCancelDialog(
                        project,
                        message.toString(),
                        "Update " + dependenciesWithUpdateCandidates.size() + " dependencies",
                        "Update All",
                        "Cancel",
                        Messages.getQuestionIcon()
                );

                if (result == Messages.OK) {
                    List<Map.Entry<Dependency, VersionCandidate>> sortedUpdates = sortUpdatesInReverseDocumentOrder(dependenciesWithUpdateCandidates);

                    applyUpdateDependenciesToDocument(sortedUpdates, project);

                    Messages.showInfoMessage(
                            project,
                            "Successfully updated " + dependenciesWithUpdateCandidates.size() + " dependencies!",
                            "Update All Dependencies"
                    );
                }
            }

            @NotNull
            private List<Map.Entry<Dependency, VersionCandidate>> sortUpdatesInReverseDocumentOrder(Map<Dependency, VersionCandidate> updates) {
                return updates.entrySet().stream()
                        .sorted((e1, e2) -> {
                            final SmartPsiElementPointer<PsiElement> psiElementPointer1 = e1.getKey().psiElementPointer();
                            final SmartPsiElementPointer<PsiElement> psiElementPointer2 = e2.getKey().psiElementPointer();

                            final PsiElement psiElement1 = psiElementPointer1 != null ? psiElementPointer1.getElement() : null;
                            final PsiElement psiElement2 = psiElementPointer2 != null ? psiElementPointer2.getElement() : null;

                            int offset1 = psiElement1 != null ? psiElement1.getTextOffset() : 0;
                            int offset2 = psiElement2 != null ? psiElement2.getTextOffset() : 0;
                            return Integer.compare(offset2, offset1);
                        })
                        .toList();
            }

            @NotNull
            private StringBuilder getMessage() {
                StringBuilder message = new StringBuilder("The following dependencies will be updated:\n\n");

                for (Map.Entry<Dependency, VersionCandidate> entry : dependenciesWithUpdateCandidates.entrySet()) {
                    Dependency dependency = entry.getKey();
                    VersionCandidate versionCandidate = entry.getValue();

                    final String informationMessage = String.format("%s: %s → %s\n",
                            dependency.artifact(),
                            dependency.currentVersion(),
                            versionCandidate.version());

                    message.append(informationMessage);
                }

                return message;
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

    private static List<Dependency> safelyParseDependencies(final PsiFile file) {
        return ApplicationManager.getApplication().runReadAction((Computable<List<Dependency>>) () -> {
            BuildFileParser dependencyParser = BuildFileParserFactory.getParser(file);
            if (dependencyParser == null) {
                return new ArrayList<>();
            }
            return dependencyParser.parseDependencies(file);
        });
    }

    private static void applyUpdateDependenciesToDocument(final List<Map.Entry<Dependency, VersionCandidate>> sortedUpdates, final Project project) {
        WriteCommandAction.runWriteCommandAction(project, "Update All Dependencies", null, () -> {
            for (Map.Entry<Dependency, VersionCandidate> entry : sortedUpdates) {
                DependencyVersionWriter.applyUpdateInWriteAction(
                        project,
                        entry.getKey(),
                        entry.getValue().version()
                );
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = file != null && SupportedBuildFile.isSupportedFile(file.getName());
        event.getPresentation().setEnabledAndVisible(enabled);
    }
}
