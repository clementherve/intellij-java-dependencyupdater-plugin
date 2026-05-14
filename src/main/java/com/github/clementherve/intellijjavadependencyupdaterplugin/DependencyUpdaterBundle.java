package com.github.clementherve.intellijjavadependencyupdaterplugin;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class DependencyUpdaterBundle extends DynamicBundle {

    private static final String BUNDLE = "messages.DependencyUpdaterBundle";
    private static final DependencyUpdaterBundle INSTANCE = new DependencyUpdaterBundle();

    private DependencyUpdaterBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                 Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
