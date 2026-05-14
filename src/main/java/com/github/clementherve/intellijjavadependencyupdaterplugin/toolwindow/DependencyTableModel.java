package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.model.DependencyRow;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

import static com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.util.UpdateTypeUtil.determineUpdateType;

/**
 * Table model for displaying dependencies in the tool window.
 */
public class DependencyTableModel extends AbstractTableModel {

    private static final int COLUMN_COUNT = 5;
    private static final String UP_TO_DATE = "Up to date";
    private static final String OUTDATED = "Outdated";

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
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DependencyRow row = rows.get(rowIndex);
        final DependencyInfo dependency = row.dependency();
        return switch (columnIndex) {
            case 0 -> {
                if (StringUtils.isBlank(dependency.group())) {
                    yield dependency.artifact();
                }
                yield dependency.group() + ":" + dependency.artifact();
            }
            case 1 -> dependency.currentVersion();
            case 2 -> row.latestVersion() != null ? row.latestVersion().version() : "-";
            case 3 -> row.updateType() != null ? row.updateType() : "-";
            case 4 -> dependency.group().isEmpty()
                    ? DependencyUpdaterBundle.message("toolWindow.type.plugin")
                    : DependencyUpdaterBundle.message("toolWindow.type.dependency");
            default -> "";
        };
    }

    public void addRow(@NotNull DependencyInfo dependency, @Nullable VersionCandidate latestVersion) {
        String status;
        String updateType = null;

        if (latestVersion == null) {
            status = UP_TO_DATE;
        } else {
            status = OUTDATED;
            updateType = determineUpdateType(dependency.currentVersion(), latestVersion.version());
        }

        rows.add(new DependencyRow(dependency, latestVersion, status, updateType));
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
}
