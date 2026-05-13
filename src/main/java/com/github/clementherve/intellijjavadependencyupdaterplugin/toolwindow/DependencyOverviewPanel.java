package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParser;
import com.github.clementherve.intellijjavadependencyupdaterplugin.psi.DependencyParserFactory;
import com.github.clementherve.intellijjavadependencyupdaterplugin.services.DependencyUpdateService;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.SupportedFilesUtil;
import com.github.clementherve.intellijjavadependencyupdaterplugin.util.VersionReplacer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main panel for the dependency overview tool window.
 */
public class DependencyOverviewPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(DependencyOverviewPanel.class);
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
        this.loadingLabel = new JLabel("Loading dependencies...", SwingConstants.CENTER);

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
        project.getMessageBus().connect().subscribe(
            FileDocumentManagerListener.TOPIC,
            new FileDocumentManagerListener() {
                @Override
                public void beforeDocumentSaving(@NotNull Document document) {
                    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                    if (file != null && SupportedFilesUtil.isSupportedFile(file.getName())) {
                        // Build file was saved, refresh the panel
                        SwingUtilities.invokeLater(() -> refreshDependencies());
                    }
                }
            }
        );
    }


    private void setupLoadingPanel() {
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.add(loadingLabel);
        contentPanel.add(loadingPanel, CARD_LOADING);
    }

    private void setupTable() {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Double-click to navigate
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

        ActionToolbar toolbar = ActionManager.getInstance()
            .createActionToolbar("DependencyOverview", actionGroup, true);
        toolbar.setTargetComponent(this);

        add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    private void refreshDependencies() {
        showLoading();
        loadingLabel.setText("Scanning dependencies...");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning dependencies", false) {
            private final List<DependencyWithVersion> results = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                SwingUtilities.invokeLater(() -> loadingLabel.setText("Finding build.gradle files..."));
                indicator.setText("Finding build.gradle files...");

                // Find all build.gradle files by traversing the project directory
                List<VirtualFile> buildFiles = ReadAction.compute(() -> {
                    List<VirtualFile> files = new ArrayList<>();
                    VirtualFile baseDir = project.getBaseDir();

                    if (baseDir != null) {
                        VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
                            @Override
                            public boolean visitFile(@NotNull VirtualFile file) {
                                // Skip common directories
                                if (file.isDirectory()) {
                                    String name = file.getName();
                                    if (name.startsWith(".") || name.equals("build") ||
                                        name.equals("node_modules") || name.equals("target")) {
                                        return false;
                                    }
                                    return true;
                                }

                                // Check if it's a build.gradle file
                                if (SupportedFilesUtil.isSupportedFile(file.getName())) {
                                    files.add(file);
                                }
                                return true;
                            }
                        });
                    }
                    return files;
                });

                if (buildFiles.isEmpty()) {
                    LOG.info("No build.gradle files found in project");
                    SwingUtilities.invokeLater(() -> loadingLabel.setText("No build.gradle files found"));
                    return;
                }

                LOG.info("Found " + buildFiles.size() + " build.gradle files");
                SwingUtilities.invokeLater(() -> loadingLabel.setText("Found " + buildFiles.size() + " build file(s), scanning dependencies..."));

                DependencyUpdateService service = DependencyUpdateService.getInstance(project);
                PsiManager psiManager = PsiManager.getInstance(project);

                // Parse dependencies from each file
                for (int i = 0; i < buildFiles.size(); i++) {
                    if (indicator.isCanceled()) {
                        return;
                    }

                    VirtualFile file = buildFiles.get(i);
                    indicator.setFraction((double) i / buildFiles.size());
                    String statusText = "Processing " + file.getName() + "...";
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

                        LOG.info("Found " + dependencies.size() + " dependencies in " + file.getName());

                        // Check for updates
                        for (DependencyInfo dependency : dependencies) {
                            if (indicator.isCanceled()) {
                                return;
                            }

                            String checkingText = "Checking " + dependency.artifact();
                            indicator.setText2(checkingText);
                            SwingUtilities.invokeLater(() -> loadingLabel.setText(checkingText + "..."));
                            // Use checkForUpdate() instead of checkForUpdateFromCache() to fetch fresh data
                            VersionCandidate latest = service.checkForUpdate(dependency);
                            results.add(new DependencyWithVersion(dependency, latest));
                        }

                    } catch (Exception e) {
                        LOG.warn("Failed to process " + file.getName(), e);
                    }
                }
            }

            @Override
            public void onSuccess() {
                LOG.info("Scan completed. Found " + results.size() + " dependencies total");
                SwingUtilities.invokeLater(() -> {
                    tableModel.clear();
                    for (DependencyWithVersion result : results) {
                        tableModel.addRow(result.dependency, result.latestVersion);
                    }
                    tableModel.refresh();
                    showTable();

                    if (results.isEmpty()) {
                        LOG.info("No dependencies to display");
                        loadingLabel.setText("No dependencies found");
                        showLoading();
                    } else {
                        LOG.info("Displaying " + results.size() + " dependencies in table");
                    }
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("Failed to scan dependencies", error);
                SwingUtilities.invokeLater(() -> {
                    loadingLabel.setText("Error: " + error.getMessage());
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
        DependencyTableModel.DependencyRow row = tableModel.getRow(modelRow);

        SmartPsiElementPointer<PsiElement> pointer = row.dependency.psiElementPointer();
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

        OpenFileDescriptor descriptor = new OpenFileDescriptor(
            project,
            containingFile.getVirtualFile(),
            element.getTextOffset()
        );

        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }

    private void updateSelectedDependencies(boolean pickVersion) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            Messages.showInfoMessage(project, "Please select dependencies to update.", "Update Dependencies");
            return;
        }

        List<DependencyTableModel.DependencyRow> rowsToUpdate = new ArrayList<>();
        for (int selectedRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            DependencyTableModel.DependencyRow row = tableModel.getRow(modelRow);
            if (row.latestVersion != null || pickVersion) {
                rowsToUpdate.add(row);
            }
        }

        if (rowsToUpdate.isEmpty()) {
            Messages.showInfoMessage(project, "Selected dependencies are already up to date.", "Update Dependencies");
            return;
        }

        if (pickVersion && rowsToUpdate.size() == 1) {
            // Pick specific version for single selection
            DependencyTableModel.DependencyRow row = rowsToUpdate.getFirst();
            DependencyUpdateService service = DependencyUpdateService.getInstance(project);
            String selectedVersion = VersionPickerDialog.pickVersion(project, row.dependency, service);

            if (selectedVersion != null) {
                VersionReplacer.applyUpdate(project, row.dependency, selectedVersion);
                // Commit document changes to PSI before refreshing
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                refreshDependencies();
            }
        } else if (pickVersion) {
            Messages.showInfoMessage(project, "Version picker is only available for single selection.", "Pick Version");
        } else {
            // Update to latest version
            StringBuilder message = new StringBuilder("Update the following dependencies?\n\n");
            for (DependencyTableModel.DependencyRow row : rowsToUpdate) {
                message.append(String.format("%s: %s → %s\n",
                    row.dependency.artifact(),
                    row.dependency.currentVersion(),
                    row.latestVersion.version()));
            }

            int result = Messages.showOkCancelDialog(
                project,
                message.toString(),
                "Update " + rowsToUpdate.size() + " Dependencies",
                "Update",
                "Cancel",
                Messages.getQuestionIcon()
            );

            if (result == Messages.OK) {
                for (DependencyTableModel.DependencyRow row : rowsToUpdate) {
                    VersionReplacer.applyUpdate(project, row.dependency, row.latestVersion.version());
                }
                // Commit document changes to PSI before refreshing
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                refreshDependencies();
            }
        }
    }

    private void updateAllDependencies() {
        List<DependencyTableModel.DependencyRow> allRows = tableModel.getAllRows();
        List<DependencyTableModel.DependencyRow> outdatedRows = allRows.stream()
            .filter(row -> row.latestVersion != null)
            .toList();

        if (outdatedRows.isEmpty()) {
            Messages.showInfoMessage(project, "All dependencies are up to date!", "Update All Dependencies");
            return;
        }

        StringBuilder message = new StringBuilder("Update the following dependencies?\n\n");
        for (DependencyTableModel.DependencyRow row : outdatedRows) {
            message.append(String.format("%s: %s → %s\n",
                row.dependency.artifact(),
                row.dependency.currentVersion(),
                row.latestVersion.version()));
        }

        int result = Messages.showOkCancelDialog(
            project,
            message.toString(),
            "Update " + outdatedRows.size() + " Dependencies",
            "Update All",
            "Cancel",
            Messages.getQuestionIcon()
        );

        if (result == Messages.OK) {
            for (DependencyTableModel.DependencyRow row : outdatedRows) {
                VersionReplacer.applyUpdate(project, row.dependency, row.latestVersion.version());
            }
            // Commit document changes to PSI before refreshing
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            refreshDependencies();
        }
    }

    // Action classes
    private class RefreshAction extends AnAction {
        RefreshAction() {
            super("Refresh", "Refresh dependency list", AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            refreshDependencies();
        }
    }

    private class UpdateAllAction extends AnAction {
        UpdateAllAction() {
            super("Update All", "Update all outdated dependencies", AllIcons.Actions.Selectall);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateAllDependencies();
        }
    }

    private class UpdateSelectedAction extends AnAction {
        UpdateSelectedAction() {
            super("Update Selected", "Update selected dependencies", AllIcons.Actions.Upload);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateSelectedDependencies(false);
        }
    }

    private class PickVersionAction extends AnAction {
        PickVersionAction() {
            super("Pick Version", "Pick a specific version for selected dependency", AllIcons.Actions.Find);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            updateSelectedDependencies(true);
        }
    }

    private record DependencyWithVersion(DependencyInfo dependency, VersionCandidate latestVersion) {
    }
}
