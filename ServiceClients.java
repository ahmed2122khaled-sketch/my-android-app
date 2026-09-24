package com.socialnetwork.app;

import android.net.Uri;

/**
 * Immutable, lightweight endpoint clients. Each service owns its base URL and
 * exposes only path construction; feature code does not concatenate service URLs.
 */
public final class ServiceClients {
    public final Client auth;
    public final Client dataRead;
    public final Client dataWrite;
    public final Client storage;

    public ServiceClients(String authUrl, String dataReadUrl, String dataWriteUrl, String storageUrl) {
        auth = new Client(authUrl);
        dataRead = new Client(dataReadUrl);
        dataWrite = new Client(dataWriteUrl);
        storage = new Client(storageUrl);
    }

    public static final class Client {
        private final String baseUrl;

        private Client(String baseUrl) {
            this.baseUrl = normalize(baseUrl);
        }

        public String baseUrl() { return baseUrl; }

        public String path(String path) {
            if (path == null || path.isEmpty()) return baseUrl;
            return baseUrl + (path.charAt(0) == '/' ? path : "/" + path);
        }

        public String auth(String path) { return path("/auth/v1/" + trimLeading(path)); }
        public String rest(String path) { return path("/rest/v1/" + trimLeading(path)); }
        public String storage(String path) { return path("/storage/v1/" + trimLeading(path)); }

        private static String trimLeading(String value) {
            if (value == null) return "";
            int i = 0;
            while (i < value.length() && value.charAt(i) == '/') i++;
            return value.substring(i);
        }

        private static String normalize(String value) {
            if (value == null) return "";
            String s = value.trim();
            while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            return s;
        }
    }
}
