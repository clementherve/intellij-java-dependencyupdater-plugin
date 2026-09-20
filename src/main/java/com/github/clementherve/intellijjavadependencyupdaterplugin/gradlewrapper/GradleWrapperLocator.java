package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates every {@code gradle/wrapper/gradle-wrapper.properties} file in a project and parses the
 * Gradle version each one pins. A project can have more than one - e.g. separate {@code app/} and
 * {@code test/} modules, each with their own wrapper - so the whole project tree is scanned rather
 * than assuming a single wrapper file at the project root.
 */
public final class GradleWrapperLocator {

    private static final String WRAPPER_PROPERTIES_FILE_NAME = "gradle-wrapper.properties";

    private GradleWrapperLocator() {
    }

    /**
     * @return the wrapper info for every {@code gradle-wrapper.properties} file found under the
     * project root whose {@code distributionUrl} could be parsed
     */
    @NotNull
    public static List<GradleWrapperInfo> findWrapperInfos(@NotNull Project project) {
        List<VirtualFile> wrapperFiles = ApplicationManager.getApplication()
                .runReadAction((Computable<List<VirtualFile>>) () -> findWrapperPropertiesFiles(project));

        List<GradleWrapperInfo> infos = new ArrayList<>();
        for (VirtualFile wrapperFile : wrapperFiles) {
            GradleWrapperInfo info = parse(wrapperFile);
            if (info != null) {
                infos.add(info);
            }
        }
        return infos;
    }

    @NotNull
    private static List<VirtualFile> findWrapperPropertiesFiles(@NotNull Project project) {
        List<VirtualFile> files = new ArrayList<>();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);

        if (projectDir != null) {
            VfsUtilCore.visitChildrenRecursively(projectDir, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    // Skip common big directories
                    if (file.isDirectory()) {
                        String name = file.getName();
                        return !name.startsWith(".") && !name.equals("build") && !name.equals("node_modules") && !name.equals("target");
                    }

                    if (WRAPPER_PROPERTIES_FILE_NAME.equals(file.getName())) {
                        files.add(file);
                    }
                    return true;
                }
            });
        }

        return files;
    }

    @Nullable
    private static GradleWrapperInfo parse(@NotNull VirtualFile wrapperFile) {
        VirtualFile moduleDir = moduleDirFor(wrapperFile);
        if (moduleDir == null) {
            return null;
        }

        String content;
        try {
            content = VfsUtil.loadText(wrapperFile);
        } catch (IOException exception) {
            return null;
        }

        GradleWrapperPropertiesParser.ParsedDistributionUrl parsed = GradleWrapperPropertiesParser.parse(content);
        if (parsed == null) {
            return null;
        }

        return new GradleWrapperInfo(wrapperFile, moduleDir, parsed.version(), parsed.allDistribution());
    }

    /**
     * The module directory a wrapper file belongs to, e.g. {@code app/gradle/wrapper/gradle-wrapper.properties}
     * belongs to the {@code app} module directory - this is where that module's {@code gradlew}
     * script lives.
     */
    @Nullable
    private static VirtualFile moduleDirFor(@NotNull VirtualFile wrapperFile) {
        VirtualFile wrapperDir = wrapperFile.getParent();
        VirtualFile gradleDir = wrapperDir != null ? wrapperDir.getParent() : null;
        return gradleDir != null ? gradleDir.getParent() : null;
    }
}
