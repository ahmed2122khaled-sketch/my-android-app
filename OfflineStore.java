package com.socialnetwork.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Small private SQLite store for offline-first operation.
 * Keeps server GET responses and authenticated mutation intents locally.
 * Tokens are intentionally NOT stored here; the current Android Keystore-backed
 * session is used when queued operations are synchronized.
 */
final class OfflineStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "mrx_offline.db";
    private static final int DB_VERSION = 5;
    private static final long DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long MAX_DB_BYTES = 24L * 1024L * 1024L;
    private static final int MAX_CACHE_ENTRIES = 200;
    private static final int MAX_CACHE_BODY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PENDING_BODY_BYTES = 512 * 1024;
    private static final int MAX_PENDING_ENTRIES = 500;

    OfflineStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
        db.execSQL("PRAGMA busy_timeout=2500");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cache (k TEXT PRIMARY KEY, url TEXT NOT NULL, body TEXT NOT NULL, owner_user_id TEXT, saved_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX cache_saved_at ON cache(saved_at)");
        db.execSQL("CREATE INDEX cache_expires_at ON cache(expires_at)");
        db.execSQL("CREATE INDEX cache_owner_expires_at ON cache(owner_user_id, expires_at)");
        db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, method TEXT NOT NULL, url TEXT NOT NULL, body TEXT, owner_user_id TEXT, operation_id TEXT, created_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX pending_created_at ON pending(created_at, id)");
        db.execSQL("CREATE TABLE sync_state (resource TEXT PRIMARY KEY, cursor TEXT, synced_at INTEGER NOT NULL, dirty INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX sync_state_dirty ON sync_state(dirty, synced_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE cache ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE pending ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS cache_expires_at ON cache(expires_at)");
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (resource TEXT PRIMARY KEY, cursor TEXT, synced_at INTEGER NOT NULL, dirty INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE INDEX IF NOT EXISTS sync_state_dirty ON sync_state(dirty, synced_at)");
            db.execSQL("UPDATE cache SET expires_at=saved_at+600000 WHERE expires_at=0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE pending ADD COLUMN owner_user_id TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS pending_owner_created_at ON pending(owner_user_id, created_at, id)");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE cache ADD COLUMN owner_user_id TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS cache_owner_expires_at ON cache(owner_user_id, expires_at)");
            // Old cache entries cannot be safely attributed to an account.
            // They are deliberately invalidated rather than risking cross-account disclosure.
            db.delete("cache", null, null);
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE pending ADD COLUMN operation_id TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS pending_owner_operation ON pending(owner_user_id, operation_id, created_at, id)");
        }
    }

    String getCached(String url, String ownerUserId) {
        if (url == null || url.isEmpty() || !isValidOwner(ownerUserId)) return null;
        String cacheKey = key(url, ownerUserId);
        try (Cursor c = getReadableDatabase().query("cache", new String[]{"body","expires_at"}, "k=? AND owner_user_id=?", new String[]{cacheKey, ownerUserId}, null, null, null, "1")) {
            if (c.moveToFirst()) {
                long expires = c.getLong(1);
                if (expires >= System.currentTimeMillis()) return c.getString(0);
                getWritableDatabase().delete("cache", "k=? AND owner_user_id=?", new String[]{cacheKey, ownerUserId});
            }
        } catch (Exception ignored) { }
        return null;
    }

    void putCached(String url, String body, String ownerUserId) {
        if (url == null || body == null || !isValidOwner(ownerUserId) || body.getBytes(StandardCharsets.UTF_8).length > MAX_CACHE_BODY_BYTES) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("k", key(url, ownerUserId)); v.put("url", url); v.put("body", body); v.put("owner_user_id", ownerUserId); v.put("saved_at", now); v.put("expires_at", now + DEFAULT_CACHE_TTL_MS);
        db.insertWithOnConflict("cache", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        try {
            db.delete("cache", "expires_at<?", new String[]{String.valueOf(now)});
            int count = 0;
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM cache", null)) { if (c.moveToFirst()) count = c.getInt(0); }
            if (count > MAX_CACHE_ENTRIES) db.execSQL("DELETE FROM cache WHERE k IN (SELECT k FROM cache ORDER BY saved_at ASC LIMIT ?)", new Object[]{count - MAX_CACHE_ENTRIES});
        } catch (Exception ignored) { }
    }

    void clearCache() {
        try { getWritableDatabase().delete("cache", null, null); } catch (Exception ignored) { }
    }

    private static boolean isValidOwner(String ownerUserId) {
        return ownerUserId != null && ownerUserId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    }

    void markSync(String resource, String cursor, boolean dirty) {
        if (resource == null || resource.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("resource", resource); v.put("cursor", cursor); v.put("synced_at", System.currentTimeMillis()); v.put("dirty", dirty ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("sync_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    long enqueue(String method, String url, String body, String ownerUserId) {
        return enqueue(method, url, body, ownerUserId, "");
    }

    long enqueue(String method, String url, String body, String ownerUserId, String operationId) {
        if (method == null || method.isEmpty() || url == null || url.isEmpty()) return -1L;
        if (ownerUserId == null || ownerUserId.isEmpty()) return -1L;
        if (operationId == null || operationId.isEmpty() || operationId.length() > 80) return -1L;
        if (!ownerUserId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) return -1L;
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_PENDING_BODY_BYTES) return -1L;
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending", null)) {
            // Never evict an older user mutation just to make room for a new one.
            // Losing an already accepted local mutation is worse than rejecting the new enqueue.
            if (c.moveToFirst() && c.getInt(0) >= MAX_PENDING_ENTRIES) return -1L;
        }
        ContentValues v = new ContentValues();
        v.put("method", method); v.put("url", url); if (body != null) v.put("body", body); v.put("owner_user_id", ownerUserId); v.put("operation_id", operationId); v.put("created_at", System.currentTimeMillis()); v.put("attempts", 0);
        return db.insert("pending", null, v);
    }

    List<PendingOperation> pendingOperations(int limit, String ownerUserId) {
        ArrayList<PendingOperation> out = new ArrayList<>();
        if (ownerUserId == null || ownerUserId.isEmpty()) return out;
        String selection = "owner_user_id=?";
        String[] args = new String[]{ownerUserId};
        try (Cursor c = getReadableDatabase().query("pending", new String[]{"id","method","url","body","owner_user_id","operation_id"}, selection, args, null, null, "created_at ASC, id ASC", String.valueOf(Math.max(1, Math.min(limit, 100))))) {
            while (c.moveToNext()) out.add(new PendingOperation(c.getLong(0), c.getString(1), c.getString(2), c.isNull(3) ? null : c.getString(3), c.isNull(4) ? "" : c.getString(4), c.isNull(5) ? "" : c.getString(5)));
        }
        return out;
    }

    /** Legacy recovery path: only inspect unbound rows so account isolation is preserved. */
    List<PendingOperation> unboundPendingOperations(int limit) {
        ArrayList<PendingOperation> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("pending", new String[]{"id","method","url","body","owner_user_id","operation_id"}, "owner_user_id IS NULL OR owner_user_id=''", null, null, null, "created_at ASC, id ASC", String.valueOf(Math.max(1, Math.min(limit, 100))))) {
            while (c.moveToNext()) out.add(new PendingOperation(c.getLong(0), c.getString(1), c.getString(2), c.isNull(3) ? null : c.getString(3), c.isNull(4) ? "" : c.getString(4), c.isNull(5) ? "" : c.getString(5)));
        }
        return out;
    }

    void bindPendingToUser(long id, String ownerUserId) {
        if (id <= 0 || ownerUserId == null || ownerUserId.isEmpty()) return;
        getWritableDatabase().execSQL("UPDATE pending SET owner_user_id=? WHERE id=? AND (owner_user_id IS NULL OR owner_user_id='')", new Object[]{ownerUserId, id});
    }


    void deletePending(long id) { getWritableDatabase().delete("pending", "id=?", new String[]{String.valueOf(id)}); }

    int pendingAttempts(long id) {
        try (Cursor c = getReadableDatabase().query("pending", new String[]{"attempts"}, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    int incrementPendingAttempts(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE pending SET attempts=attempts+1 WHERE id=?", new Object[]{id});
        return pendingAttempts(id);
    }

    void compact() {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.delete("cache", "expires_at<?", new String[]{String.valueOf(now)});
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM cache", null)) {
            if (c.moveToFirst()) {
                int excess = c.getInt(0) - MAX_CACHE_ENTRIES;
                if (excess > 0) db.execSQL("DELETE FROM cache WHERE k IN (SELECT k FROM cache ORDER BY saved_at ASC LIMIT ?)", new Object[]{excess});
            }
        }
        try (Cursor c = db.rawQuery("PRAGMA page_count", null); Cursor p = db.rawQuery("PRAGMA page_size", null)) {
            if (c.moveToFirst() && p.moveToFirst() && c.getLong(0) * p.getLong(0) > MAX_DB_BYTES) db.execSQL("PRAGMA incremental_vacuum");
        } catch (Exception ignored) { }
    }

    boolean hasPending() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM pending LIMIT 1", null)) { return c.moveToFirst(); }
    }

    boolean hasRetryablePending(int maxAttempts, String ownerUserId) {
        if (ownerUserId == null || ownerUserId.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM pending WHERE owner_user_id=? AND attempts<? LIMIT 1", new String[]{ownerUserId, String.valueOf(maxAttempts)})) {
            return c.moveToFirst();
        }
    }

    private static String key(String url, String ownerUserId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((ownerUserId + "\n" + url).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(d.length * 2);
            for (byte x : d) b.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
            return b.toString();
        } catch (Exception e) { return Integer.toHexString((ownerUserId + "\n" + url).hashCode()); }
    }

    static final class PendingOperation {
        final long id; final String method; final String url; final String body; final String ownerUserId; final String operationId;
        PendingOperation(long id, String method, String url, String body, String ownerUserId, String operationId) { this.id=id; this.method=method; this.url=url; this.body=body; this.ownerUserId=ownerUserId; this.operationId=operationId; }
    }
}
