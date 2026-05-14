package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.model;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.DependencyInfo;
import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionCandidate;

public record DependencyWithVersion(DependencyInfo dependency, VersionCandidate latestVersion) {
}
