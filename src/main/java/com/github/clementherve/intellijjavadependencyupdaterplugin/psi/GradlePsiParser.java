package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dependency declarations from Groovy build.gradle files.
 */
public class GradlePsiParser implements DependencyParser {

    private static final Logger LOGGER = Logger.getInstance(GradlePsiParser.class);

    private static final List<String> KNOWN_CONFIGURATIONS = Arrays.asList(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompileOnly", "testRuntimeOnly",
            "annotationProcessor", "kapt"
    );

    // group:artifact:version
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "([^:]+):([^:]+):([^:]+)?"
    );
    private static final String EXT_BLOCK_CONSTANT = "ext";

    @Override
    public boolean canParse(@NotNull PsiFile psiFile) {
        return "build.gradle".equals(psiFile.getName());
    }

    @NotNull
    @Override
    public List<DependencyInfo> parseDependencies(@NotNull PsiFile psiFile) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(psiFile, GrMethodCall.class)) {
            try {
                DependencyInfo dependency = parseDependencyFromMethodCall(methodCall, psiFile);
                if (dependency != null) {
                    dependencies.add(dependency);
                }
            } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                // Rethrow - this is a control flow exception, not an error
                throw e;
            } catch (Exception e) {
                LOGGER.debug("Failed to parse dependency from method call", e);
            }
        }

        return dependencies;
    }

    /**
     * Attempts to parse a dependency from a method call expression.
     */
    private DependencyInfo parseDependencyFromMethodCall(@NotNull GrMethodCall methodCall, @NotNull PsiFile psiFile) {
        PsiElement methodElement = methodCall.getInvokedExpression();

        String methodName = methodElement.getText();
        if (!KNOWN_CONFIGURATIONS.contains(methodName)) {
            return null;
        }

        GrArgumentList argumentList = methodCall.getArgumentList();
        if (argumentList.getAllArguments().length == 0) {
            return null;
        }

        GrExpression[] expressionArguments = argumentList.getExpressionArguments();
        if (expressionArguments.length > 0) {
            if (expressionArguments[0] instanceof final GrLiteral literal) {
                Object value = literal.getValue();
                if (value instanceof String) {
                    return parseStringNotation((String) value, methodName, literal, psiFile);
                }

                if (literal instanceof final GrString gstring) {
                    return parseGStringNotation(gstring, methodName, psiFile);
                }
            }
        }

        GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
        if (namedArguments.length > 0) {
            return parseMapNotation(namedArguments, methodName, psiFile);
        }

        return null;
    }

    /**
     * Parses GString notation with variable interpolation: "group:artifact:$version"
     */
    private DependencyInfo parseGStringNotation(@NotNull GrString gstring,
                                                @NotNull String configurationName,
                                                @NotNull PsiFile psiFile) {
        StringBuilder fullString = new StringBuilder();
        String variableName;
        boolean hasVariable = false;

        //  Walk through the GString structure to build the full dependency string
        // and detect variable references
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
            variableName = variableName.substring(1);
            resolvedVariable = resolveVariable(variableName, psiFile);
            hasVariable = true;
        } else {
            resolvedVariable = variableName;
        }

        if (StringUtils.isBlank(resolvedVariable)) {
            resolvedVariable = "";
            hasVariable = false;
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(gstring);

        return new DependencyInfo(
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
     * Parses string notation: "group:artifact:version"
     */
    private DependencyInfo parseStringNotation(@NotNull String dependencyString,
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

        // Check if version is a variable reference (starts with $)
        boolean isVersionVariable = version.startsWith("$");
        String variableName = null;
        String resolvedVersion = version;

        if (isVersionVariable) {
            variableName = version.substring(1);
            // Resolve the variable to its actual value
            String resolved = resolveVariable(variableName, psiFile);
            if (resolved != null) {
                resolvedVersion = resolved;
            } else {
                LOGGER.debug("Could not resolve variable: " + variableName);
            }
        }

        SmartPsiElementPointer<PsiElement> pointer =
                SmartPointerManager.createPointer(versionElement);

        return new DependencyInfo(
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
     * Parses map notation: group: 'g', name: 'a', version: 'v'
     */
    private DependencyInfo parseMapNotation(@NotNull GrNamedArgument[] namedArgs,
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
                    }
                }
            }
        }

        if (group == null || artifact == null || version == null) {
            return null;
        }

        // Check if version is a variable reference (starts with $)
        boolean isVersionVariable = version.startsWith("$");
        String variableName = null;
        String resolvedVersion = version;

        if (isVersionVariable) {
            variableName = version.substring(1);
            // Resolve the variable to its actual value
            String resolved = resolveVariable(variableName, psiFile);
            if (resolved != null) {
                resolvedVersion = resolved;
            } else {
                LOGGER.debug("Could not resolve variable: " + variableName);
            }
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(versionElement);

        return new DependencyInfo(
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
     * Resolves a Gradle variable to its value by searching the ext block and project properties.
     *
     * @param variableName the name of the variable (without the $ prefix)
     * @param psiFile      the Gradle build file
     * @return the resolved value, or null if not found
     */
    private String resolveVariable(@NotNull String variableName, @NotNull PsiFile psiFile) {
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
     * Searches for a variable assignment within an ext{} block.
     * todo: optimization: parse the ext block once and cache the result
     */
    private String findVariableInExtBlock(@NotNull String variableName, @NotNull GrMethodCall extCall) {
        for (PsiElement child : extCall.getChildren()) {
            String text = child.getText();
            Pattern pattern = Pattern.compile(variableName + "\\s*=\\s*['\"]([^'\"]+)['\"]");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
