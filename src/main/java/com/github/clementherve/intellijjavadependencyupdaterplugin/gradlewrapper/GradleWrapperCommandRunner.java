package com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Updates a project's Gradle wrapper the same way a developer would from the command line, by
 * running {@code ./gradlew wrapper --gradle-version=<version>} in the module's directory. This
 * (rather than hand-editing {@code gradle-wrapper.properties}) is what actually keeps
 * {@code gradle-wrapper.jar}, {@code gradlew} and {@code gradlew.bat} consistent with the new
 * version, exactly as Gradle's own wrapper upgrade process does.
 */
public final class GradleWrapperCommandRunner {

    private static final int TIMEOUT_MILLIS = 5 * 60 * 1000;

    private GradleWrapperCommandRunner() {
    }

    /**
     * Runs {@code ./gradlew wrapper --gradle-version=newVersion} (or {@code gradlew.bat} on
     * Windows) in {@code info.moduleDir()}. WARNING: blocks the calling thread for the duration of
     * the process - do NOT call from EDT or read actions!
     *
     * @throws IOException if the wrapper script is missing, the process fails, or times out
     */
    public static void run(@NotNull GradleWrapperInfo info, @NotNull String newVersion, @NotNull ProgressIndicator indicator) throws IOException {
        File script = new File(info.moduleDir().getPath(), SystemInfo.isWindows ? "gradlew.bat" : "gradlew");
        if (!script.isFile()) {
            throw new IOException("Gradle wrapper script not found: " + script.getPath());
        }
        if (!SystemInfo.isWindows) {
            script.setExecutable(true);
        }

        GeneralCommandLine commandLine = new GeneralCommandLine(script.getPath(), "wrapper", "--gradle-version=" + newVersion)
                .withWorkDirectory(info.moduleDir().getPath());

        ProcessOutput output;
        try {
            output = new CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator, TIMEOUT_MILLIS);
        } catch (ExecutionException exception) {
            throw new IOException("Failed to run gradlew wrapper for " + info.moduleName(), exception);
        }

        if (output.isCancelled()) {
            throw new ProcessCanceledException();
        }
        if (output.isTimeout()) {
            throw new IOException("gradlew wrapper timed out for " + info.moduleName());
        }
        if (output.getExitCode() != 0) {
            throw new IOException("gradlew wrapper failed for " + info.moduleName() + " (exit " + output.getExitCode() + "): " + output.getStderr());
        }

        refreshWrapperFiles(info.moduleDir());
    }

    // The process rewrites gradle-wrapper.properties, gradle-wrapper.jar, gradlew and gradlew.bat
    // on disk, outside the IDE's knowledge - refresh so the VFS/documents pick up the new content.
    private static void refreshWrapperFiles(@NotNull VirtualFile moduleDir) {
        moduleDir.refresh(false, true);
    }
}
