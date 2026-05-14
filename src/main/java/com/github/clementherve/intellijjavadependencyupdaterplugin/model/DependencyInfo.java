package com.github.clementherve.intellijjavadependencyupdaterplugin.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a dependency declaration found in a Gradle build file.
 */
public record DependencyInfo(String group, String artifact, String currentVersion, String configurationName,
                             SmartPsiElementPointer<PsiElement> psiElementPointer, boolean isVersionVariable,
                             String variableName) {
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

    @Override
    @NotNull
    public String group() {
        return group;
    }

    @Override
    @NotNull
    public String artifact() {
        return artifact;
    }

    @Override
    @NotNull
    public String currentVersion() {
        return currentVersion;
    }

    @Override
    @NotNull
    public String configurationName() {
        return configurationName;
    }

    @Override
    @Nullable
    public SmartPsiElementPointer<PsiElement> psiElementPointer() {
        return psiElementPointer;
    }

    @Override
    @Nullable
    public String variableName() {
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

    @NotNull
    @Override
    public String toString() {
        return "DependencyInfo{" +
                configurationName + "('" +
                group + ":" + artifact + ":" + currentVersion + "'" +
                (isVersionVariable ? ", variable=" + variableName : "") +
                ")}";
    }
}
