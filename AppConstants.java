package com.socialnetwork.app;

/** Stable application constants shared by features. Change only with a matching migration/verification. */
public final class AppConstants {
    private AppConstants() {}

    public static final String PREFS = "social_network_session";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String CURRENT_USER_ID = "current_user_id";
    public static final String KEYSTORE = "AndroidKeyStore";
    public static final String KEY_ALIAS = "social_network_session_key_v1";

    public static final int PICK_MEDIA = 4101;
    public static final int CAMERA_CAPTURE = 4102;

    public static final long MAX_MEDIA_BYTES = 50L * 1024L * 1024L;
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_REQUEST_BODY_BYTES = 512 * 1024;
    public static final int MAX_IMAGE_PREVIEW_BYTES = 8 * 1024 * 1024;
    public static final int FEED_PAGE_SIZE = 20;
    public static final long VERIFIED_USER_CACHE_MS = 60_000L;
    public static final int TRANSIENT_RETRY_COUNT = 2;
    public static final long TRANSIENT_RETRY_BASE_MS = 500L;
    public static final long TRANSIENT_RETRY_MAX_MS = 5_000L;

    public static final int NETWORK_CONNECT_TIMEOUT_MS = 15_000;
    public static final int NETWORK_READ_TIMEOUT_MS = 30_000;
}
