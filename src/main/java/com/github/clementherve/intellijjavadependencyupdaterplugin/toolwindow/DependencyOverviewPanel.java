package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.model.DependencyRow;
import com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.model.DependencyWithVersion;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.util.FindBuildGradleFilesUtil.findBuildGradleFilesInCurrentProject;

/**
 * Main panel for the dependency overview tool window.
 */
public class DependencyOverviewPanel extends JPanel {

    private static final Logger LOGGER = Logger.getInstance(DependencyOverviewPanel.class);
    private static final String CARD_LOADING = "loading";
    private static final String CARD_TABLE = "table";

    private final Project project;
    private final DependencyTableModel tableModel;
    private final JBTable table;
    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private final JLabel loadingLabel;

    public DependencyOverviewPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.tableModel = new DependencyTableModel();
        this.table = new JBTable(tableModel);
        this.cardLayout = new CardLayout();
        this.contentPanel = new JPanel(cardLayout);
        this.loadingLabel = new JLabel(DependencyUpdaterBundle.message("toolWindow.loading"), SwingConstants.CENTER);

        setupLoadingPanel();
        setupTable();
        setupToolbar();
        setupFileListener();

        add(contentPanel, BorderLayout.CENTER);

        showLoading();
        refreshDependencies();
    }

    private void setupFileListener() {
        // Listen for document saves to auto-refresh when build files change
        project.getMessageBus().connect().subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
            @Override
            public void beforeDocumentSaving(@NotNull Document document) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                if (file != null && SupportedFilesUtil.isSupportedFile(file.getName())) {
                    SwingUtilities.invokeLater(() -> refreshDependencies());
                }
            }
        });
    }


    private void setupLoadingPanel() {
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.add(loadingLabel);
        contentPanel.add(loadingPanel, CARD_LOADING);
    }

    private void setupTable() {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    navigateToSelectedDependency();
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(table);
        contentPanel.add(scrollPane, CARD_TABLE);
    }

    private void showLoading() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_LOADING));
    }

    private void showTable() {
        SwingUtilities.invokeLater(() -> cardLayout.show(contentPanel, CARD_TABLE));
    }

    private void setupToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new RefreshAction());
        actionGroup.add(new UpdateAllAction());
        actionGroup.add(new UpdateSelectedAction());
        actionGroup.add(new PickVersionAction());

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DependencyOverview", actionGroup, true);
        toolbar.setTargetComponent(this);

        add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    private void refreshDependencies() {
        showLoading();
        loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.scanning"));

        ProgressManager.getInstance().run(new Task.Backgroundable(project, DependencyUpdaterBundle.message("toolWindow.scanning"), false) {
            private final List<DependencyWithVersion> results = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                SwingUtilities.invokeLater(() -> loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.findingFiles")));
                indicator.setText(DependencyUpdaterBundle.message("toolWindow.findingFiles"));

                List<VirtualFile> buildFiles = findBuildGradleFilesInCurrentProject(project);

                if (buildFiles.isEmpty()) {
                    SwingUtilities.invokeLater(() -> loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.noFiles")));
                    return;
                }

                SwingUtilities.invokeLater(() -> loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.foundFiles", buildFiles.size())));

                DependencyUpdateService service = DependencyUpdateService.getInstance(project);
                PsiManager psiManager = PsiManager.getInstance(project);

                for (int i = 0; i < buildFiles.size(); i++) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    VirtualFile file = buildFiles.get(i);
                    indicator.setFraction((double) i / buildFiles.size());
                    String statusText = DependencyUpdaterBundle.message("toolWindow.processingFile", file.getName());
                    indicator.setText(statusText);
                    SwingUtilities.invokeLater(() -> loadingLabel.setText(statusText));

                    try {
                        List<DependencyInfo> dependencies = ReadAction.compute(() -> {
                            PsiFile psiFile = psiManager.findFile(file);
                            if (psiFile == null) {
                                return List.of();
                            }

                            DependencyParser parser = DependencyParserFactory.getParser(psiFile);
                            if (parser == null) {
                                return List.of();
                            }

                            return parser.parseDependencies(psiFile);
                        });

                        // Check for updates
                        for (DependencyInfo dependency : dependencies) {
                            if (indicator.isCanceled()) {
                                return;
                            }

                            String checkingText = DependencyUpdaterBundle.message("toolWindow.checkingDependency", dependency.artifact());
                            indicator.setText2(checkingText);
                            SwingUtilities.invokeLater(() -> loadingLabel.setText(checkingText + "..."));
                            VersionCandidate latest = service.checkForUpdate(dependency);
                            results.add(new DependencyWithVersion(dependency, latest));
                        }

                    } catch (Exception e) {
                        LOGGER.warn("Failed to process " + file.getName(), e);
                    }
                }
            }

            @Override
            public void onSuccess() {
                SwingUtilities.invokeLater(() -> {
                    tableModel.clear();
                    for (DependencyWithVersion result : results) {
                        tableModel.addRow(result.dependency(), result.latestVersion());
                    }
                    tableModel.refresh();
                    showTable();

                    if (results.isEmpty()) {
                        loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.noDependencies"));
                        showLoading();
                    }
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOGGER.error("Failed to scan dependencies", error);
                SwingUtilities.invokeLater(() -> {
                    loadingLabel.setText(DependencyUpdaterBundle.message("toolWindow.error", error.getMessage()));
                    showLoading();
                });
            }
        });
    }


    private void navigateToSelectedDependency() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        DependencyRow row = tableModel.getRow(modelRow);

        SmartPsiElementPointer<PsiElement> pointer = row.dependency().psiElementPointer();
        if (pointer == null) {
            return;
        }

        PsiElement element = pointer.getElement();
        if (element == null) {
            return;
        }

        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || containingFile.getVirtualFile() == null) {
            return;
        }

        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile(), element.getTextOffset());

        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }

    private void updateSelectedDependencies(boolean pickVersion) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        List<DependencyRow> rowsToUpdate = new ArrayList<>();
        for (int selectedRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            DependencyRow row = tableModel.getRow(modelRow);
            if (row.latestVersion() != null || pickVersion) {
                rowsToUpdate.add(row);
            }
        }

        if (rowsToUpdate.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.alreadyUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.selectToUpdateTitle"));
            return;
        }

        if (pickVersion && rowsToUpdate.size() == 1) {
            DependencyRow row = rowsToUpdate.getFirst();
            DependencyUpdateService service = DependencyUpdateService.getInstance(project);
            String selectedVersion = VersionPickerDialog.pickVersion(project, row.dependency(), service);

            if (selectedVersion != null) {
                VersionReplacer.applyUpdate(project, row.dependency(), selectedVersion);
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                refreshDependencies();
            }
        } else if (pickVersion) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.singleSelectionOnly"), DependencyUpdaterBundle.message("toolWindow.dialog.pickVersionTitle"));
        } else {
            StringBuilder message = new StringBuilder(DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateQuestion") + "\n\n");
            for (DependencyRow row : rowsToUpdate) {
                message.append(String.format("%s: %s → %s\n", row.dependency().artifact(), row.dependency().currentVersion(), row.latestVersion().version()));
            }

            int result = Messages.showOkCancelDialog(project, message.toString(),
                    DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateTitle", rowsToUpdate.size()),
                    DependencyUpdaterBundle.message("toolWindow.dialog.updateButton"),
                    DependencyUpdaterBundle.message("toolWindow.dialog.cancelButton"),
                    Messages.getQuestionIcon());

            if (result == Messages.OK) {
                List<DependencyRow> sortedRows = sortUpdatedRowsReversed(rowsToUpdate);

                WriteCommandAction.runWriteCommandAction(project, "Update Dependencies", null, () -> {
                    for (DependencyRow row : sortedRows) {
                        VersionReplacer.applyUpdateInWriteAction(project, row.dependency(), row.latestVersion().version());
                    }
                });

                PsiDocumentManager.getInstance(project).commitAllDocuments();
                refreshDependencies();
            }
        }
    }

    // Sort updates in reverse order (bottom to top) to avoid position invalidation
    private List<DependencyRow> sortUpdatedRowsReversed(final List<DependencyRow> rowsToUpdate) {
        return rowsToUpdate.stream().sorted((r1, r2) -> {
            final SmartPsiElementPointer<PsiElement> psiElementPointer1 = r1.dependency().psiElementPointer();
            final SmartPsiElementPointer<PsiElement> psiElementPointer2 = r2.dependency().psiElementPointer();

            int offset1 = psiElementPointer1 != null && psiElementPointer1.getElement() != null ? psiElementPointer1.getElement().getTextOffset() : 0;
            int offset2 = psiElementPointer2 != null && psiElementPointer2.getElement() != null ? psiElementPointer2.getElement().getTextOffset() : 0;

            return Integer.compare(offset2, offset1);
        }).toList();
    }

    private void updateAllDependencies() {
        List<DependencyRow> allRows = tableModel.getAllRows();
        List<DependencyRow> outdatedRows = allRows.stream().filter(row -> row.latestVersion() != null).toList();

        if (outdatedRows.isEmpty()) {
            Messages.showInfoMessage(project, DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDate"), DependencyUpdaterBundle.message("toolWindow.dialog.allUpToDateTitle"));
            return;
        }

        StringBuilder message = new StringBuilder(DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateQuestion") + "\n\n");
        for (DependencyRow row : outdatedRows) {
            message.append(String.format("%s: %s → %s\n", row.dependency().artifact(), row.dependency().currentVersion(), row.latestVersion().version()));
        }

        int result = Messages.showOkCancelDialog(project, message.toString(),
                DependencyUpdaterBundle.message("toolWindow.dialog.confirmUpdateTitle", outdatedRows.size()),
                DependencyUpdaterBundle.message("toolWindow.dialog.updateAllButton"),
                DependencyUpdaterBundle.message("toolWindow.dialog.cancelButton"),
                Messages.getQuestionIcon());

        if (result == Messages.OK) {
            final List<DependencyRow> sortedRows = sortUpdatedRowsReversed(outdatedRows);

            WriteCommandAction.runWriteCommandAction(project, "Update All Dependencies", null, () -> {
                for (DependencyRow row : sortedRows) {
                    VersionReplacer.applyUpdateInWriteAction(project, row.dependency(), row.latestVersion().version());
                }
            });

            PsiDocumentManager.getInstance(project).commitAllDocuments();
            refreshDependencies();
        }
    }

    private class RefreshAction extends AnAction {
        RefreshAction() {
            super(
                    DependencyUpdaterBundle.message("toolWindow.refresh"),
                    DependencyUpdaterBundle.message("toolWindow.action.refresh.description"),
                    AllIcons.Actions.Refresh
            );
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            refreshDependencies();
        }
    }

    private class UpdateAllAction extends AnAction {
        UpdateAllAction() {
            super(
                    DependencyUpdaterBundle.message("toolWindow.updateAll"),
                    DependencyUpdaterBundle.message("toolWindow.action.updateAll.description"),
                    AllIcons.Diff.MagicResolve
            );
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateAllDependencies();
        }
    }

    private class UpdateSelectedAction extends AnAction {
        UpdateSelectedAction() {
            super(
                    DependencyUpdaterBundle.message("toolWindow.updateSelected"),
                    DependencyUpdaterBundle.message("toolWindow.action.updateSelected.description"),
                    AllIcons.Actions.Edit
            );
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateSelectedDependencies(false);
        }
    }

    private class PickVersionAction extends AnAction {
        PickVersionAction() {
            super(
                    DependencyUpdaterBundle.message("toolWindow.pickVersion"),
                    DependencyUpdaterBundle.message("toolWindow.action.pickVersion.description"),
                    AllIcons.Actions.ShortcutFilter
            );
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateSelectedDependencies(true);
        }
    }

}
