package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle;

import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.BuildFileParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses dependency and plugin declarations from Groovy build.gradle files by walking every
 * method call and delegating each to the dependency and plugin extractors.
 */
public class GradleBuildFileParser implements BuildFileParser {

    private static final Logger LOGGER = Logger.getInstance(GradleBuildFileParser.class);

    @Override
    public boolean canParse(@NotNull PsiFile psiFile) {
        return SupportedBuildFile.isSupportedFile(psiFile.getName());
    }

    @NotNull
    @Override
    public List<Dependency> parseDependencies(@NotNull PsiFile psiFile) {
        List<Dependency> dependencies = new ArrayList<>();

        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(psiFile, GrMethodCall.class)) {
            try {
                Dependency dependency = GradleDependencyExtractor.extract(methodCall, psiFile);
                if (dependency != null) {
                    dependencies.add(dependency);
                }

                Dependency plugin = GradlePluginExtractor.extract(methodCall);
                if (plugin != null) {
                    dependencies.add(plugin);
                }
            } catch (ProcessCanceledException exception) {
                // Rethrow - this is a control flow exception, not an error
                throw exception;
            } catch (Exception exception) {
                LOGGER.debug("Failed to parse dependency from method call", exception);
            }
        }

        return dependencies;
    }
}
