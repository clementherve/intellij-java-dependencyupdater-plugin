package com.github.clementherve.intellijjavadependencyupdaterplugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides controller functionality for the settings UI.
 */
public class DependencyUpdaterConfigurable implements Configurable {

    private final Project myProject;
    private DependencyUpdaterSettingsPanel mySettingsPanel;

    public DependencyUpdaterConfigurable(Project project) {
        this.myProject = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Dependency Updater";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsPanel = new DependencyUpdaterSettingsPanel(myProject);
        return mySettingsPanel.getPanel();
    }

    @Override
    public boolean isModified() {
        if (mySettingsPanel == null) {
            return false;
        }
        return mySettingsPanel.isModified();
    }

    @Override
    public void apply() {
        if (mySettingsPanel != null) {
            mySettingsPanel.apply();
        }
    }

    @Override
    public void reset() {
        if (mySettingsPanel != null) {
            mySettingsPanel.reset();
        }
    }

    @Override
    public void disposeUIResources() {
        mySettingsPanel = null;
    }
}
