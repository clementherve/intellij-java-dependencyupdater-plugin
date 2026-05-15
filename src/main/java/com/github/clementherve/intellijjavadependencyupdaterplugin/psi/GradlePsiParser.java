package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
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
        return SupportedFilesUtil.isSupportedFile(psiFile.getName());
    }

    @NotNull
    @Override
    public List<DependencyInfo> parseDependencies(@NotNull PsiFile psiFile) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(psiFile, GrMethodCall.class)) {
            try {
                // Parse regular dependencies
                DependencyInfo dependency = parseDependencyFromMethodCall(methodCall, psiFile);
                if (dependency != null) {
                    dependencies.add(dependency);
                }

                // Parse plugin declarations
                DependencyInfo plugin = parsePluginFromMethodCall(methodCall, psiFile);
                if (plugin != null) {
                    dependencies.add(plugin);
                }
            } catch (ProcessCanceledException e) {
                // Rethrow - this is a control flow exception, not an error
                throw e;
            } catch (Exception e) {
                LOGGER.debug("Failed to parse dependency from method call", e);
            }
        }

        return dependencies;
    }

    /**
     * Attempts to parse a plugin declaration from the plugins block.
     * Format: id 'plugin.id' version 'version'
     * <p>
     * In Groovy, this syntax is a chained method call where 'version' is called on the result of 'id'.
     * The PSI structure looks like: GrMethodCall(version) -> GrReferenceExpression(id(...))
     */
    private DependencyInfo parsePluginFromMethodCall(@NotNull GrMethodCall methodCall, @NotNull PsiFile psiFile) {
        // Get the actual method name (not the full text which includes the chain)
        PsiElement invokedExpression = methodCall.getInvokedExpression();
        String methodName;

        // Extract just the method name from the reference
        if (invokedExpression instanceof GrReferenceExpression) {
            methodName = ((GrReferenceExpression) invokedExpression).getReferenceName();
        } else {
            methodName = invokedExpression.getText();
        }

        // Check if this is a 'version' method call that wraps an 'id' call
        if ("version".equals(methodName)) {
            // This is the outer 'version' call in: id 'plugin.id' version 'version'
            GrArgumentList argumentList = methodCall.getArgumentList();
            GrExpression[] versionArguments = argumentList.getExpressionArguments();

            if (versionArguments.length > 0 && versionArguments[0] instanceof GrLiteral) {
                Object versionValue = ((GrLiteral) versionArguments[0]).getValue();

                if (versionValue instanceof final String version) {
                    PsiElement versionElement = versionArguments[0];

                    // The invoked expression should be a reference expression with a qualifier that is the 'id' call
                    if (invokedExpression instanceof final GrReferenceExpression refExpr) {
                        GrExpression qualifier = refExpr.getQualifierExpression();

                        if (qualifier instanceof final GrMethodCall idCall) {
                            PsiElement idInvoked = idCall.getInvokedExpression();
                            String idMethodName = idInvoked.getText();

                            if ("id".equals(idMethodName)) {
                                // Check if inside plugins block
                                boolean insidePluginsBlock = false;
                                PsiElement ancestor = methodCall.getParent();
                                while (ancestor != null) {
                                    if (ancestor instanceof GrMethodCall) {
                                        PsiElement ancestorMethod = ((GrMethodCall) ancestor).getInvokedExpression();
                                        String ancestorName = ancestorMethod.getText();
                                        if ("plugins".equals(ancestorName)) {
                                            insidePluginsBlock = true;
                                            break;
                                        }
                                    }
                                    ancestor = ancestor.getParent();
                                }

                                if (!insidePluginsBlock) {
                                    return null;
                                }

                                // Extract plugin ID from the 'id' call
                                GrArgumentList idArgList = idCall.getArgumentList();
                                GrExpression[] idArgs = idArgList.getExpressionArguments();

                                if (idArgs.length > 0 && idArgs[0] instanceof GrLiteral) {
                                    Object pluginIdValue = ((GrLiteral) idArgs[0]).getValue();
                                    if (pluginIdValue instanceof final String pluginId) {

                                        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(versionElement);

                                        return new DependencyInfo(
                                                "", // empty group for plugins
                                                pluginId, // artifact is the plugin ID
                                                version,
                                                "plugin",
                                                pointer,
                                                false,
                                                null
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
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

        boolean isVersionVariable = version.startsWith("$");
        String variableName = null;
        String resolvedVersion = version;

        if (isVersionVariable) {
            variableName = version.substring(1);
            String resolved = resolveVariable(variableName, psiFile);
            if (resolved != null) {
                resolvedVersion = resolved;
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
