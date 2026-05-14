package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow;

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

    private static final String[] COLUMN_NAMES = {
            "Dependency",
            "Current Version",
            "Latest Version",
            "Update",
            "Type"
    };
    private static final String DEPENDENCY_TYPE_PLUGIN = "plugin";
    private static final String DEPENDENCY_TYPE_DEPENDENCY = "dependency";
    private static final String UP_TO_DATE = "Up to date";
    private static final String OUTDATED = "Outdated";

    private final List<DependencyRow> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
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
            case 4 -> dependency.group().isEmpty() ? DEPENDENCY_TYPE_PLUGIN : DEPENDENCY_TYPE_DEPENDENCY;
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
