package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Gradle build-script variables (those declared in an {@code ext { }} block) to
 * their literal values, and normalises variable references such as {@code ${name}} or
 * {@code $name} to the bare variable name.
 */
final class GradleVariableResolver {

    private static final String EXT_BLOCK_CONSTANT = "ext";
    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private GradleVariableResolver() {
    }

    /**
     * Resolves a Gradle variable to its value by searching the {@code ext} block.
     *
     * @param variableName the name of the variable (without the {@code $} prefix)
     * @param psiFile      the Gradle build file
     * @return the resolved value, or {@code null} if not found
     */
    @Nullable
    static String resolveVariable(@NotNull String variableName, @NotNull PsiFile psiFile) {
        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(psiFile, GrMethodCall.class)) {
            PsiElement methodElement = methodCall.getInvokedExpression();

            final boolean hasExtBlock = EXT_BLOCK_CONSTANT.equals(methodElement.getText());
            if (hasExtBlock) {
                String value = findVariableInExtBlock(variableName, methodCall);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Searches for a variable assignment within an {@code ext { }} block.
     */
    @Nullable
    private static String findVariableInExtBlock(@NotNull String variableName, @NotNull GrMethodCall extCall) {
        for (PsiElement child : extCall.getChildren()) {
            String text = child.getText();
            Pattern pattern = Pattern.compile(Pattern.quote(variableName) + "\\s*=\\s*['\"]([^'\"]+)['\"]");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Extracts the bare variable name from a reference: {@code ${spring_version}} or
     * {@code $spring_version} both yield {@code spring_version}. Returns {@code null} when the
     * value is not a variable reference.
     */
    @Nullable
    static String extractVariableName(@NotNull String version) {
        if (version.startsWith("${")) {
            Matcher matcher = INTERPOLATION_PATTERN.matcher(version);
            return matcher.find() ? matcher.group(1) : version;
        }

        if (version.startsWith("$")) {
            return version.substring(1);
        }

        return null;
    }
}
