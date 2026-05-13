package com.github.clementherve.intellijjavadependencyupdaterplugin.psi;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dependency declarations from Groovy build.gradle files.
 */
public class GradlePsiParser implements DependencyParser {

    private static final Logger LOG = Logger.getInstance(GradlePsiParser.class);

    private static final List<String> KNOWN_CONFIGURATIONS = Arrays.asList(
        "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "testCompileOnly", "testRuntimeOnly",
        "annotationProcessor", "kapt"
    );

    // fixme:
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
        "([^:]+):([^:]+):([^:]+)"  // group:artifact:version
    );

    @Override
    public boolean canParse(@NotNull PsiFile psiFile) {
        return "build.gradle".equals(psiFile.getName());
    }

    @NotNull
    @Override
    public List<DependencyInfo> parseDependencies(@NotNull PsiFile psiFile) {
        List<DependencyInfo> dependencies = new ArrayList<>();

        // Find all method calls in the file
        for (GrMethodCall methodCall : PsiTreeUtil.findChildrenOfType(psiFile, GrMethodCall.class)) {
            try {
                DependencyInfo dependency = parseDependencyFromMethodCall(methodCall);
                if (dependency != null) {
                    dependencies.add(dependency);
                }
            } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                // Rethrow - this is a control flow exception, not an error
                throw e;
            } catch (Exception e) {
                LOG.debug("Failed to parse dependency from method call", e);
            }
        }

        LOG.info("Parsed " + dependencies.size() + " dependencies from " + psiFile.getName());
        return dependencies;
    }

    /**
     * Attempts to parse a dependency from a method call expression.
     */
    private DependencyInfo parseDependencyFromMethodCall(@NotNull GrMethodCall methodCall) {
        PsiElement methodElement = methodCall.getInvokedExpression();
        if (methodElement == null) {
            return null;
        }

        String methodName = methodElement.getText();
        if (!KNOWN_CONFIGURATIONS.contains(methodName)) {
            return null;
        }

        GrArgumentList argumentList = methodCall.getArgumentList();
        if (argumentList == null || argumentList.getAllArguments().length == 0) {
            return null;
        }

        // Try string notation first: implementation("group:artifact:version")
        GrExpression[] args = argumentList.getExpressionArguments();
        if (args.length > 0 && args[0] instanceof GrLiteral) {
            GrLiteral literal = (GrLiteral) args[0];
            Object value = literal.getValue();
            if (value instanceof String) {
                return parseStringNotation((String) value, methodName, literal);
            }
        }

        // Try map notation: implementation(group: 'g', name: 'a', version: 'v')
        GrNamedArgument[] namedArgs = argumentList.getNamedArguments();
        if (namedArgs.length > 0) {
            return parseMapNotation(namedArgs, methodName);
        }

        return null;
    }

    /**
     * Parses string notation: "group:artifact:version"
     */
    private DependencyInfo parseStringNotation(@NotNull String dependencyString,
                                               @NotNull String configurationName,
                                               @NotNull PsiElement versionElement) {
        Matcher matcher = DEPENDENCY_PATTERN.matcher(dependencyString);
        if (!matcher.matches()) {
            return null;
        }

        String group = matcher.group(1);
        String artifact = matcher.group(2);
        String version = matcher.group(3);

        // Check if version is a variable reference (starts with $)
        boolean isVersionVariable = version.startsWith("$");
        String variableName = isVersionVariable ? version.substring(1) : null;

        SmartPsiElementPointer<PsiElement> pointer =
            SmartPointerManager.createPointer(versionElement);

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
     * Parses map notation: group: 'g', name: 'a', version: 'v'
     */
    private DependencyInfo parseMapNotation(@NotNull GrNamedArgument[] namedArgs,
                                           @NotNull String configurationName) {
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

        boolean isVersionVariable = version.startsWith("$");
        String variableName = isVersionVariable ? version.substring(1) : null;

        SmartPsiElementPointer<PsiElement> pointer = versionElement != null ?
            SmartPointerManager.createPointer(versionElement) : null;

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
}
