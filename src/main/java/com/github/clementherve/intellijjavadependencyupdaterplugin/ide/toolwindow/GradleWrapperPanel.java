package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.gradlewrapper.GradleWrapperCommandRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool window tab that lists every Gradle wrapper file found in the project (a project may have
 * several - e.g. separate {@code app/} and {@code test/} modules each with their own wrapper),
 * checks each against the latest stable Gradle release, and lets the user update them.
 */
public class GradleWrapperPanel extends JPanel {

    private static final Logger LOGGER = Logger.getInstance(GradleWrapperPanel.class);
    private static final String CARD_LOADING = "loading";
    private static final String CARD_TABLE = "table";

    private final Project project;
    private final GradleWrapperTable wrapperTable;
    private final GradleWrapperScanController scanController;
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JLabel loadingLabel;

    public GradleWrapperPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.wrapperTable = new GradleWrapperTable(project);
        this.scanController = new GradleWrapperScanController(project, new ScanListener());
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        this.loadingLabel = new JLabel(DependencyUpdaterBundle.message("toolWindow.gradleWrapper.checking"), SwingConstants.CENTER);

        setupLoadingPanel();
        contentPanel.add(wrapperTable.getComponent(), CARD_TABLE);
        setupToolbar();

        add(contentPanel, BorderLayout.CENTER);

        showLoading();
        refresh(false);
    }

    private void setupLoadingPanel() {
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.add(loadingLabel);
        contentPanel.add(loadingPanel, CARD_LOADING);
    }

    private void setupToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new RefreshAction());
        actionGroup.add(new UpdateAllAction());
        actionGroup.add(new UpdateSelectedAction());

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("GradleWrapperOverview", actionGroup, true);
        toolbar.setTargetComponent(this);

        add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    private void refresh(boolean forceRefresh) {
        showLoading();
        loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.gradleWrapper.checking"));
        scanController.refresh(forceRefresh);
    }

    private void showLoading() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_LOADING));
    }

    private void showTable() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_TABLE));
    }

    private class ScanListener implements GradleWrapperScanController.Listener {
        @Override
        public void onStatus(@NotNull String message) {
            SwingUtilities.invokeLater(() -> loadingLabel.setText(message));
        }

        @Override
        public void onScanned(@NotNull List<GradleWrapperRow> rows) {
            SwingUtilities.invokeLater(() -> {
                if (rows.isEmpty()) {
                    loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.gradleWrapper.notFound"));
                    showLoading();
                    return;
                }
                wrapperTable.setRows(rows);
                showTable();
            });
        }

        @Override
        public void onError(@NotNull Throwable error) {
            LOGGER.warn("Failed to scan Gradle wrapper versions", error);
            SwingUtilities.invokeLater(() -> {
                loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.gradleWrapper.error", error.getMessage()));
                showLoading();
            });
        }
    }

    private void updateSelectedWrappers() {
        if (!wrapperTable.hasSelection()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        List<GradleWrapperRow> rowsToUpdate = wrapperTable.getSelectedRows().stream()
                .filter(row -> row.latestVersion() != null)
                .toList();

        if (rowsToUpdate.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.alreadyUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        confirmAndApply(rowsToUpdate, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.dialog.confirmTitle"), DependencyUpdaterBundle.message("toolWindow.dialog.updateButton"));
    }

    private void updateAllWrappers() {
        List<GradleWrapperRow> outdatedRows = wrapperTable.getAllRows().stream()
                .filter(row -> row.latestVersion() != null)
                .toList();

        if (outdatedRows.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDateTitle"));
            return;
        }

        confirmAndApply(outdatedRows, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.dialog.confirmTitle"), DependencyUpdaterBundle.message("toolWindow.dialog.updateAllButton"));
    }

    private void confirmAndApply(@NotNull List<GradleWrapperRow> rowsToUpdate, @NotNull String title, @NotNull String confirmButton) {
        StringBuilder message = new StringBuilder(DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateQuestion") + "\n\n");
        for (GradleWrapperRow row : rowsToUpdate) {
            message.append(String.format("%s: %s → %s\n", row.info().moduleName(), row.info().currentVersion(), row.latestVersion().version()));
        }

        int result = Messages.showOkCancelDialog(project, message.toString(), title, confirmButton, DependencyUpdaterBundle.message("toolWindow.dialog.cancelButton"), Messages.getQuestionIcon());

        if (result != Messages.OK) {
            return;
        }

        runUpdates(rowsToUpdate);
    }

    // Runs `./gradlew wrapper --gradle-version=...` for each row - a real process per module, so
    // this happens as its own cancellable background task rather than a write action.
    private void runUpdates(@NotNull List<GradleWrapperRow> rowsToUpdate) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.updating"), true) {
            private final List<String> failedModules = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                for (GradleWrapperRow row : rowsToUpdate) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    indicator.setText(DependencyUpdaterBundle.message("toolWindow.gradleWrapper.updatingModule", row.info().moduleName()));
                    try {
                        GradleWrapperCommandRunner.run(row.info(), row.latestVersion().version(), indicator);
                    } catch (IOException exception) {
                        LOGGER.warn("Failed to update Gradle wrapper for " + row.info().moduleName(), exception);
                        failedModules.add(row.info().moduleName());
                    }
                }
            }

            @Override
            public void onFinished() {
                if (!failedModules.isEmpty()) {
                    Messages.showErrorDialog(project, DependencyUpdaterBundle.message("toolWindow.gradleWrapper.updateFailed", String.join(", ", failedModules)), DependencyUpdaterBundle.message("toolWindow.gradleWrapper.dialog.confirmTitle"));
                }
                refresh(false);
            }
        });
    }

    private class RefreshAction extends AnAction {
        RefreshAction() {
            super(DependencyUpdaterBundle.message("toolWindow.refresh"), DependencyUpdaterBundle.message("toolWindow.action.refresh.description"), AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            refresh(true);
        }
    }

    private class UpdateAllAction extends AnAction {
        UpdateAllAction() {
            super(DependencyUpdaterBundle.message("toolWindow.updateAll"), DependencyUpdaterBundle.message("toolWindow.action.updateAll.description"), AllIcons.Diff.MagicResolve);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            updateAllWrappers();
        }
    }

    private class UpdateSelectedAction extends AnAction {
        UpdateSelectedAction() {
            super(DependencyUpdaterBundle.message("toolWindow.updateSelected"), DependencyUpdaterBundle.message("toolWindow.action.updateSelected.description"), AllIcons.Actions.Edit);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            updateSelectedWrappers();
        }
    }
}
