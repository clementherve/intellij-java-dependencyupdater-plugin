package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow.DependencyRow;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for displaying dependencies in the tool window.
 */
public class DependencyTableModel extends AbstractTableModel {

    private static final int COLUMN_COUNT = 6;
    private static final String NO_VALUE = "-";

    private final List<DependencyRow> rows = new ArrayList<>();

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
            case 0 -> DependencyUpdaterBundle.message("toolWindow.column.dependency");
            case 1 -> DependencyUpdaterBundle.message("toolWindow.column.currentVersion");
            case 2 -> DependencyUpdaterBundle.message("toolWindow.column.latestVersion");
            case 3 -> DependencyUpdaterBundle.message("toolWindow.column.update");
            case 4 -> DependencyUpdaterBundle.message("toolWindow.column.type");
            case 5 -> DependencyUpdaterBundle.message("toolWindow.column.project");
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DependencyRow row = rows.get(rowIndex);
        final Dependency dependency = row.dependency();

        return switch (columnIndex) {
            case 0 -> {
                final String artifact = dependency.artifact();
                final String group = dependency.group();
                if (StringUtils.isBlank(group)) {
                    yield artifact;
                }

                yield String.format("%s:%s", group, artifact);
            }
            case 1 -> dependency.currentVersion();
            case 2 -> row.latestVersion() != null ? row.latestVersion().version() : NO_VALUE;
            case 3 -> row.status() == DependencyRow.Status.NOT_FOUND
                    ? DependencyUpdaterBundle.message("toolWindow.status.notFound")
                    : row.updateType() != null ? row.updateType() : NO_VALUE;
            case 4 -> dependency.group().isEmpty()
                    ? DependencyUpdaterBundle.message("toolWindow.type.plugin")
                    : DependencyUpdaterBundle.message("toolWindow.type.dependency");
            case 5 -> row.projectName();
            default -> "";
        };
    }

    public void addRow(@NotNull Dependency dependency, @Nullable VersionCandidate latestVersion,
                       @NotNull String projectName) {
        rows.add(DependencyRow.from(dependency, latestVersion, projectName));
    }

    /**
     * Replaces every row with the given rows and refreshes the table.
     */
    public void setRows(@NotNull List<DependencyRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public void clear() {
        rows.clear();
        fireTableDataChanged();
    }

    public void refresh() {
        fireTableDataChanged();
    }

    @NotNull
    public DependencyRow getRow(int rowIndex) {
        return rows.get(rowIndex);
    }

    @NotNull
    public List<DependencyRow> getAllRows() {
        return new ArrayList<>(rows);
    }

    public boolean hasMultipleProjects() {
        return rows.stream().map(DependencyRow::projectName).distinct().count() > 1;
    }
}
