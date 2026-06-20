package com.zyt.medconsensus.config;

import java.util.Locale;

public final class WebSocketUserNames {

    private WebSocketUserNames() {
    }

    public static String forRole(String role, Long userId) {
        return role.trim().toUpperCase(Locale.ROOT) + ":" + userId;
    }

    public static String doctor(Long userId) {
        return forRole("DOCTOR", userId);
    }
}
