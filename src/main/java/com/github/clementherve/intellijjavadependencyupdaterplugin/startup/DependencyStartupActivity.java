package com.github.clementherve.intellijjavadependencyupdaterplugin.startup;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.util.FindBuildGradleFilesUtil.findBuildGradleFilesInCurrentProject;

/**
 * Startup activity that pre-populates the version cache when the project opens.
 * This improves user experience by having version information ready before opening build files.
 */
public class DependencyStartupActivity implements ProjectActivity {

    private static final Logger LOGGER = Logger.getInstance(DependencyStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        final boolean isTriggerModeOnOpen = settings.getTriggerMode() != DependencyUpdaterSettings.TriggerMode.ON_OPEN;
        if (isTriggerModeOnOpen) {
            return null;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, DependencyUpdaterBundle.message("cache.taskTitle"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                fetchDependenciesAndSaveThemInCache(project, indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOGGER.error("Failed to fetch dependencies", error);
            }
        });

        return null;
    }

    private void fetchDependenciesAndSaveThemInCache(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        indicator.setText(DependencyUpdaterBundle.message("cache.findingFiles"));

        List<VirtualFile> buildFiles = findBuildGradleFilesInCurrentProject(project);

        if (buildFiles.isEmpty()) {
            indicator.setText(DependencyUpdaterBundle.message("cache.noFilesFound"));
            return;
        }

        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (int i = 0; i < buildFiles.size(); i++) {
            if (indicator.isCanceled()) {
                return;
            }

            VirtualFile file = buildFiles.get(i);
            indicator.setFraction((double) i / buildFiles.size());
            indicator.setText(DependencyUpdaterBundle.message("cache.processingFile", file.getName()));

            try {
                List<DependencyInfo> dependencies = ReadAction.compute(() -> {
                    PsiFile psiFile = psiManager.findFile(file);
                    if (psiFile == null) {
                        return new ArrayList<>();
                    }

                    DependencyParser parser = DependencyParserFactory.getParser(psiFile);
                    if (parser == null) {
                        return new ArrayList<>();
                    }

                    return parser.parseDependencies(psiFile);
                });

                for (DependencyInfo dependency : dependencies) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    indicator.setText2(DependencyUpdaterBundle.message("cache.fetchingVersions", dependency.artifact()));

                    try {
                        service.fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to fetch versions for " + dependency.getFullCoordinates() + ": " + e.getMessage());
                        // Continue with other dependencies
                    }
                }

            } catch (Exception e) {
                LOGGER.warn("Failed to process " + file.getName(), e);
                // Continue with other files
            }
        }

        indicator.setText(DependencyUpdaterBundle.message("cache.complete"));
    }
}
