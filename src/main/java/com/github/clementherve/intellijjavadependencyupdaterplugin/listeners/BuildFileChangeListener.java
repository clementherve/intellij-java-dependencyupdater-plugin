package com.github.clementherve.intellijjavadependencyupdaterplugin.listeners;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Listens for build file saves and refreshes the dependency cache.
 * Only active when trigger mode is set to ON_SAVE.
 */
public class BuildFileChangeListener implements FileDocumentManagerListener {

    private static final Logger LOG = Logger.getInstance(BuildFileChangeListener.class);

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VirtualFile file = fileDocumentManager.getFile(document);

        if (file == null) {
            return;
        }

        if (!SupportedFilesUtil.isSupportedFile(file.getName())) {
            return;
        }

        Project project = findProjectForFile(file);
        if (project == null) {
            return;
        }

        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Only refresh cache if trigger mode is ON_SAVE
        if (settings.getTriggerMode() != DependencyUpdaterSettings.TriggerMode.ON_SAVE) {
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Refreshing dependency cache", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                refreshCacheForFile(project, file, indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.warn("Failed to refresh dependency cache for " + file.getName(), error);
            }
        });
    }

    /**
     * Refreshes the cache for dependencies in the given file.
     */
    private void refreshCacheForFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull ProgressIndicator indicator) {
        indicator.setText("Parsing dependencies in " + file.getName() + "...");

        try {
            // Parse dependencies in read action
            List<DependencyInfo> dependencies = ApplicationManager.getApplication().runReadAction((Computable<List<DependencyInfo>>) () -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile == null) {
                    return List.of();
                }

                DependencyParser parser = DependencyParserFactory.getParser(psiFile);
                if (parser == null) {
                    return List.of();
                }

                return parser.parseDependencies(psiFile);
            });

            if (dependencies.isEmpty()) {
                LOG.debug("No dependencies found in " + file.getName());
                return;
            }

            LOG.debug("Found " + dependencies.size() + " dependencies, fetching latest versions...");

            DependencyUpdateService service = DependencyUpdateService.getInstance(project);

            // Invalidate cache and fetch fresh versions for each dependency
            for (int i = 0; i < dependencies.size(); i++) {
                if (indicator.isCanceled()) {
                    return;
                }

                DependencyInfo dependency = dependencies.get(i);
                indicator.setFraction((double) i / dependencies.size());
                indicator.setText2("Checking " + dependency.artifact());

                try {
                    // Invalidate old cache entry
                    service.invalidateCache(dependency.group(), dependency.artifact());

                    // Fetch fresh versions (this will populate cache)
                    service.fetchVersionsAndSaveThemToCache(dependency.group(), dependency.artifact());

                    LOG.debug("Refreshed cache for " + dependency.getFullCoordinates());
                } catch (Exception e) {
                    LOG.debug("Failed to refresh cache for " + dependency.getFullCoordinates() + ": " + e.getMessage());
                    // Continue with other dependencies
                }
            }

            indicator.setText("Cache refresh complete");
            LOG.info("Successfully refreshed cache for " + dependencies.size() + " dependencies in " + file.getName());

        } catch (Exception e) {
            LOG.warn("Failed to refresh cache for " + file.getName(), e);
        }
    }

    /**
     * Finds the project that contains the given file.
     */
    private Project findProjectForFile(@NotNull VirtualFile file) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) {
                continue;
            }

            // Check if file is in project scope
            if (ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                return psiFile != null;
            })) {
                return project;
            }
        }
        return null;
    }
}
