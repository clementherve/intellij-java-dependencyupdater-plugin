package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dependency declarations from Kotlin DSL build.gradle.kts files.
 */
public class KotlinDslPsiParser implements DependencyParser {

    private static final Logger LOG = Logger.getInstance(KotlinDslPsiParser.class);

    private static final List<String> KNOWN_CONFIGURATIONS = Arrays.asList(
        "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "testCompileOnly", "testRuntimeOnly",
        "annotationProcessor", "kapt"
    );

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "([^:]+):([^:]+):([^:]+)"  // group:artifact:version
    );

    @Override
    public boolean canParse(@NotNull PsiFile psiFile) {
        return "build.gradle.kts".equals(psiFile.getName());
    }

    @NotNull
    @Override
    public List<DependencyInfo> parseDependencies(@NotNull PsiFile psiFile) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        // Find all call expressions in the file
        for (KtCallExpression callExpr : PsiTreeUtil.findChildrenOfType(psiFile, KtCallExpression.class)) {
            try {
                DependencyInfo dependency = parseDependencyFromCallExpression(callExpr);
                if (dependency != null) {
                    dependencies.add(dependency);
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse dependency from call expression", e);
            }
        }

        LOG.info("Parsed " + dependencies.size() + " dependencies from " + psiFile.getName());
        return dependencies;
    }

    /**
     * Attempts to parse a dependency from a call expression.
     */
    @Nullable
    private DependencyInfo parseDependencyFromCallExpression(@NotNull KtCallExpression callExpr) {
        KtExpression calleeExpression = callExpr.getCalleeExpression();
        if (calleeExpression == null) {
            return null;
        }

        String methodName = calleeExpression.getText();
        if (!KNOWN_CONFIGURATIONS.contains(methodName)) {
            return null;
        }

        List<? extends ValueArgument> args = callExpr.getValueArguments();
        if (args.isEmpty()) {
            return null;
        }

        // Try string notation: implementation("group:artifact:version")
        ValueArgument firstArg = args.get(0);
        KtExpression argExpression = firstArg.getArgumentExpression();

        if (argExpression instanceof KtStringTemplateExpression) {
            return parseStringTemplateExpression(
                (KtStringTemplateExpression) argExpression,
                methodName
            );
        }

        return null;
    }

    /**
     * Parses a Kotlin string template expression.
     * Handles both simple strings and strings with variable references.
     */
    @Nullable
    private DependencyInfo parseStringTemplateExpression(@NotNull KtStringTemplateExpression templateExpr,
                                                         @NotNull String configurationName) {
        String dependencyString = buildDependencyString(templateExpr);
        if (dependencyString == null) {
            return null;
        }

        Matcher matcher = DEPENDENCY_PATTERN.matcher(dependencyString);
        if (!matcher.matches()) {
            return null;
        }

        String group = matcher.group(1);
        String artifact = matcher.group(2);
        String version = matcher.group(3);

        // Check if version is a variable reference
        boolean isVersionVariable = version.startsWith("$");
        String variableName = isVersionVariable ? version.substring(1).replaceAll("[{}]", "") : null;

        SmartPsiElementPointer<PsiElement> pointer =
            SmartPointerManager.createPointer(templateExpr);

        return new DependencyInfo(
            group,
            artifact,
            version,
            configurationName,
            pointer,
            isVersionVariable,
            variableName
        );
    }

    /**
     * Builds a dependency string from a string template expression.
     * Handles both literal strings and variable references.
     */
    @Nullable
    private String buildDependencyString(@NotNull KtStringTemplateExpression templateExpr) {
        StringBuilder result = new StringBuilder();

        for (KtStringTemplateEntry entry : templateExpr.getEntries()) {
            if (entry instanceof KtLiteralStringTemplateEntry) {
                result.append(entry.getText());
            } else if (entry instanceof KtSimpleNameStringTemplateEntry) {
                // Variable reference like $version
                result.append("$").append(((KtSimpleNameStringTemplateEntry) entry).getExpression().getText());
            } else if (entry instanceof KtBlockStringTemplateEntry) {
                // Variable reference like ${version}
                KtBlockStringTemplateEntry blockEntry = (KtBlockStringTemplateEntry) entry;
                KtExpression expression = blockEntry.getExpression();
                if (expression != null) {
                    result.append("${").append(expression.getText()).append("}");
                }
            }
        }

        return result.toString();
    }
}
