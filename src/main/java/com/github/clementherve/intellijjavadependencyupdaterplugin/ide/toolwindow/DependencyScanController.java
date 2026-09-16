package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.repository.DependencyNotFoundException;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.ParallelDependencyChecker;
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
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileLocator.findBuildGradleFilesInCurrentProject;

/**
 * Runs the background scan that discovers build files, parses their dependencies and checks
 * each for an available update. Progress and results are reported to a {@link Listener}; the
 * controller itself performs no UI work beyond driving the progress indicator.
 * <p>
 * Dependencies are collected from all build files first, then checked concurrently on a bounded
 * thread pool ({@link ParallelDependencyChecker}) - repositories are queried one dependency at a
 * time either way, but in parallel instead of sequentially.
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

    private record ScanEntry(@NotNull Dependency dependency, @NotNull String projectName) {
    }

    private record CheckOutcome(VersionCandidate candidate, boolean notFound) {
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

                List<ScanEntry> entries = collectEntries(buildFiles, indicator);
                if (entries.isEmpty() || indicator.isCanceled()) {
                    return;
                }

                checkEntries(entries, indicator, forceRefresh);
            }

            @NotNull
            private List<ScanEntry> collectEntries(@NotNull List<VirtualFile> buildFiles, @NotNull ProgressIndicator indicator) {
                PsiManager psiManager = PsiManager.getInstance(project);
                List<ScanEntry> entries = new ArrayList<>();

                for (VirtualFile file : buildFiles) {
                    if (indicator.isCanceled()) {
                        return entries;
                    }

                    String projectName = file.getParent() != null ? file.getParent().getName() : file.getName();
                    report(indicator, DependencyUpdaterBundle.message("toolWindow.processingFile", file.getName()));

                    try {
                        for (Dependency dependency : parseDependencies(file, psiManager)) {
                            entries.add(new ScanEntry(dependency, projectName));
                        }
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to process " + file.getName(), exception);
                    }
                }

                return entries;
            }

            @NotNull
            private List<Dependency> parseDependencies(@NotNull VirtualFile file, @NotNull PsiManager psiManager) {
                return ApplicationManager.getApplication().runReadAction((Computable<List<Dependency>>) () -> {
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
            }

            private void checkEntries(@NotNull List<ScanEntry> entries, @NotNull ProgressIndicator indicator, boolean forceRefresh) {
                DependencyUpdateService service = DependencyUpdateService.getInstance(project);
                AtomicInteger completedCount = new AtomicInteger(0);
                report(indicator, DependencyUpdaterBundle.message("toolWindow.checkingDependencies", 0, entries.size()));

                ParallelDependencyChecker.run(
                        entries,
                        indicator,
                        entry -> checkOne(service, entry.dependency(), forceRefresh),
                        (entry, outcome) -> {
                            int completed = completedCount.incrementAndGet();
                            indicator.setFraction((double) completed / entries.size());
                            report(indicator, DependencyUpdaterBundle.message("toolWindow.checkingDependencies", completed, entries.size()));

                            if (outcome.notFound()) {
                                rows.add(DependencyRow.notFound(entry.dependency(), entry.projectName()));
                            } else {
                                rows.add(DependencyRow.from(entry.dependency(), outcome.candidate(), entry.projectName()));
                            }
                        }
                );
            }

            @NotNull
            private CheckOutcome checkOne(@NotNull DependencyUpdateService service, @NotNull Dependency dependency, boolean forceRefresh) {
                try {
                    VersionCandidate latest = forceRefresh
                            ? service.forceCheckForUpdate(dependency)
                            : service.checkForUpdate(dependency);
                    return new CheckOutcome(latest, false);
                } catch (DependencyNotFoundException notFound) {
                    return new CheckOutcome(null, true);
                } catch (IOException exception) {
                    LOGGER.warn("Failed to check for update: " + dependency.getFullCoordinates(), exception);
                    return new CheckOutcome(null, false);
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
