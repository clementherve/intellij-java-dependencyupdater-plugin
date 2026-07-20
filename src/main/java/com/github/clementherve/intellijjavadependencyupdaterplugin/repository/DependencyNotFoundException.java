package com.github.clementherve.intellijjavadependencyupdaterplugin.repository;

import java.io.IOException;

/**
 * Signals that a dependency's coordinates could not be found in the queried repository
 * (e.g. an HTTP 404), as opposed to the artifact existing but having no newer version.
 */
public class DependencyNotFoundException extends IOException {

    public DependencyNotFoundException(String message) {
        super(message);
    }
}
