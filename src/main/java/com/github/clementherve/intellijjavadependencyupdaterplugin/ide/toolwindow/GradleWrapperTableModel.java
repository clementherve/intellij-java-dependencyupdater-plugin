package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for displaying Gradle wrapper files in the tool window - one row per
 * {@code gradle-wrapper.properties} file found in the project.
 */
public class GradleWrapperTableModel extends AbstractTableModel {

    private static final int COLUMN_COUNT = 4;
    private static final String NO_VALUE = "-";

    private final List<GradleWrapperRow> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_COUNT;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> DependencyUpdaterBundle.message("toolWindow.gradleWrapper.column.module");
            case 1 -> DependencyUpdaterBundle.message("toolWindow.column.currentVersion");
            case 2 -> DependencyUpdaterBundle.message("toolWindow.column.latestVersion");
            case 3 -> DependencyUpdaterBundle.message("toolWindow.gradleWrapper.column.status");
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        GradleWrapperRow row = rows.get(rowIndex);

        return switch (columnIndex) {
            case 0 -> row.info().moduleName();
            case 1 -> row.info().currentVersion();
            case 2 -> row.latestVersion() != null ? row.latestVersion().version() : NO_VALUE;
            case 3 -> switch (row.status()) {
                case UP_TO_DATE -> DependencyUpdaterBundle.message("toolWindow.gradleWrapper.status.upToDate");
                case OUTDATED -> DependencyUpdaterBundle.message("toolWindow.gradleWrapper.status.updateAvailable");
                case ERROR -> DependencyUpdaterBundle.message("toolWindow.gradleWrapper.status.error");
            };
            default -> "";
        };
    }

    /**
     * Replaces every row with the given rows and refreshes the table.
     */
    public void setRows(@NotNull List<GradleWrapperRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    @NotNull
    public GradleWrapperRow getRow(int rowIndex) {
        return rows.get(rowIndex);
    }

    @NotNull
    public List<GradleWrapperRow> getAllRows() {
        return new ArrayList<>(rows);
    }
}
