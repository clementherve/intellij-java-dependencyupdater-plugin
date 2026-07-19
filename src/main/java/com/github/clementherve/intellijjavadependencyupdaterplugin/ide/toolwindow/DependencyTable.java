package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow.DependencyRow;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The dependency table view: owns the table, its model, sorting, the search/filter field and
 * the scroll component, and handles in-table interactions (double-click navigation to the
 * declaration and right-click version picking). Data-mutating commands live in the panel.
 */
class DependencyTable {

    /**
     * Receives in-table interactions that require coordination outside the view.
     */
    interface Listener {
        void onPickVersion(@NotNull DependencyRow row);
    }

    private static final int PROJECT_COLUMN_INDEX = 5;

    private final Project project;
    private final Listener listener;
    private final DependencyTableModel model = new DependencyTableModel();
    private final JBTable table = new JBTable(model);
    private final TableRowSorter<DependencyTableModel> sorter;
    private final JComponent component;

    DependencyTable(@NotNull Project project, @NotNull Listener listener) {
        this.project = project;
        this.listener = listener;

        this.sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer(Object.class, new StatusAwareCellRenderer(model));
        installMouseListener();

        this.component = buildComponent();
    }

    @NotNull
    JComponent getComponent() {
        return component;
    }

    void setRows(@NotNull List<DependencyRow> rows) {
        model.setRows(rows);
        updateProjectColumnVisibility();
    }

    @NotNull
    List<DependencyRow> getAllRows() {
        return model.getAllRows();
    }

    boolean hasSelection() {
        return table.getSelectedRowCount() > 0;
    }

    @NotNull
    List<DependencyRow> getSelectedRows() {
        List<DependencyRow> selected = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            selected.add(model.getRow(table.convertRowIndexToModel(viewRow)));
        }
        return selected;
    }

    @NotNull
    private JComponent buildComponent() {
        SearchTextField searchField = new SearchTextField();
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilter(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilter(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilter(searchField.getText()); }
        });

        JBScrollPane scrollPane = new JBScrollPane(table);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void applyFilter(String text) {
        if (text == null || text.isBlank()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 0));
        }
    }

    private void installMouseListener() {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    navigateToSelectedDependency();
                }

                if (event.getClickCount() == 1 && event.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                    int clickedRow = table.rowAtPoint(event.getPoint());
                    if (clickedRow < 0) {
                        return;
                    }
                    table.setRowSelectionInterval(clickedRow, clickedRow);
                    DependencyRow row = model.getRow(table.convertRowIndexToModel(clickedRow));
                    listener.onPickVersion(row);
                }
            }
        });
    }

    private void navigateToSelectedDependency() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        DependencyRow row = model.getRow(table.convertRowIndexToModel(selectedRow));

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

    private void updateProjectColumnVisibility() {
        TableColumn projectColumn = table.getColumnModel().getColumn(PROJECT_COLUMN_INDEX);
        if (model.hasMultipleProjects()) {
            projectColumn.setMinWidth(50);
            projectColumn.setMaxWidth(Integer.MAX_VALUE);
            projectColumn.setPreferredWidth(120);
        } else {
            projectColumn.setMinWidth(0);
            projectColumn.setMaxWidth(0);
            projectColumn.setPreferredWidth(0);
        }
    }

    /**
     * Renders every cell of a row in red when its dependency could not be found in the
     * repository, so a lookup failure is visible instead of blending in as "up to date".
     */
    private static final class StatusAwareCellRenderer extends DefaultTableCellRenderer {

        private final DependencyTableModel model;

        StatusAwareCellRenderer(@NotNull DependencyTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                        boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                DependencyRow dependencyRow = model.getRow(table.convertRowIndexToModel(row));
                component.setForeground(dependencyRow.status() == DependencyRow.Status.NOT_FOUND
                        ? JBColor.RED
                        : table.getForeground());
            }

            return component;
        }
    }
}
