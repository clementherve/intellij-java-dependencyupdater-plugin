package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.service.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.settings.DependencyUpdaterSettings;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow.DependencyRow;
import com.github.clementherve.intellijjavadependencyupdaterplugin.update.DependencyVersionWriter;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile.SupportedBuildFile;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Main panel for the dependency overview tool window. Wires together the {@link DependencyTable}
 * view, the {@link DependencyScanController} background scan and the loading/table card switch,
 * and hosts the toolbar update commands.
 */
public class DependencyOverviewPanel extends JPanel {

    private static final Logger LOGGER = Logger.getInstance(DependencyOverviewPanel.class);
    private static final String CARD_LOADING = "loading";
    private static final String CARD_TABLE = "table";

    private final Project project;
    private final DependencyTable dependencyTable;
    private final DependencyScanController scanController;
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JLabel loadingLabel;

    public DependencyOverviewPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.dependencyTable = new DependencyTable(project, new DependencyTable.Listener() {
            @Override
            public void onPickVersion(@NotNull DependencyRow row) {
                pickAndApplyVersion(row);
            }

            @Override
            public void onIgnoreVersion(@NotNull DependencyRow row) {
                ignoreVersion(row);
            }
        });
        this.scanController = new DependencyScanController(project, new ScanListener());
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        this.loadingLabel = new JLabel(DependencyUpdaterBundle.message("toolWindow.loading"), SwingConstants.CENTER);

        setupLoadingPanel();
        contentPanel.add(dependencyTable.getComponent(), CARD_TABLE);
        setupToolbar();
        setupFileListener();

        add(contentPanel, BorderLayout.CENTER);

        showLoading();
        refreshDependencies(false);
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

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DependencyOverview", actionGroup, true);
        toolbar.setTargetComponent(this);

