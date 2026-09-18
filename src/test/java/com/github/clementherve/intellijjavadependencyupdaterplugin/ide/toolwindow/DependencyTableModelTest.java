package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.dependency.Dependency;
import com.github.clementherve.intellijjavadependencyupdaterplugin.vulnerability.Vulnerability;
import com.github.clementherve.intellijjavadependencyupdaterplugin.vulnerability.VulnerabilityReport;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * Tests for {@link DependencyTableModel}, in particular the "Project" column.
 */
public class DependencyTableModelTest extends BasePlatformTestCase {

    private static Dependency dependency() {
        return new Dependency(
                "com.google.guava", "guava", "31.1-jre", "implementation",
                null, false, null);
    }

    public void test_project_column_is_last_and_labelled() {
        DependencyTableModel model = new DependencyTableModel();

        assertEquals(7, model.getColumnCount());
        assertEquals(DependencyUpdaterBundle.message("toolWindow.column.project"), model.getColumnName(5));
        assertEquals(DependencyUpdaterBundle.message("toolWindow.column.vulnerabilities"), model.getColumnName(6));
    }

    public void test_project_column_value_is_the_folder_name() {
        DependencyTableModel model = new DependencyTableModel();
        model.addRow(dependency(), null, "app");

        // New column carries the folder name...
        assertEquals("app", model.getValueAt(0, 5));

        // ...and the surrounding columns are unaffected (guards against an off-by-one).
        assertEquals("com.google.guava:guava", model.getValueAt(0, 0));
        assertEquals("31.1-jre", model.getValueAt(0, 1));
        assertEquals(DependencyUpdaterBundle.message("toolWindow.type.dependency"), model.getValueAt(0, 4));
    }

    public void test_not_found_row_shows_not_found_in_update_column() {
        DependencyTableModel model = new DependencyTableModel();
        model.setRows(List.of(DependencyRow.notFound(dependency(), "app")));

        assertEquals(DependencyUpdaterBundle.message("toolWindow.status.notFound"), model.getValueAt(0, 3));
        assertEquals("-", model.getValueAt(0, 2));
    }

    public void test_vulnerability_column_defaults_to_not_checked() {
        DependencyTableModel model = new DependencyTableModel();
        model.addRow(dependency(), null, "app");

        assertEquals("-", model.getValueAt(0, 6));
    }

    public void test_vulnerability_column_shows_count_when_vulnerable() {
        DependencyTableModel model = new DependencyTableModel();
        model.setRows(List.of(DependencyRow.from(dependency(), null, "app")
                .withVulnerabilityReport(VulnerabilityReport.vulnerable(List.of(new Vulnerability("GHSA-xxxx-xxxx-xxxx"))))));

        assertEquals(DependencyUpdaterBundle.message("toolWindow.vulnerability.foundSingle"), model.getValueAt(0, 6));
    }

    public void test_vulnerability_column_shows_safe_when_checked_and_clean() {
        DependencyTableModel model = new DependencyTableModel();
        model.setRows(List.of(DependencyRow.from(dependency(), null, "app").withVulnerabilityReport(VulnerabilityReport.SAFE)));

        assertEquals(DependencyUpdaterBundle.message("toolWindow.vulnerability.safe"), model.getValueAt(0, 6));
    }
}
