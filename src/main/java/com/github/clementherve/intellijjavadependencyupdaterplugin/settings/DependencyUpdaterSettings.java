package com.github.clementherve.intellijjavadependencyupdaterplugin.settings;

import com.github.clementherve.intellijjavadependencyupdaterplugin.model.VersionPolicy;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
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

    private State myState = new State();
    private final Project myProject;

    public DependencyUpdaterSettings(Project project) {
        this.myProject = project;
    }

    public static DependencyUpdaterSettings getInstance(@NotNull Project project) {
        return project.getService(DependencyUpdaterSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    public String getNexusBaseUrl() {
        return myState.nexusBaseUrl;
    }

    public void setNexusBaseUrl(String nexusBaseUrl) {
        myState.nexusBaseUrl = nexusBaseUrl;
    }

    public String getNexusUsername() {
        return myState.nexusUsername;
    }

    public void setNexusUsername(String nexusUsername) {
        myState.nexusUsername = nexusUsername;
    }

    @Nullable
    public String getNexusPassword() {
        CredentialAttributes attributes = createCredentialAttributes();
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    public void setNexusPassword(@Nullable String password) {
        CredentialAttributes attributes = createCredentialAttributes();
        Credentials credentials = password != null ? new Credentials(myState.nexusUsername, password) : null;
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    private CredentialAttributes createCredentialAttributes() {
        String serviceName = "DependencyUpdater-" + myProject.getName();
        return new CredentialAttributes(
            CredentialAttributesKt.generateServiceName("DependencyUpdater", serviceName)
        );
    }

    public boolean isFallbackToMavenCentral() {
        return myState.fallbackToMavenCentral;
    }

    public void setFallbackToMavenCentral(boolean fallbackToMavenCentral) {
        myState.fallbackToMavenCentral = fallbackToMavenCentral;
    }

    public int getCacheTtlMinutes() {
        return myState.cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        myState.cacheTtlMinutes = cacheTtlMinutes;
    }

    @NotNull
    public List<VersionPolicy> getVersionPolicies() {
        if (myState.versionPolicies == null || myState.versionPolicies.isEmpty()) {
            return Collections.singletonList(VersionPolicy.createDefaultStablePolicy());
        }
        return new ArrayList<>(myState.versionPolicies);
    }

    public void setVersionPolicies(@NotNull List<VersionPolicy> versionPolicies) {
        myState.versionPolicies = new ArrayList<>(versionPolicies);
    }

    public boolean isShowGutterIcons() {
        return myState.showGutterIcons;
    }

    public void setShowGutterIcons(boolean showGutterIcons) {
        myState.showGutterIcons = showGutterIcons;
    }

    public boolean isShowInlayHints() {
        return myState.showInlayHints;
    }

    public void setShowInlayHints(boolean showInlayHints) {
        myState.showInlayHints = showInlayHints;
    }

    @NotNull
    public TriggerMode getTriggerMode() {
        return myState.triggerMode;
    }

    public void setTriggerMode(@NotNull TriggerMode triggerMode) {
        myState.triggerMode = triggerMode;
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
        public boolean fallbackToMavenCentral = true;
        public int cacheTtlMinutes = 30;
        public List<VersionPolicy> versionPolicies = new ArrayList<>();
        public boolean showGutterIcons = true;
        public boolean showInlayHints = true;
        public TriggerMode triggerMode = TriggerMode.ON_OPEN;

        // Default constructor for XML serialization
        public State() {
        }
    }
}