        add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    private void setupFileListener() {
        // Listen for document saves to auto-refresh when build files change
        project.getMessageBus().connect().subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
            @Override
            public void beforeDocumentSaving(@NotNull Document document) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                if (file != null && SupportedBuildFile.isSupportedFile(file.getName())) {
                    SwingUtilities.invokeLater(() -> refreshDependencies(false));
                }
            }
        });
    }

    private void refreshDependencies(boolean forceRefresh) {
        showLoading();
        loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.scanning"));
        scanController.refresh(forceRefresh);
    }

    private void showLoading() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_LOADING));
    }

    private void showTable() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_TABLE));
    }

    /**
     * Receives scan progress and results and drives the loading/table card switch.
     */
    private class ScanListener implements DependencyScanController.Listener {
        @Override
        public void onStatus(@NotNull String message) {
            SwingUtilities.invokeLater(() -> loadingLabel.setText(message));
        }

        @Override
        public void onScanned(@NotNull List<DependencyRow> rows) {
            SwingUtilities.invokeLater(() -> {
                if (rows.isEmpty()) {
                    loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.noDependencies"));
                    showLoading();
                    return;
                }
                dependencyTable.setRows(rows);
                showTable();
            });
        }

        @Override
        public void onError(@NotNull Throwable error) {
            LOGGER.error("Failed to scan dependencies", error);
            SwingUtilities.invokeLater(() -> {
                loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.error", error.getMessage()));
                showLoading();
            });
        }
    }

    private void pickAndApplyVersion(@NotNull DependencyRow row) {
        DependencyUpdateService service = DependencyUpdateService.getInstance(project);
        String selectedVersion = VersionPickerDialog.pickVersion(project, row.dependency(), service);
        if (selectedVersion != null) {
            DependencyVersionWriter.applyUpdate(project, row.dependency(), selectedVersion);
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            refreshDependencies(false);
        }
    }

    private void ignoreVersion(@NotNull DependencyRow row) {
        VersionCandidate latestVersion = row.latestVersion();
        if (latestVersion == null) {
            return;
        }

        DependencyUpdaterSettings.getInstance().ignoreVersion(row.dependency().group(), row.dependency().artifact(), latestVersion.version());
        refreshDependencies(false);
    }

    private void updateSelectedDependencies(boolean pickVersion) {
        if (!dependencyTable.hasSelection()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        List<DependencyRow> rowsToUpdate = new ArrayList<>();
        for (DependencyRow row : dependencyTable.getSelectedRows()) {
            if (row.latestVersion() != null || pickVersion) {
                rowsToUpdate.add(row);
            }
        }

        if (rowsToUpdate.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.alreadyUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        if (pickVersion && rowsToUpdate.size() == 1) {
            pickAndApplyVersion(rowsToUpdate.getFirst());
        } else if (pickVersion) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.singleSelectionOnly"), DependencyUpdaterBundle.message("toolWindow.dialog.pickVersionTitle"));
        } else {
            confirmAndApply(rowsToUpdate, DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateTitle", rowsToUpdate.size()), DependencyUpdaterBundle.message("toolWindow.dialog.updateButton"));
        }
    }

    private void updateAllDependencies() {
        List<DependencyRow> outdatedRows = dependencyTable.getAllRows().stream()
                .filter(row -> row.latestVersion() != null)
                .toList();

        if (outdatedRows.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDateTitle"));
            return;
        }

        confirmAndApply(outdatedRows, DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateTitle", outdatedRows.size()), DependencyUpdaterBundle.message("toolWindow.dialog.updateAllButton"));
    }

    private void confirmAndApply(@NotNull List<DependencyRow> rowsToUpdate, @NotNull String title, @NotNull String confirmButton) {
        StringBuilder message = new StringBuilder(DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateQuestion") + "\n\n");
        for (DependencyRow row : rowsToUpdate) {
            message.append(String.format("%s: %s → %s\n", row.dependency().artifact(), row.dependency().currentVersion(), row.latestVersion().version()));
        }

        int result = Messages.showOkCancelDialog(project, message.toString(), title, confirmButton, DependencyUpdaterBundle.message("toolWindow.dialog.cancelButton"), Messages.getQuestionIcon());

        if (result != Messages.OK) {
            return;
        }

        List<DependencyRow> sortedRows = sortInReverseDocumentOrder(rowsToUpdate);

        WriteCommandAction.runWriteCommandAction(project, "Update Dependencies", null, () -> {
            for (DependencyRow row : sortedRows) {
                DependencyVersionWriter.applyUpdateInWriteAction(project, row.dependency(), row.latestVersion().version());
            }
        });

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        refreshDependencies(false);
    }

    // Sort updates from bottom to top of the document to avoid invalidating offsets as we edit.
    private List<DependencyRow> sortInReverseDocumentOrder(@NotNull List<DependencyRow> rowsToUpdate) {
        return rowsToUpdate.stream().sorted((first, second) -> {
            int firstOffset = offsetOf(first);
            int secondOffset = offsetOf(second);
            return Integer.compare(secondOffset, firstOffset);
        }).toList();
    }

    private int offsetOf(@NotNull DependencyRow row) {
        SmartPsiElementPointer<PsiElement> pointer = row.dependency().psiElementPointer();
        PsiElement element = pointer != null ? pointer.getElement() : null;
        return element != null ? element.getTextOffset() : 0;
    }

    private class RefreshAction extends AnAction {
        RefreshAction() {
            super(DependencyUpdaterBundle.message("toolWindow.refresh"), DependencyUpdaterBundle.message("toolWindow.action.refresh.description"), AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            refreshDependencies(true);
        }
    }

    private class UpdateAllAction extends AnAction {
        UpdateAllAction() {
            super(DependencyUpdaterBundle.message("toolWindow.updateAll"), DependencyUpdaterBundle.message("toolWindow.action.updateAll.description"), AllIcons.Diff.MagicResolve);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            updateAllDependencies();
        }
    }

    private class UpdateSelectedAction extends AnAction {
        UpdateSelectedAction() {
            super(DependencyUpdaterBundle.message("toolWindow.updateSelected"), DependencyUpdaterBundle.message("toolWindow.action.updateSelected.description"), AllIcons.Actions.Edit);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            updateSelectedDependencies(false);
        }
    }
}
