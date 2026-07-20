package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile;

import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle.GradleBuildFileParser;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating appropriate dependency parsers based on file type.
 */
public class BuildFileParserFactory {

    /**
     * Gets the appropriate parser for the given file.
     *
     * @param file the PSI file
     * @return a parser instance, or null if no parser is available for this file type
     */
    @Nullable
    public static BuildFileParser getParser(@NotNull PsiFile file) {
        String fileName = file.getName();

        if ("build.gradle".equals(fileName)) {
            return new GradleBuildFileParser();
        }

        return null;
    }
}
