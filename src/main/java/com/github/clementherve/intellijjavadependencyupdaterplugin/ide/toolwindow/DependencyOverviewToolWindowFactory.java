package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.toolwindow;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the Dependency Overview tool window.
 */
public class DependencyOverviewToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        DependencyOverviewPanel dependenciesPanel = new DependencyOverviewPanel(project);
        Content dependenciesContent = contentFactory.createContent(dependenciesPanel, DependencyUpdaterBundle.message("toolWindow.tab.dependencies"), false);
        toolWindow.getContentManager().addContent(dependenciesContent);

        GradleWrapperPanel gradleWrapperPanel = new GradleWrapperPanel(project);
        Content gradleWrapperContent = contentFactory.createContent(gradleWrapperPanel, DependencyUpdaterBundle.message("toolWindow.tab.gradleWrapper"), false);
        toolWindow.getContentManager().addContent(gradleWrapperContent);
    }
}
