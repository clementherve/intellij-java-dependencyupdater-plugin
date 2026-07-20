package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for parsing dependency declarations from Gradle build files.
 */
public interface BuildFileParser {

    /**
     * Parses all dependency declarations from a PSI file.
     *
     * @param psiFile the PSI file to parse
     * @return a list of dependency information objects
     */
    @NotNull
    List<Dependency> parseDependencies(@NotNull PsiFile psiFile);

    /**
     * Checks if this parser can handle the given file.
     *
     * @param psiFile the PSI file to check
     * @return true if this parser can parse the file
     */
    boolean canParse(@NotNull PsiFile psiFile);
}
