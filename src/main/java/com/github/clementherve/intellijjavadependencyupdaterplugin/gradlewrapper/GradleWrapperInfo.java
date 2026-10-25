package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * The Gradle version currently pinned by one {@code gradle-wrapper.properties} file, the file it
 * was read from, and the directory of the module (subproject) it belongs to - a project can
 * contain several wrapper files, e.g. a separate {@code app/} and {@code test/} module each with
 * their own Gradle wrapper. {@code moduleDir} is where the module's {@code gradlew} script lives,
 * i.e. the parent of its {@code gradle/wrapper/} directory.
 */
public record GradleWrapperInfo(@NotNull VirtualFile file, @NotNull VirtualFile moduleDir,
                                @NotNull String currentVersion, boolean allDistribution) {

    @NotNull
    public String moduleName() {
        return moduleDir.getName();
    }
}
