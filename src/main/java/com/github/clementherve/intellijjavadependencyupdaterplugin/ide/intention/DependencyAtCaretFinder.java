package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParserFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Locates the dependency declaration that contains a given caret position, by parsing the
 * build file and matching the element offset against each declaration's range.
 */
public final class DependencyAtCaretFinder {

    private DependencyAtCaretFinder() {
    }

    @Nullable
    public static Dependency find(@NotNull PsiFile file, @NotNull PsiElement element) {
        BuildFileParser parser = BuildFileParserFactory.getParser(file);
        if (parser == null) {
            return null;
        }

        List<Dependency> dependencies = parser.parseDependencies(file);
        int offset = element.getTextOffset();

        for (Dependency dependency : dependencies) {
            if (dependency.psiElementPointer() == null) {
                continue;
            }
            PsiElement declarationElement = dependency.psiElementPointer().getElement();
            if (declarationElement == null) {
                continue;
            }
            int start = declarationElement.getTextRange().getStartOffset();
            int end = declarationElement.getTextRange().getEndOffset();
            if (offset >= start && offset <= end) {
                return dependency;
            }
        }

        return null;
    }
}
