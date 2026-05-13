package com.github.clementherve.intellijjavadependencyupdaterplugin.settings;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The settings UI panel.
 */
public class DependencyUpdaterSettingsPanel {

    private final Project myProject;
    private final JPanel myMainPanel;

    private final JBTextField nexusBaseUrlField;
    private final JBTextField nexusUsernameField;
    private final JBPasswordField nexusPasswordField;
    private final JBCheckBox fallbackToMavenCentralCheckBox;
    private final JSpinner cacheTtlSpinner;
    private final JBCheckBox showGutterIconsCheckBox;
    private final JBCheckBox showInlayHintsCheckBox;
    private final ComboBox<DependencyUpdaterSettings.TriggerMode> triggerModeComboBox;
    private final JBTextField versionFilterRegexField;
    private String cachedPassword = "";

    public DependencyUpdaterSettingsPanel(@NotNull Project project) {
        this.myProject = project;

        nexusBaseUrlField = new JBTextField();
        nexusUsernameField = new JBTextField();
        nexusPasswordField = new JBPasswordField();
        fallbackToMavenCentralCheckBox = new JBCheckBox("Fallback to Maven Central if Nexus is unavailable");

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(30, 1, 1440, 5);
        cacheTtlSpinner = new JSpinner(spinnerModel);

        showGutterIconsCheckBox = new JBCheckBox("Show gutter icons for outdated dependencies");
        showInlayHintsCheckBox = new JBCheckBox("Show inlay hints with available versions");

        versionFilterRegexField = new JBTextField();

        triggerModeComboBox = new ComboBox<>(DependencyUpdaterSettings.TriggerMode.values());
        triggerModeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DependencyUpdaterSettings.TriggerMode) {
                    setText(((DependencyUpdaterSettings.TriggerMode) value).getDisplayName());
                }
                return this;
            }
        });

        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Nexus Base URL:"), nexusBaseUrlField, 1, false)
                .addComponentToRightColumn(new JBLabel("Example: https://nexus.company.com"), 0)
                .addLabeledComponent(new JBLabel("Nexus username:"), nexusUsernameField, 1, false)
                .addLabeledComponent(new JBLabel("Nexus password:"), nexusPasswordField, 1, false)
                .addComponent(fallbackToMavenCentralCheckBox, 1)
                .addSeparator(5)
                .addLabeledComponent(new JBLabel("Cache TTL (minutes):"), cacheTtlSpinner, 1, false)
                .addComponentToRightColumn(new JBLabel("How long to cache version information"), 0)
                .addSeparator(5)
                .addComponent(showInlayHintsCheckBox, 1)
                .addSeparator(5)
                .addLabeledComponent(new JBLabel("Trigger mode:"), triggerModeComboBox, 1, false)
                .addComponentToRightColumn(new JBLabel("When to check for dependency updates"), 0)
                .addSeparator(5)
                .addLabeledComponent(new JBLabel("Version filter regex:"), versionFilterRegexField, 1, false)
                .addComponentToRightColumn(new JBLabel("Exclude versions matching this regex (e.g., \".*-SNAPSHOT\" or \".*-(alpha|beta).*\")"), 0)
                .addSeparator(10)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        myMainPanel.setBorder(JBUI.Borders.empty(10));
    }

    @NotNull
    public JPanel getPanel() {
        return myMainPanel;
    }

    public boolean isModified() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(myProject);

        if (!nexusBaseUrlField.getText().equals(settings.getNexusBaseUrl())) return true;
        if (!nexusUsernameField.getText().equals(settings.getNexusUsername())) return true;

        // Use cached password to avoid slow operation on EDT
        String newPassword = new String(nexusPasswordField.getPassword());
        if (!newPassword.equals(cachedPassword)) return true;

        if (fallbackToMavenCentralCheckBox.isSelected() != settings.isFallbackToMavenCentral()) return true;
        if (!cacheTtlSpinner.getValue().equals(settings.getCacheTtlMinutes())) return true;
        if (showGutterIconsCheckBox.isSelected() != settings.isShowGutterIcons()) return true;
        if (showInlayHintsCheckBox.isSelected() != settings.isShowInlayHints()) return true;

        DependencyUpdaterSettings.TriggerMode selectedMode =
                (DependencyUpdaterSettings.TriggerMode) triggerModeComboBox.getSelectedItem();
        if (selectedMode != null && selectedMode != settings.getTriggerMode()) return true;

        if (!versionFilterRegexField.getText().equals(settings.getVersionFilterRegex())) return true;

        return false;
    }

    public void apply() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(myProject);

        settings.setNexusBaseUrl(nexusBaseUrlField.getText());
        settings.setNexusUsername(nexusUsernameField.getText());

        String password = new String(nexusPasswordField.getPassword());
        if (!password.isEmpty()) {
            settings.setNexusPassword(password);
            cachedPassword = password;  // Update cache after saving
        }

        settings.setFallbackToMavenCentral(fallbackToMavenCentralCheckBox.isSelected());
        settings.setCacheTtlMinutes((Integer) cacheTtlSpinner.getValue());
        settings.setShowGutterIcons(showGutterIconsCheckBox.isSelected());
        settings.setShowInlayHints(showInlayHintsCheckBox.isSelected());

        DependencyUpdaterSettings.TriggerMode selectedMode =
                (DependencyUpdaterSettings.TriggerMode) triggerModeComboBox.getSelectedItem();
        if (selectedMode != null) {
            settings.setTriggerMode(selectedMode);
        }

        settings.setVersionFilterRegex(versionFilterRegexField.getText());
    }

    public void reset() {
        DependencyUpdaterSettings settings = DependencyUpdaterSettings.getInstance(myProject);

        nexusBaseUrlField.setText(settings.getNexusBaseUrl());
        nexusUsernameField.setText(settings.getNexusUsername());

        // Cache password to avoid slow operations on EDT during isModified() checks
        String password = settings.getNexusPassword();
        cachedPassword = password != null ? password : "";
        nexusPasswordField.setText(cachedPassword);

        fallbackToMavenCentralCheckBox.setSelected(settings.isFallbackToMavenCentral());
        cacheTtlSpinner.setValue(settings.getCacheTtlMinutes());
        showGutterIconsCheckBox.setSelected(settings.isShowGutterIcons());
        showInlayHintsCheckBox.setSelected(settings.isShowInlayHints());
        triggerModeComboBox.setSelectedItem(settings.getTriggerMode());
        versionFilterRegexField.setText(settings.getVersionFilterRegex());
    }
}
