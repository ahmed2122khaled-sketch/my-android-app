package com.socialnetwork.app;

/**
 * Central feature switches. Keep defaults production-safe. A flag must guard a complete,
 * testable feature boundary; do not use flags to hide broken core behavior.
 */
public final class FeatureFlags {
    private FeatureFlags() {}

    public static final boolean FEED_PAGINATION = true;
    public static final boolean STORIES = true;
    public static final boolean LIKES = true;
    public static final boolean COMMENTS = true;
    public static final boolean PROFILES = true;
    public static final boolean FOLLOWS = true;
    public static final boolean NOTIFICATIONS = true;
    public static final boolean MESSAGES = true;
}
