package com.github.clementherve.intellijjavadependencyupdaterplugin.ide.intention;

import com.github.clementherve.intellijjavadependencyupdaterplugin.DependencyUpdaterBundle;
import com.github.clementherve.intellijjavadependencyupdaterplugin.version.VersionChangeKind;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action to update a dependency to the latest patch version.
 */
public class UpdateDependencyToPatchIntention extends AbstractUpdateDependencyIntention {

    @NotNull
    @Override
    protected VersionChangeKind changeKind() {
        return VersionChangeKind.PATCH;
    }

    @NotNull
    @Override
    public String getText() {
        return DependencyUpdaterBundle.message("intention.updateDependencyToPatch");
    }
}
