package com.github.clementherve.intellijjavadependencyupdaterplugin.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides controller functionality for the settings UI.
 */
public class DependencyUpdaterConfigurable implements Configurable {

    private DependencyUpdaterSettingsPanel mySettingsPanel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Dependency Updater";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsPanel = new DependencyUpdaterSettingsPanel();
        mySettingsPanel.reset();
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
