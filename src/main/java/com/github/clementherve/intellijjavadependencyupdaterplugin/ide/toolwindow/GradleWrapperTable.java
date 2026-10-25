package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The Gradle wrapper table view: owns the table, its model and sorting, and handles in-table
 * interactions (double-click navigation to the wrapper file). Data-mutating commands live in the panel.
 */
class GradleWrapperTable {

    private final Project project;
    private final GradleWrapperTableModel model = new GradleWrapperTableModel();
    private final JBTable table = new JBTable(model);
    private final JComponent component;

    GradleWrapperTable(@NotNull Project project) {
        this.project = project;

        table.setRowSorter(new TableRowSorter<>(model));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer(Object.class, new StatusAwareCellRenderer(model));
        installMouseListener();

        this.component = new JBScrollPane(table);
    }

    @NotNull
    JComponent getComponent() {
        return component;
    }

    void setRows(@NotNull List<GradleWrapperRow> rows) {
        model.setRows(rows);
    }

    @NotNull
    List<GradleWrapperRow> getAllRows() {
        return model.getAllRows();
    }

    boolean hasSelection() {
        return table.getSelectedRowCount() > 0;
    }

    @NotNull
    List<GradleWrapperRow> getSelectedRows() {
        List<GradleWrapperRow> selected = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            selected.add(model.getRow(table.convertRowIndexToModel(viewRow)));
        }
        return selected;
    }

    private void installMouseListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    navigateToSelectedWrapperFile();
                }
            }
        });
    }

    private void navigateToSelectedWrapperFile() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        GradleWrapperRow row = model.getRow(table.convertRowIndexToModel(selectedRow));
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, row.info().file());
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }

    /**
     * Renders every cell of a row in red when its check failed, so an error is visible instead of
     * blending in as up to date.
     */
    private static final class StatusAwareCellRenderer extends DefaultTableCellRenderer {

        private final GradleWrapperTableModel model;

        StatusAwareCellRenderer(@NotNull GradleWrapperTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                        boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                GradleWrapperRow wrapperRow = model.getRow(table.convertRowIndexToModel(row));
                component.setForeground(wrapperRow.status() == GradleWrapperRow.Status.ERROR ? JBColor.RED : table.getForeground());
            }

            return component;
        }
    }
}
