package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeKind;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action to update a dependency to the latest major version.
 */
public class UpdateDependencyToMajorIntention extends AbstractUpdateDependencyIntention {

    @NotNull
    @Override
    protected VersionChangeKind changeKind() {
        return VersionChangeKind.MAJOR;
    }

    @NotNull
    @Override
    public String getText() {
        return DependencyUpdaterBundle.message("intention.updateDependencyToMajor");
    }
}
