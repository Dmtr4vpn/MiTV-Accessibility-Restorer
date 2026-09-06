package com.mitv.accessibilityrestorer;

import android.content.ComponentName;

final class RecoveryTargets {
    static final String RESTORER_PACKAGE = "com.mitv.accessibilityrestorer";
    static final String PROJECTIVY_PACKAGE = "com.spocky.projengmenu";
    static final String MAPPER_PACKAGE = "flar2.homebutton";
    static final String MAPPER_UNSTOP_PRIMARY_FLAT = "flar2.homebutton/a.s";
    static final String MAPPER_UNSTOP_SECONDARY_FLAT = "flar2.homebutton/a.r";
    static final String PROJECTIVY_UNSTOP_RECEIVER_FLAT =
            "com.spocky.projengmenu/.services.StartUpBootReceiver";

    static final ComponentName PROJECTIVY_ACTIVITY = new ComponentName(
            PROJECTIVY_PACKAGE,
            "com.spocky.projengmenu.ui.home.MainActivity");
    static final ComponentName PROJECTIVY_SERVICE = new ComponentName(
            PROJECTIVY_PACKAGE,
            "com.spocky.projengmenu.services.ProjectivyAccessibilityService");
    static final ComponentName MAPPER_ACTIVITY = new ComponentName(MAPPER_PACKAGE, "a.a");
    static final ComponentName MAPPER_SERVICE = new ComponentName(MAPPER_PACKAGE, "a.i");
    static final ComponentName MAPPER_UNSTOP_PRIMARY =
            component(MAPPER_UNSTOP_PRIMARY_FLAT);
    static final ComponentName MAPPER_UNSTOP_SECONDARY =
            component(MAPPER_UNSTOP_SECONDARY_FLAT);
    static final ComponentName PROJECTIVY_UNSTOP_RECEIVER =
            component(PROJECTIVY_UNSTOP_RECEIVER_FLAT);

    private RecoveryTargets() {
    }

    private static ComponentName component(String flattened) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null) {
            throw new IllegalArgumentException("Invalid component: " + flattened);
        }
        return component;
    }
}
