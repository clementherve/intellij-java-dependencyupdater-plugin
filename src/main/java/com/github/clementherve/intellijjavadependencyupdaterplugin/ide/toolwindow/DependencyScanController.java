package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow.DependencyRow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileLocator.findBuildGradleFilesInCurrentProject;

/**
 * Runs the background scan that discovers build files, parses their dependencies and checks
 * each for an available update. Progress and results are reported to a {@link Listener}; the
 * controller itself performs no UI work beyond driving the progress indicator.
 */
class DependencyScanController {

    /**
     * Receives scan progress and results. {@link #onStatus} may be called from a background
     * thread; {@link #onScanned} and {@link #onError} are called on the EDT.
     */
    interface Listener {
        void onStatus(@NotNull String message);

        void onScanned(@NotNull List<DependencyRow> rows);

        void onError(@NotNull Throwable error);
    }

    private static final Logger LOGGER = Logger.getInstance(DependencyScanController.class);

    private final Project project;
    private final Listener listener;

    DependencyScanController(@NotNull Project project, @NotNull Listener listener) {
        this.project = project;
        this.listener = listener;
    }

    void refresh(boolean forceRefresh) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, DependencyUpdaterBundle.message("toolWindow.scanning"), false) {
            private final List<DependencyRow> rows = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                report(indicator, DependencyUpdaterBundle.message("toolWindow.findingFiles"));

                List<VirtualFile> buildFiles = findBuildGradleFilesInCurrentProject(project);
                if (buildFiles.isEmpty()) {
                    listener.onStatus(DependencyUpdaterBundle.message("toolWindow.noFiles"));
                    return;
                }

                listener.onStatus(DependencyUpdaterBundle.message("toolWindow.foundFiles", buildFiles.size()));

                DependencyUpdateService service = DependencyUpdateService.getInstance(project);
                PsiManager psiManager = PsiManager.getInstance(project);

                for (int i = 0; i < buildFiles.size(); i++) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    VirtualFile file = buildFiles.get(i);
                    String projectName = file.getParent() != null ? file.getParent().getName() : file.getName();
                    indicator.setFraction((double) i / buildFiles.size());
                    report(indicator, DependencyUpdaterBundle.message("toolWindow.processingFile", file.getName()));

                    try {
                        scanFile(file, projectName, psiManager, service, indicator, forceRefresh);
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to process " + file.getName(), exception);
                    }
                }
            }

            private void scanFile(@NotNull VirtualFile file, @NotNull String projectName,
                                  @NotNull PsiManager psiManager, @NotNull DependencyUpdateService service,
                                  @NotNull ProgressIndicator indicator, boolean forceRefresh) throws IOException {
                List<Dependency> dependencies = ApplicationManager.getApplication().runReadAction((Computable<List<Dependency>>) () -> {
                    PsiFile psiFile = psiManager.findFile(file);
                    if (psiFile == null) {
                        return List.of();
                    }

                    BuildFileParser parser = BuildFileParserFactory.getParser(psiFile);
                    if (parser == null) {
                        return List.of();
                    }

                    return parser.parseDependencies(psiFile);
                });

                for (Dependency dependency : dependencies) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    indicator.setText2(DependencyUpdaterBundle.message("toolWindow.checkingDependency", dependency.artifact()));
                    listener.onStatus(DependencyUpdaterBundle.message("toolWindow.checkingDependency", dependency.artifact()) + "...");

                    VersionCandidate latest = forceRefresh
                            ? service.forceCheckForUpdate(dependency)
                            : service.checkForUpdate(dependency);
                    rows.add(DependencyRow.from(dependency, latest, projectName));
                }
            }

            @Override
            public void onSuccess() {
                listener.onScanned(rows);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                listener.onError(error);
            }

            private void report(@NotNull ProgressIndicator indicator, @NotNull String message) {
                indicator.setText(message);
                listener.onStatus(message);
            }
        });
    }
}
