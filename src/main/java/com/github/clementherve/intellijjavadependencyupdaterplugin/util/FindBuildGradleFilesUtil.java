package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FindBuildGradleFilesUtil {
    public static List<VirtualFile> findBuildGradleFilesInCurrentProject(Project project) {
        return ReadAction.compute(() -> {
            List<VirtualFile> files = new ArrayList<>();
            VirtualFile baseDir = project.getBaseDir(); // fixme: getBaseDir is deprecated

            if (baseDir != null) {
                VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        // Skip common directories
                        if (file.isDirectory()) {
                            String name = file.getName();
                            return !name.startsWith(".") && !name.equals("build") && !name.equals("node_modules") && !name.equals("target");
                        }

                        if (SupportedFilesUtil.isSupportedFile(file.getName())) {
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
