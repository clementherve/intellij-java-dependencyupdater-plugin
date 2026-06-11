package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.gradle;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Extracts a plugin declaration from a {@code plugins { }} block.
 * <p>
 * Format: {@code id 'plugin.id' version 'version'}. In Groovy this is a chained method call
 * where {@code version} is invoked on the result of {@code id}, so the PSI structure is
 * {@code GrMethodCall(version) -> GrReferenceExpression(id(...))}.
 */
final class GradlePluginExtractor {

    private GradlePluginExtractor() {
    }

    @Nullable
    static Dependency extract(@NotNull GrMethodCall methodCall) {
        PsiElement invokedExpression = methodCall.getInvokedExpression();
        String methodName;

        // Extract just the method name from the reference (not the full chained text)
        if (invokedExpression instanceof GrReferenceExpression) {
            methodName = ((GrReferenceExpression) invokedExpression).getReferenceName();
        } else {
            methodName = invokedExpression.getText();
        }

        // Only the outer 'version' call in: id 'plugin.id' version 'version'
        if (!"version".equals(methodName)) {
            return null;
        }

        GrArgumentList argumentList = methodCall.getArgumentList();
        GrExpression[] versionArguments = argumentList.getExpressionArguments();

        if (versionArguments.length == 0 || !(versionArguments[0] instanceof GrLiteral)) {
            return null;
        }

        Object versionValue = ((GrLiteral) versionArguments[0]).getValue();
        if (!(versionValue instanceof final String version)) {
            return null;
        }

        PsiElement versionElement = versionArguments[0];

        // The invoked expression should be a reference with a qualifier that is the 'id' call
        if (!(invokedExpression instanceof final GrReferenceExpression referenceExpression)) {
            return null;
        }

        GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (!(qualifier instanceof final GrMethodCall idCall)) {
            return null;
        }

        PsiElement idInvoked = idCall.getInvokedExpression();
        if (!"id".equals(idInvoked.getText())) {
            return null;
        }

        if (!isInsidePluginsBlock(methodCall)) {
            return null;
        }

        GrArgumentList idArgumentList = idCall.getArgumentList();
        GrExpression[] idArguments = idArgumentList.getExpressionArguments();

        if (idArguments.length == 0 || !(idArguments[0] instanceof GrLiteral)) {
            return null;
        }

        Object pluginIdValue = ((GrLiteral) idArguments[0]).getValue();
        if (!(pluginIdValue instanceof final String pluginId)) {
            return null;
        }

        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(versionElement);

        return new Dependency(
                "",        // empty group for plugins
                pluginId,  // artifact is the plugin ID
                version,
                "plugin",
                pointer,
                false,
                null
        );
    }

    private static boolean isInsidePluginsBlock(@NotNull GrMethodCall methodCall) {
        PsiElement ancestor = methodCall.getParent();
        while (ancestor != null) {
            if (ancestor instanceof GrMethodCall) {
                PsiElement ancestorMethod = ((GrMethodCall) ancestor).getInvokedExpression();
                if ("plugins".equals(ancestorMethod.getText())) {
                    return true;
                }
            }
            ancestor = ancestor.getParent();
        }
        return false;
    }
}
