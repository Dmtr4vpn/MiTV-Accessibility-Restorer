package com.mitv.accessibilityrestorer;

import android.content.ComponentName;

import java.util.LinkedHashSet;
import java.util.Set;

final class AccessibilityPlan {
    final String raw;
    final String base;
    final String mapper;
    final String projectivy;
    final String allInitial;
    final String allFinal;

    private AccessibilityPlan(
            String raw,
            String base,
            String mapper,
            String projectivy,
            String allInitial,
            String allFinal) {
        this.raw = raw;
        this.base = base;
        this.mapper = mapper;
        this.projectivy = projectivy;
        this.allInitial = allInitial;
        this.allFinal = allFinal;
    }

    static AccessibilityPlan create(
            String existing,
            boolean manageMapper,
            boolean manageProjectivy,
            boolean includeTorrServe) {
        String raw = existing == null ? "" : existing;
        String[] tokens = raw.split(":", -1);
        StringBuilder base = new StringBuilder();
        boolean firstRetainedToken = true;
        for (String token : tokens) {
            boolean removeMapper = matches(token, RecoveryTargets.MAPPER_SERVICE);
            boolean removeProjectivy = matches(
                    token, RecoveryTargets.PROJECTIVY_SERVICE);
            boolean removeTorrServe = matches(
                    token, TorrServeTarget.ACCESSIBILITY_SERVICE);
            if (!removeMapper && !removeProjectivy && !removeTorrServe) {
                if (!firstRetainedToken) {
                    base.append(':');
                }
                base.append(token);
                firstRetainedToken = false;
            }
        }

        String baseValue = base.toString();
        String mapperValue = manageMapper
                ? append(baseValue, RecoveryTargets.MAPPER_SERVICE)
                : baseValue;
        String projectivyValue = manageProjectivy
                ? append(baseValue, RecoveryTargets.PROJECTIVY_SERVICE)
                : baseValue;
        String allInitialValue = manageProjectivy
                ? append(mapperValue, RecoveryTargets.PROJECTIVY_SERVICE)
                : mapperValue;
        String allFinalValue = includeTorrServe
                ? append(allInitialValue, TorrServeTarget.ACCESSIBILITY_SERVICE)
                : allInitialValue;
        return new AccessibilityPlan(
                raw,
                baseValue,
                mapperValue,
                projectivyValue,
                allInitialValue,
                allFinalValue);
    }

    static boolean sameComponentSet(String left, String right) {
        return canonicalSet(left).equals(canonicalSet(right));
    }

    private static Set<String> canonicalSet(String services) {
        Set<String> result = new LinkedHashSet<String>();
        if (services == null || services.isEmpty()) {
            return result;
        }
        String[] tokens = services.split(":", -1);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            ComponentName component = ComponentName.unflattenFromString(token);
            result.add(component == null ? "raw:" + token : component.flattenToString());
        }
        return result;
    }

    private static boolean matches(String token, ComponentName expected) {
        ComponentName component = ComponentName.unflattenFromString(token);
        return expected.equals(component) || expected.flattenToString().equals(token);
    }

    private static String append(String services, ComponentName component) {
        StringBuilder result = new StringBuilder(services == null ? "" : services);
        if (result.length() > 0 && result.charAt(result.length() - 1) != ':') {
            result.append(':');
        }
        result.append(component.flattenToString());
        return result.toString();
    }
}
