package com.github.clementherve.intellijjavadependencyupdaterplugin.startup;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup activity that pre-populates the version cache when the project opens.
 * This improves user experience by having version information ready before opening build files.
 */
public class DependencyStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(DependencyStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(project);

        // Only run if trigger mode is ON_OPEN
        if (settings.getTriggerMode() != DependencyUpdaterSettings.TriggerMode.ON_OPEN) {
            LOG.debug("Skipping startup cache warming - trigger mode is: " + settings.getTriggerMode());
            return null;
        }

        LOG.info("Starting dependency cache warmup on project open");

        // Run cache warming in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Warming up dependency cache", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                warmUpCache(project, indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.warn("Failed to warm up dependency cache", error);
            }
        });

        return null;
    }

    /**
     * Scans all build.gradle files and pre-fetches version information.
     */
    private void warmUpCache(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        indicator.setText("Finding Gradle build files...");

        // Find all build.gradle files by traversing the project directory
        List<VirtualFile> buildFiles = ReadAction.compute(() -> {
            List<VirtualFile> files = new ArrayList<>();
            VirtualFile baseDir = project.getBaseDir();

            if (baseDir != null) {
                VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        // Skip common directories
                        if (file.isDirectory()) {
                            String name = file.getName();
                            if (name.startsWith(".") || name.equals("build") ||
                                name.equals("node_modules") || name.equals("target")) {
                                return false;
                            }
                            return true;
                        }

                        // Check if it's a build.gradle file
                        if (SupportedFilesUtil.isSupportedFile(file.getName())) {
                            files.add(file);
                        }
                        return true;
                    }
                });
            }
            return files;
        });

        if (buildFiles.isEmpty()) {
            indicator.setText("No Gradle build files found");
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
            indicator.setText("Processing " + file.getName() + "...");

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

                    indicator.setText2("Fetching versions for " + dependency.artifact());

                    try {
                        service.getVersions(dependency.group(), dependency.artifact());
                    } catch (Exception e) {
                        LOG.debug("Failed to fetch versions for " + dependency.getFullCoordinates() + ": " + e.getMessage());
                        // Continue with other dependencies
                    }
                }

            } catch (Exception e) {
                LOG.warn("Failed to process " + file.getName(), e);
                // Continue with other files
            }
        }

        indicator.setText("Dependency cache warmup complete");
        LOG.info("Dependency cache warmup completed successfully");
    }
}
