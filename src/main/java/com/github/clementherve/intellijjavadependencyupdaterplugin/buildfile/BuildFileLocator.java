package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BuildFileLocator {
    public static List<VirtualFile> findBuildGradleFilesInCurrentProject(Project project) {
        return ApplicationManager.getApplication().runReadAction((Computable<List<VirtualFile>>) () -> {
            List<VirtualFile> files = new ArrayList<>();
            VirtualFile baseDir = ProjectUtil.guessProjectDir(project);

            if (baseDir != null) {
                VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        // Skip common big directories
                        if (file.isDirectory()) {
                            String name = file.getName();
                            return !name.startsWith(".") && !name.equals("build") && !name.equals("node_modules") && !name.equals("target");
                        }

                        if (SupportedBuildFile.isSupportedFile(file.getName())) {
                            files.add(file);
                        }
                        return true;
                    }
                });
            }

            return files;
        });
    }
}
