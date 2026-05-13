package com.github.clementherve.intellijjavadependencyupdaterplugin.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a dependency declaration found in a Gradle build file.
 */
public class DependencyInfo {
    private final String group;
    private final String artifact;
    private final String currentVersion;
    private final String configurationName;
    private final SmartPsiElementPointer<PsiElement> psiElementPointer;
    private final boolean isVersionVariable;
    private final String variableName;

    public DependencyInfo(@NotNull String group,
                         @NotNull String artifact,
                         @NotNull String currentVersion,
                         @NotNull String configurationName,
                         @Nullable SmartPsiElementPointer<PsiElement> psiElementPointer,
                         boolean isVersionVariable,
                         @Nullable String variableName) {
        this.group = group;
        this.artifact = artifact;
        this.currentVersion = currentVersion;
        this.configurationName = configurationName;
        this.psiElementPointer = psiElementPointer;
        this.isVersionVariable = isVersionVariable;
        this.variableName = variableName;
    }

    @NotNull
    public String getGroup() {
        return group;
    }

    @NotNull
    public String getArtifact() {
        return artifact;
    }

    @NotNull
    public String getCurrentVersion() {
        return currentVersion;
    }

    @NotNull
    public String getConfigurationName() {
        return configurationName;
    }

    @Nullable
    public SmartPsiElementPointer<PsiElement> getPsiElementPointer() {
        return psiElementPointer;
    }

    public boolean isVersionVariable() {
        return isVersionVariable;
    }

    @Nullable
    public String getVariableName() {
        return variableName;
    }

    @NotNull
    public String getCoordinates() {
        return group + ":" + artifact;
    }

    @NotNull
    public String getFullCoordinates() {
        return group + ":" + artifact + ":" + currentVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyInfo that = (DependencyInfo) o;
        return Objects.equals(group, that.group) &&
               Objects.equals(artifact, that.artifact) &&
               Objects.equals(currentVersion, that.currentVersion) &&
               Objects.equals(configurationName, that.configurationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, artifact, currentVersion, configurationName);
    }

    @Override
    public String toString() {
        return "DependencyInfo{" +
               configurationName + "('" +
               group + ":" + artifact + ":" + currentVersion + "'" +
               (isVersionVariable ? ", variable=" + variableName : "") +
               ")}";
    }
}
