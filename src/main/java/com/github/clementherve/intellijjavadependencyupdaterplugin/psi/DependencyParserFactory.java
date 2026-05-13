package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating appropriate dependency parsers based on file type.
 */
public class DependencyParserFactory {

    /**
     * Gets the appropriate parser for the given file.
     *
     * @param file the PSI file
     * @return a parser instance, or null if no parser is available for this file type
     */
    @Nullable
    public static DependencyParser getParser(@NotNull PsiFile file) {
        String fileName = file.getName();

        if ("build.gradle".equals(fileName)) {
            return new GradlePsiParser();
        } else if ("build.gradle.kts".equals(fileName)) {
            return new KotlinDslPsiParser();
        }

        return null;
    }
}
