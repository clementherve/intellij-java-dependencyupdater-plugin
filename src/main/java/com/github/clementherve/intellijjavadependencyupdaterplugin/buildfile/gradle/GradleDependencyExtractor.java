package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a dependency declaration from a single Groovy method call, supporting the three
 * notations Gradle accepts: string ({@code "group:artifact:version"}), interpolated GString
 * ({@code "group:artifact:$version"}) and map ({@code group: 'g', name: 'a', version: 'v'}).
 */
final class GradleDependencyExtractor {

    private static final List<String> KNOWN_CONFIGURATIONS = Arrays.asList(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompileOnly", "testRuntimeOnly",
            "annotationProcessor", "kapt"
    );

    // group:artifact:version
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("([^:]+):([^:]+):([^:]+)?");

    private GradleDependencyExtractor() {
    }

    /**
     * Attempts to parse a dependency from a method call expression.
     * In Groovy, everything is a function, i.e.: {@code <name> <argument list>}.
     */
    @Nullable
    static Dependency extract(@NotNull GrMethodCall methodCall, @NotNull PsiFile psiFile) {
        PsiElement methodElement = methodCall.getInvokedExpression();

        String methodName = methodElement.getText();
        final boolean unknownMethodName = !KNOWN_CONFIGURATIONS.contains(methodName);
        if (unknownMethodName) {
            return null;
        }

        GrArgumentList argumentList = methodCall.getArgumentList();

        final boolean emptyMethodArguments = argumentList.getAllArguments().length == 0;
        if (emptyMethodArguments) {
            return null;
        }

        // cas: implementation 'org.springframework:spring-core:6.0'
        GrExpression[] expressionArguments = argumentList.getExpressionArguments();
        final boolean hasExpressionArguments = expressionArguments.length > 0;
        if (hasExpressionArguments) {
            if (expressionArguments[0] instanceof final GrLiteral literal) {
                Object value = literal.getValue();

                if (value instanceof String) {
                    // string notation, no variable
                    return parseStringNotation((String) value, methodName, literal, psiFile);
                } else if (literal instanceof final GrString gstring) {
                    // GString notation, with variables
                    return parseGStringNotation(gstring, methodName, psiFile);
                }
            }
        }

        // cas: implementation(group: 'org.springframework', name: 'spring-core', version: '6.0')
        GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
        final boolean hasNamedArguments = namedArguments.length > 0;
        if (hasNamedArguments) {
            return parseMapNotation(namedArguments, methodName, psiFile);
        }

        return null;
    }

    /**
     * Parses GString notation with variable interpolation: {@code "group:artifact:$version"}
     */
    @Nullable
    private static Dependency parseGStringNotation(@NotNull GrString gstring,
                                                   @NotNull String configurationName,
                                                   @NotNull PsiFile psiFile) {
        StringBuilder fullString = new StringBuilder();
        String variableName;
        boolean hasVariable = false;

        for (PsiElement child : gstring.getChildren()) {
            fullString.append(child.getText());
        }

        String dependencyString = fullString.toString();
        Matcher matcher = DEPENDENCY_PATTERN.matcher(dependencyString);
        if (!matcher.matches()) {
            return null;
        }

        String group = matcher.group(1);
        String artifact = matcher.group(2);
        variableName = matcher.group(3);

        if (variableName == null) {
            return null;
        }

        String resolvedVariable;

        if (variableName.startsWith("$")) {
            variableName = GradleVariableResolver.extractVariableName(variableName);
            resolvedVariable = GradleVariableResolver.resolveVariable(variableName, psiFile);
            hasVariable = true;
        } else {
            resolvedVariable = variableName;
        }

        if (StringUtils.isBlank(resolvedVariable)) {
            resolvedVariable = "";
            hasVariable = false;
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(gstring);

        return new Dependency(
                group,
                artifact,
                resolvedVariable,
                configurationName,
                pointer,
                hasVariable,
                variableName
        );
    }

    /**
     * Parses string notation: {@code "group:artifact:version"}
     */
    @Nullable
    private static Dependency parseStringNotation(@NotNull String dependencyString,
                                                  @NotNull String configurationName,
                                                  @NotNull PsiElement versionElement,
                                                  @NotNull PsiFile psiFile) {
        Matcher matcher = DEPENDENCY_PATTERN.matcher(dependencyString);
        if (!matcher.matches()) {
            return null;
        }

        String group = matcher.group(1);
        String artifact = matcher.group(2);
        String version = matcher.group(3);

        boolean isVersionVariable = version.startsWith("$");
        String variableName = null;
        String resolvedVersion = version;

        if (isVersionVariable) {
            variableName = GradleVariableResolver.extractVariableName(version);
            String resolved = GradleVariableResolver.resolveVariable(variableName, psiFile);
            if (resolved != null) {
                resolvedVersion = resolved;
            }
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(versionElement);

        return new Dependency(
                group,
                artifact,
                resolvedVersion,
                configurationName,
                pointer,
                isVersionVariable,
                variableName
        );
    }

    /**
     * Parses map notation: {@code group: 'g', name: 'a', version: 'v'}
     */
    @Nullable
    private static Dependency parseMapNotation(@NotNull GrNamedArgument[] namedArgs,
                                               @NotNull String configurationName,
                                               @NotNull PsiFile psiFile) {
        String group = null;
        String artifact = null;
        String version = null;
        PsiElement versionElement = null;

        for (GrNamedArgument arg : namedArgs) {
            String argName = arg.getLabelName();
            GrExpression expression = arg.getExpression();

            if (expression instanceof GrLiteral) {
                Object value = ((GrLiteral) expression).getValue();
                if (value instanceof String) {
                    switch (argName) {
                        case "group":
                            group = (String) value;
                            break;
                        case "name":
                            artifact = (String) value;
                            break;
                        case "version":
                            version = (String) value;
                            versionElement = expression;
                            break;
                        case null:
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + argName);
                    }
                }
            }
        }

        if (group == null || artifact == null || version == null) {
            return null;
        }

        boolean isVersionVariable = version.startsWith("$");
        String variableName = null;
        String resolvedVersion = version;

        if (isVersionVariable) {
            variableName = GradleVariableResolver.extractVariableName(version);
            String resolved = GradleVariableResolver.resolveVariable(variableName, psiFile);
            if (resolved != null) {
                resolvedVersion = resolved;
            }
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(versionElement);

        return new Dependency(
                group,
                artifact,
                resolvedVersion,
                configurationName,
                pointer,
                isVersionVariable,
                variableName
        );
    }
}
