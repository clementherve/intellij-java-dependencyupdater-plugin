package com.github.clementherve.intellijjavadependencyupdaterplugin.util;

public class SupportedFilesUtil {
    private static final String SUPPORTED_FILE = "build.gradle";

    public static boolean isSupportedFile(String fileName) {
        return SUPPORTED_FILE.equals(fileName);
    }
}
