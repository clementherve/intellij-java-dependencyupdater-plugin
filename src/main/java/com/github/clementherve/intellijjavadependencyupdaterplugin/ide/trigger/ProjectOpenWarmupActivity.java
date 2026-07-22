package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.trigger;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.DependencyNotFoundException;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileLocator.findBuildGradleFilesInCurrentProject;

/**
 * Startup activity that pre-populates the version cache when the project opens.
 * This improves user experience by having version information ready before opening build files.
 */
public class ProjectOpenWarmupActivity implements ProjectActivity {

    private static final Logger LOGGER = Logger.getInstance(ProjectOpenWarmupActivity.class);

    private static void tryAndFetchVersions(final Dependency dependency, final DependencyUpdateService service) {
        try {
            service.fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());
        } catch (DependencyNotFoundException notFound) {
            // Expected: already logged/cached at the repository layer.
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch versions for " + dependency.getFullCoordinates() + ": " + e.getMessage());
            // Continue with other dependencies
        }
    }

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance();

        final boolean isTriggerModeOnOpen = settings.getTriggerMode() == DependencyUpdaterSettings.TriggerMode.ON_OPEN;
        if (!isTriggerModeOnOpen) {
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
                List<Dependency> dependencies = ApplicationManager.getApplication().runReadAction((Computable<List<Dependency>>) () -> {
                    PsiFile psiFile = psiManager.findFile(file);
                    if (psiFile == null) {
                        return new ArrayList<>();
                    }

                    BuildFileParser parser = BuildFileParserFactory.getParser(psiFile);
                    if (parser == null) {
                        return new ArrayList<>();
                    }

                    return parser.parseDependencies(psiFile);
                });

                for (Dependency dependency : dependencies) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    indicator.setText2(DependencyUpdaterBundle.message("cache.fetchingVersions", dependency.artifact()));

                    tryAndFetchVersions(dependency, service);
                }

            } catch (Exception e) {
                LOGGER.warn("Failed to process " + file.getName(), e);
                // Continue with other files
            }
        }

        indicator.setText(DependencyUpdaterBundle.message("cache.complete"));
    }
}
