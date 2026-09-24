package com.socialnetwork.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Human-readable feature inventory used by diagnostics and future feature work. */
public final class FeatureRegistry {
    private FeatureRegistry() {}

    public static Map<String, Boolean> current() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("feed_pagination", FeatureFlags.FEED_PAGINATION);
        map.put("stories", FeatureFlags.STORIES);
        map.put("likes", FeatureFlags.LIKES);
        map.put("comments", FeatureFlags.COMMENTS);
        map.put("profiles", FeatureFlags.PROFILES);
        map.put("follows", FeatureFlags.FOLLOWS);
        map.put("notifications", FeatureFlags.NOTIFICATIONS);
        map.put("messages", FeatureFlags.MESSAGES);
        return Collections.unmodifiableMap(map);
    }
}
