package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeKind;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action to update a dependency to the latest minor version.
 */
public class UpdateDependencyToMinorIntention extends AbstractUpdateDependencyIntention {

    @NotNull
    @Override
    protected VersionChangeKind changeKind() {
        return VersionChangeKind.MINOR;
    }

    @NotNull
    @Override
    public String getText() {
        return DependencyUpdaterBundle.message("intention.updateDependencyToMinor");
    }
}
