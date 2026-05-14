package com.github.clementherve.intellijjavadependencyupdaterplugin.toolwindow.util;

public class UpdateTypeUtil {
    public static String determineUpdateType(String currentVersion, String latestVersion) {
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

}
