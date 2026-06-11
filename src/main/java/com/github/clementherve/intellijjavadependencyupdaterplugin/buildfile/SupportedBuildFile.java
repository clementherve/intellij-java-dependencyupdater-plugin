package com.github.clementherve.intellijjavadependencyupdaterplugin.buildfile;

public class SupportedBuildFile {
    private static final String SUPPORTED_FILE = "build.gradle";

    public static boolean isSupportedFile(String fileName) {
        return SUPPORTED_FILE.equals(fileName);
    }
}
