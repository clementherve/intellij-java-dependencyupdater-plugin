package com.github.clementherve.intellijjavadependencyupdaterplugin.settings;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent settings for the Dependency Updater plugin.
 */
@State(
        name = "DependencyUpdaterSettings",
        storages = @Storage("dependencyUpdater.xml")
)
@Service(Service.Level.PROJECT)
public final class DependencyUpdaterSettings implements PersistentStateComponent<DependencyUpdaterSettings.State> {

    private final State state = new State();
    private final Project myProject;

    public DependencyUpdaterSettings(Project project) {
        this.myProject = project;
    }

    public static DependencyUpdaterSettings getInstance(@NotNull Project project) {
        return project.getService(DependencyUpdaterSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public String getNexusBaseUrl() {
        return state.nexusBaseUrl;
    }

    public void setNexusBaseUrl(String nexusBaseUrl) {
        state.nexusBaseUrl = nexusBaseUrl;
    }

    public String getNexusUsername() {
        return state.nexusUsername;
    }

    public void setNexusUsername(String nexusUsername) {
        state.nexusUsername = nexusUsername;
    }

    @Nullable
    public String getNexusPassword() {
        CredentialAttributes attributes = createCredentialAttributes();
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    public void setNexusPassword(@Nullable String password) {
        CredentialAttributes attributes = createCredentialAttributes();
        Credentials credentials = password != null ? new Credentials(state.nexusUsername, password) : null;
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    private CredentialAttributes createCredentialAttributes() {
        String serviceName = "DependencyUpdater-" + myProject.getName();
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName("DependencyUpdater", serviceName)
        );
    }

    public boolean isFallbackToMavenCentral() {
        return state.fallbackToMavenCentral;
    }

    public void setFallbackToMavenCentral(boolean fallbackToMavenCentral) {
        state.fallbackToMavenCentral = fallbackToMavenCentral;
    }

    public int getCacheTtlMinutes() {
        return state.cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        state.cacheTtlMinutes = cacheTtlMinutes;
    }

    @NotNull
    public List<VersionPolicy> getVersionPolicies() {
        if (state.versionPolicies == null || state.versionPolicies.isEmpty()) {
            return Collections.singletonList(VersionPolicy.createDefaultStablePolicy());
        }
        return new ArrayList<>(state.versionPolicies);
    }

    public void setVersionPolicies(@NotNull List<VersionPolicy> versionPolicies) {
        state.versionPolicies = new ArrayList<>(versionPolicies);
    }

    public boolean isShowInlayHints() {
        return state.showInlayHints;
    }

    public void setShowInlayHints(boolean showInlayHints) {
        state.showInlayHints = showInlayHints;
    }

    @NotNull
    public TriggerMode getTriggerMode() {
        return state.triggerMode;
    }

    public void setTriggerMode(@NotNull TriggerMode triggerMode) {
        state.triggerMode = triggerMode;
    }

    @NotNull
    public String getVersionFilterRegex() {
        return state.versionFilterRegex != null ? state.versionFilterRegex : "";
    }

    public void setVersionFilterRegex(@NotNull String versionFilterRegex) {
        state.versionFilterRegex = versionFilterRegex;
    }

    @NotNull
    public String getNexusDependencyRegex() {
        return state.nexusDependencyRegex != null ? state.nexusDependencyRegex : "";
    }

    public void setNexusDependencyRegex(@NotNull String nexusDependencyRegex) {
        state.nexusDependencyRegex = nexusDependencyRegex;
    }

    public enum TriggerMode {
        ON_OPEN("On project open"),
        ON_SAVE("On file save"),
        MANUAL("Manual");

        private final String displayName;

        TriggerMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * The state object that gets serialized to XML.
     */
    public static class State {
        public String nexusBaseUrl = "";
        public String nexusUsername = "";
        public String nexusDependencyRegex = "";
        public boolean fallbackToMavenCentral = true;
        public int cacheTtlMinutes = 30;
        public List<VersionPolicy> versionPolicies = new ArrayList<>();
        public boolean showInlayHints = true;
        public TriggerMode triggerMode = TriggerMode.ON_OPEN;
        public String versionFilterRegex = "";

        // Default constructor for XML serialization
        public State() {
        }
    }
}
