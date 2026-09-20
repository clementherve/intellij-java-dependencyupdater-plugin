package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper.GradleWrapperInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper.GradleWrapperUpdateService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the background scan that locates every Gradle wrapper file in the project and checks each
 * one for an available update. Progress and results are reported to a {@link Listener}.
 * <p>
 * Every wrapper file is checked against the same set of published Gradle versions, which
 * {@link GradleWrapperUpdateService} fetches once and caches - so unlike the dependency scan,
 * checks are done sequentially rather than in parallel; there's no per-item network call to
 * overlap once the shared cache is warm.
 */
class GradleWrapperScanController {

    interface Listener {
        void onStatus(@NotNull String message);

        void onScanned(@NotNull List<GradleWrapperRow> rows);

        void onError(@NotNull Throwable error);
    }

    private static final Logger LOGGER = Logger.getInstance(GradleWrapperScanController.class);

    private final Project project;
    private final Listener listener;

    GradleWrapperScanController(@NotNull Project project, @NotNull Listener listener) {
        this.project = project;
        this.listener = listener;
    }

    void refresh(boolean forceRefresh) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.checking"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                GradleWrapperUpdateService service = GradleWrapperUpdateService.getInstance(project);

                List<GradleWrapperInfo> infos = service.findWrapperInfos();
                if (infos.isEmpty()) {
                    listener.onScanned(List.of());
                    return;
                }

                if (forceRefresh) {
                    service.invalidateCache();
                }

                List<GradleWrapperRow> rows = new ArrayList<>();
                for (GradleWrapperInfo info : infos) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    report(indicator, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.checkingModule", info.moduleName()));
                    rows.add(checkOne(service, info));
                }

                listener.onScanned(rows);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOGGER.warn("Failed to scan Gradle wrapper versions", error);
                listener.onError(error);
            }

            private void report(@NotNull ProgressIndicator indicator, @NotNull String message) {
                indicator.setText(message);
                listener.onStatus(message);
            }
        });
    }

    @NotNull
    private GradleWrapperRow checkOne(@NotNull GradleWrapperUpdateService service, @NotNull GradleWrapperInfo info) {
        try {
            return GradleWrapperRow.from(info, service.checkForUpdate(info));
        } catch (IOException exception) {
            LOGGER.warn("Failed to check for Gradle wrapper update: " + info.moduleName(), exception);
            return GradleWrapperRow.error(info);
        }
    }
}
