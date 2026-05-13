package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for displaying dependencies in the tool window.
 */
public class DependencyTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
        "Dependency",
        "Current Version",
        "Latest Version",
        "Update Type",
    };

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
        return switch (columnIndex) {
            case 0 -> row.dependency.group() + ":" + row.dependency.artifact();
            case 1 -> row.dependency.currentVersion();
            case 2 -> row.latestVersion != null ? row.latestVersion.version() : "N/A";
            case 3 -> row.updateType != null ? row.updateType : "-";
            case 4 -> row.status;
            default -> "";
        };
    }

    public void addRow(@NotNull DependencyInfo dependency, @Nullable VersionCandidate latestVersion) {
        String status;
        String updateType = null;

        if (latestVersion == null) {
            status = "Up to date";
        } else {
            status = "Outdated";
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

    private String determineUpdateType(String currentVersion, String latestVersion) {
        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");

        if (currentParts.length < 1 || latestParts.length < 1) {
            return "Unknown";
        }

        try {
            int currentMajor = Integer.parseInt(currentParts[0]);
            int latestMajor = Integer.parseInt(latestParts[0]);

            if (latestMajor > currentMajor) {
                return "Major";
            }

            if (currentParts.length >= 2 && latestParts.length >= 2) {
                int currentMinor = Integer.parseInt(currentParts[1]);
                int latestMinor = Integer.parseInt(latestParts[1]);

                if (latestMinor > currentMinor) {
                    return "Minor";
                }
            }

            if (currentParts.length >= 3 && latestParts.length >= 3) {
                int currentPatch = Integer.parseInt(currentParts[2].split("-")[0]); // Handle versions like 1.2.3-beta
                int latestPatch = Integer.parseInt(latestParts[2].split("-")[0]);

                if (latestPatch > currentPatch) {
                    return "Patch";
                }
            }

            return "Other";
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }

    /**
     * Represents a row in the dependency table.
     */
    public static class DependencyRow {
        public final DependencyInfo dependency;
        public final VersionCandidate latestVersion;
        public final String status;
        public final String updateType;

        public DependencyRow(@NotNull DependencyInfo dependency,
                           @Nullable VersionCandidate latestVersion,
                           @NotNull String status,
                           @Nullable String updateType) {
            this.dependency = dependency;
            this.latestVersion = latestVersion;
            this.status = status;
            this.updateType = updateType;
        }
    }
}
