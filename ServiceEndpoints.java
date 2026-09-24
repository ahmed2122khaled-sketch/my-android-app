package com.socialnetwork.app;

import android.net.Uri;

/**
 * Lightweight immutable service map. Feature code never needs to know where a
 * service lives. Optional endpoints fall back to the primary endpoint.
 */
public final class ServiceEndpoints {
    public final String primary;
    public final String dataRead;
    public final String dataWrite;
    public final String auth;
    public final String storage;
    public final String key;
    public final ServiceClients clients;

    public ServiceEndpoints(String primary, String dataRead, String dataWrite,
                            String auth, String storage, String key) {
        this.primary = clean(primary);
        this.dataRead = fallback(clean(dataRead), this.primary);
        this.dataWrite = fallback(clean(dataWrite), this.primary);
        this.auth = fallback(clean(auth), this.primary);
        this.storage = fallback(clean(storage), this.primary);
        this.key = key == null ? "" : key.trim();
        this.clients = new ServiceClients(this.auth, this.dataRead, this.dataWrite, this.storage);
    }

    private static String clean(String value) {
        if (value == null) return "";
        String s = value.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String fallback(String value, String primary) {
        return value.isEmpty() ? primary : value;
    }

    public String serviceBase(String service) {
        if ("auth".equals(service)) return clients.auth.baseUrl();
        if ("data_read".equals(service)) return clients.dataRead.baseUrl();
        if ("data_write".equals(service)) return clients.dataWrite.baseUrl();
        if ("storage".equals(service)) return clients.storage.baseUrl();
        return primary;
    }

    public boolean configured() {
        return !primary.isEmpty() && !key.isEmpty()
                && isHttps(primary) && validOrigin(dataRead)
                && validOrigin(dataWrite) && validOrigin(auth)
                && validOrigin(storage);
    }

    private boolean validOrigin(String endpoint) {
        return isHttps(endpoint) && safeOrigin(endpoint);
    }

    private boolean isHttps(String endpoint) {
        try { return "https".equalsIgnoreCase(Uri.parse(endpoint).getScheme()); }
        catch (Exception e) { return false; }
    }

    private boolean safeOrigin(String endpoint) {
        try {
            Uri u = Uri.parse(endpoint);
            return u.getHost() != null
                    && u.getUserInfo() == null
                    && u.getFragment() == null
                    && u.getQuery() == null
                    && (u.getPath() == null || u.getPath().isEmpty() || "/".equals(u.getPath()))
                    && (u.getPort() == -1 || (u.getPort() >= 1 && u.getPort() <= 65535));
        } catch (Exception e) { return false; }
    }

    /** Maps an application request to the correct independent service. */
    public String route(String method, String url) {
        if (url == null) return null;
        String path = pathFromOrigin(url, primary);
        if (path == null) return url;
        if (path.startsWith("/auth/")) return auth + path;
        if (path.startsWith("/storage/")) return storage + path;
        if (path.startsWith("/rest/")) {
            boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
            return (read ? dataRead : dataWrite) + path;
        }
        return url;
    }

    private String pathFromOrigin(String url, String origin) {
        try {
            Uri u = Uri.parse(url), b = Uri.parse(origin);
            if (!isHttps(u.getScheme()) || !isHttps(b.getScheme())
                    || u.getUserInfo() != null || u.getFragment() != null
                    || !sameOrigin(u, b)) return null;
            String path = u.getPath();
            if (path == null) path = "/";
            if (u.getQuery() != null && !u.getQuery().isEmpty()) path += "?" + u.getQuery();
            return path;
        } catch (Exception e) { return null; }
    }

    public String fallbackWrite(String url) {
        if (url == null) return null;
        String path = pathFromOrigin(url, primary);
        if (path == null) return url;
        return dataWrite + path;
    }

    public boolean isReadReplica(String method, String url) {
        return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                && url != null
                && pathFromOrigin(url, primary) != null
                && pathFromOrigin(url, primary).startsWith("/rest/")
                && !dataRead.equals(dataWrite);
    }

    public boolean trusts(String value) {
        try {
            Uri target = Uri.parse(value);
            if (!isHttps(target.getScheme()) || target.getHost() == null
                    || target.getUserInfo() != null || target.getFragment() != null) return false;
            return sameOrigin(target, primary) || sameOrigin(target, dataRead)
                    || sameOrigin(target, dataWrite) || sameOrigin(target, auth)
                    || sameOrigin(target, storage);
        } catch (Exception e) { return false; }
    }

    private boolean isHttps(String scheme) { return "https".equalsIgnoreCase(scheme); }

    private boolean sameOrigin(Uri target, String configured) {
        try {
            Uri base = Uri.parse(configured);
            int basePort = base.getPort() == -1 ? 443 : base.getPort();
            int targetPort = target.getPort() == -1 ? 443 : target.getPort();
            return target.getHost().equalsIgnoreCase(base.getHost()) && targetPort == basePort;
        } catch (Exception e) { return false; }
    }
}
