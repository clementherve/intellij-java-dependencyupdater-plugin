package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Dialog for selecting a specific version from all available versions.
 */
public class VersionPickerDialog extends DialogWrapper {

    private final Project project;
    private final Dependency dependency;
    private final DependencyUpdateService service;
    private List<VersionCandidate> availableVersions;
    private JBList<String> versionList;
    private RefreshAction refreshAction;
    private String selectedVersion;

    public VersionPickerDialog(@NotNull Project project, @NotNull Dependency dependency,
                               @NotNull List<VersionCandidate> availableVersions,
                               @NotNull DependencyUpdateService service) {
        super(project);
        this.project = project;
        this.dependency = dependency;
        this.availableVersions = availableVersions;
        this.service = service;

        setTitle("Select Version for " + dependency.artifact());
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        String[] versions = availableVersions.stream().map(VersionCandidate::version).toArray(String[]::new);

        versionList = new JBList<>(versions);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        if (versions.length > 0) {
            versionList.setSelectedIndex(0);
        }

        versionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    doOKAction();
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(versionList);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(
                        new JBLabel("Dependency:"),
                        new JBLabel(dependency.group() + ":" + dependency.artifact()), 1, false)
                .addLabeledComponent(
                        new JBLabel("Current version:"),
                        new JBLabel(dependency.currentVersion()), 1, false)
                .addSeparator(10)
                .addLabeledComponent(
                        new JBLabel("Available versions:"), scrollPane, 1, true)
                .addComponentToRightColumn(
                        new JBLabel("(Latest version at the top)"), 0)
                .getPanel();

        panel.setBorder(JBUI.Borders.empty(10));

        return panel;
    }

    @NotNull
    @Override
    protected Action[] createLeftSideActions() {
        refreshAction = new RefreshAction();
        return new Action[]{refreshAction};
    }

    private void refreshVersions() {
        refreshAction.setEnabled(false);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Refreshing " + dependency.artifact(), false) {
            private List<VersionCandidate> refreshed;
            private Exception failure;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    service.forceCheckForUpdate(dependency);
                    refreshed = service.getAllCandidatesFromCache(dependency);
                } catch (Exception exception) {
                    failure = exception;
                }
            }

            @Override
            public void onSuccess() {
                refreshAction.setEnabled(true);

                if (failure != null) {
                    Messages.showErrorDialog(
                            project,
                            "Failed to refresh " + dependency.artifact() + ": " + failure.getMessage(),
                            "Refresh Dependency"
                    );
                    return;
                }

                updateVersionList(refreshed);
            }
        });
    }

    private void updateVersionList(@NotNull List<VersionCandidate> versions) {
        this.availableVersions = versions;
        String[] versionStrings = versions.stream().map(VersionCandidate::version).toArray(String[]::new);
        versionList.setListData(versionStrings);
        if (versionStrings.length > 0) {
            versionList.setSelectedIndex(0);
        }
    }

    private final class RefreshAction extends AbstractAction {
        RefreshAction() {
            super("Refresh");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            refreshVersions();
        }
    }

    @Override
    protected void doOKAction() {
        String selected = versionList.getSelectedValue();
        if (selected != null) {
            selectedVersion = selected;
        }
        super.doOKAction();
    }

    @Nullable
    public String getSelectedVersion() {
        return selectedVersion;
    }

    @Nullable
    public static String pickVersion(@NotNull Project project, @NotNull Dependency dependency, @NotNull DependencyUpdateService service) {
        List<VersionCandidate> versions = service.getAllCandidatesFromCache(dependency);

        if (versions.isEmpty()) {
            service.scheduleCacheWarmup(dependency);
            return null;
        }

        VersionPickerDialog dialog = new VersionPickerDialog(project, dependency, versions, service);
        if (dialog.showAndGet()) {
            return dialog.getSelectedVersion();
        }

        return null;
    }
}
