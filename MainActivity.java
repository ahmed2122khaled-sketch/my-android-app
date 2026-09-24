package com.socialnetwork.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.math.BigInteger;
import java.security.spec.X509EncodedKeySpec;
import javax.security.auth.x500.X500Principal;
import javax.crypto.spec.SecretKeySpec;
import java.util.UUID;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Native Android entry point. No WebView and no website UI. */
public class MainActivity extends Activity {
    private static final String PREFS = AppConstants.PREFS;
    private static final String ACCESS_TOKEN = AppConstants.ACCESS_TOKEN;
    private static final String REFRESH_TOKEN = AppConstants.REFRESH_TOKEN;
    private static final String CURRENT_USER_ID = AppConstants.CURRENT_USER_ID;
    private static final String KEYSTORE = AppConstants.KEYSTORE;
    private static final String KEY_ALIAS = AppConstants.KEY_ALIAS;
    private static final String LEGACY_RSA_KEY_ALIAS = KEY_ALIAS + "_rsa";
    private static final String LEGACY_WRAPPED_AES = "wrapped_session_key_v1";
    private static final int PICK_MEDIA = AppConstants.PICK_MEDIA;
    private static final int CAMERA_CAPTURE = AppConstants.CAMERA_CAPTURE;
    private static final long MAX_MEDIA_BYTES = AppConstants.MAX_MEDIA_BYTES;

    private final AppExecutors appExecutors = new AppExecutors();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView status;
    private Uri selectedMedia;
    private String selectedMime;
    private Uri pendingCameraUri;
    private final Object refreshLock = new Object();
    private OfflineStore offlineStore;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean online = true;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private volatile boolean syncInFlight = false;
    private volatile boolean publishInFlight = false;
    private volatile boolean storyInFlight = false;
    private volatile boolean feedLoadInFlight = false;
    private volatile boolean messageSendInFlight = false;
    private volatile boolean profileUpdateInFlight = false;
    private volatile boolean peopleLoadInFlight = false;
    private final java.util.Set<String> followTargetsInFlight = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private volatile boolean notificationsMarkReadInFlight = false;
    private boolean pickingStoryMedia = false;
    private volatile String verifiedUserId = "";
    private volatile long verifiedUserIdAt = 0L;
    private static final long VERIFIED_USER_CACHE_MS = AppConstants.VERIFIED_USER_CACHE_MS;
    private static final int MAX_OFFLINE_SYNC_ATTEMPTS = 8;
    private int feedOffset = 0;
    private static final int FEED_PAGE_SIZE = AppConstants.FEED_PAGE_SIZE;
    private static final int MAX_RESPONSE_BYTES = AppConstants.MAX_RESPONSE_BYTES;
    private static final int MAX_REQUEST_BODY_BYTES = AppConstants.MAX_REQUEST_BODY_BYTES;
    private static final int MAX_IMAGE_PREVIEW_BYTES = AppConstants.MAX_IMAGE_PREVIEW_BYTES;
    private static final int TRANSIENT_RETRY_COUNT = AppConstants.TRANSIENT_RETRY_COUNT;
    private static final long TRANSIENT_RETRY_BASE_MS = AppConstants.TRANSIENT_RETRY_BASE_MS;
    private static final long TRANSIENT_RETRY_MAX_MS = AppConstants.TRANSIENT_RETRY_MAX_MS;
    private static final String APP_NAME = "mr.x";
    private static final String AUTH_CALLBACK_SCHEME = "mrx";
    private static final String AUTH_CALLBACK_HOST = "auth";
    private static final String AUTH_CALLBACK_PATH = "/callback";
    private static final String RECOVERY_STATE = "recovery_state";
    private static final String RECOVERY_STATE_AT = "recovery_state_at";
    private static final long RECOVERY_STATE_TTL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final String OFFLINE_POST_URL = "mrx://offline/post";
    private static final String OFFLINE_STORY_URL = "mrx://offline/story";
    private volatile boolean passwordResetInFlight = false;
    private volatile String currentOperationId = "";

    private final ServiceEndpoints services = new ServiceEndpoints(
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_DATA_READ_URL,
            BuildConfig.SUPABASE_DATA_WRITE_URL,
            BuildConfig.SUPABASE_AUTH_URL,
            BuildConfig.SUPABASE_STORAGE_URL,
            BuildConfig.SUPABASE_PUBLISHABLE_KEY);

    private String supabaseUrl() { return services.primary; }
    private String dataReadUrl() { return services.clients.dataRead.baseUrl(); }
    private String dataWriteUrl() { return services.clients.dataWrite.baseUrl(); }
    private String authUrl() { return services.clients.auth.baseUrl(); }
    private String storageUrl() { return services.clients.storage.baseUrl(); }
    private String supabaseKey() { return services.key; }

    /** All feature requests are routed through the immutable service map. */
    private String routeServiceEndpoint(String method, String url) {
        return services.route(method, url);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        configureSystemBars();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // Remove the legacy plaintext user-id cache; current identity binding is Keystore-backed.
        prefs.edit().remove("offline_user_id").apply();
        offlineStore = new OfflineStore(this);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        online = isNetworkAvailable();
        registerConnectivityMonitor();
        ensureKeystoreKey();
        showBoot();
        if (!isConfigured()) showConfigurationError();
        else if (handleAuthCallback(getIntent())) return;
        else if (hasSession()) showFeed(); else showLogin();
    }

    private void configureSystemBars() {
        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        } else if (android.os.Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(Color.WHITE);
            w.setNavigationBarColor(Color.WHITE);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!isFinishing() && handleAuthCallback(intent)) return;
    }

    @Override protected void onStart() {
        super.onStart();
        online = isNetworkAvailable();
        if (online) scheduleSync(0);
    }

    @Override protected void onStop() {
        super.onStop();
    }

    private boolean isNetworkAvailable() {
        try {
            if (connectivityManager == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                Network n = connectivityManager.getActiveNetwork();
                if (n == null) return false;
                NetworkCapabilities c = connectivityManager.getNetworkCapabilities(n);
                if (c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
                // VALIDATED is useful on newer Android releases, but API 23 devices
                // can have working Internet without exposing the newer validation
                // behavior consistently. Treat INTERNET as the compatibility floor.
                return android.os.Build.VERSION.SDK_INT < 24
                        || c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        || c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            }
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void registerConnectivityMonitor() {
        if (connectivityManager == null || android.os.Build.VERSION.SDK_INT < 21) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                online = isNetworkAvailable();
                if (online) scheduleSync(250);
                updateConnectivityStatus();
            }
            @Override public void onLost(Network network) {
                online = isNetworkAvailable();
                updateConnectivityStatus();
            }
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (Exception ignored) {
            networkCallback = null;
        }
    }

    private void updateConnectivityStatus() {
        postUi(() -> status.setText(online ? "متصل بالإنترنت · تتم المزامنة تلقائيًا" : "وضع عدم الاتصال · البيانات المحفوظة متاحة والتغييرات ستتزامن لاحقًا"));
    }

    private void scheduleSync(long delayMs) {
        syncHandler.removeCallbacksAndMessages("offline-sync");
        syncHandler.postAtTime(() -> syncPendingOperations(), "offline-sync", android.os.SystemClock.uptimeMillis() + Math.max(0, delayMs));
    }

    private void syncPendingOperations() {
        if (!online || syncInFlight || offlineStore == null || !hasSession()) return;
        syncInFlight = true;
        if (!submitIo(() -> {
            try {
                String refresh = readSecret(REFRESH_TOKEN);
                String ownerUserId = readSecret(CURRENT_USER_ID);
                if (ownerUserId.isEmpty()) { syncInFlight = false; return; }
                // Only load this account's queue. This prevents another account's
                // backlog from starving the current account and avoids needless
                // background wakeups after account switching.
                List<OfflineStore.PendingOperation> ops = offlineStore.pendingOperations(50, ownerUserId);
                // Recover only genuinely legacy/unbound rows; never adopt a row
                // already bound to a different account.
                for (OfflineStore.PendingOperation legacy : offlineStore.unboundPendingOperations(20)) {
                    if (pendingOperationBelongsToUser(legacy, ownerUserId)) offlineStore.bindPendingToUser(legacy.id, ownerUserId);
                }
                ops = offlineStore.pendingOperations(50, ownerUserId);
                for (OfflineStore.PendingOperation op : ops) {
                    if (!online) break;
                    if (offlineStore.pendingAttempts(op.id) >= MAX_OFFLINE_SYNC_ATTEMPTS) continue;
                    try {
                        if (OFFLINE_POST_URL.equals(op.url) || OFFLINE_STORY_URL.equals(op.url)) {
                            if (syncOfflineMediaOperation(op)) offlineStore.deletePending(op.id);
                            else break;
                            continue;
                        }
                        String token = readSecret(ACCESS_TOKEN);
                        HttpResult r = request(op.method, op.url, op.body, token);
                        if (r.code == 401 && !refresh.isEmpty() && refreshSessionIfNeeded(refresh)) {
                            token = readSecret(ACCESS_TOKEN);
                            r = request(op.method, op.url, op.body, token);
                        }
                        if (r.code >= 200 && r.code < 300) offlineStore.deletePending(op.id);
                        else if (r.code == 401) break;
                        else if (r.code >= 400 && r.code < 500 && r.code != 408 && r.code != 429) offlineStore.deletePending(op.id);
                    } catch (Exception ignored) {
                        int attempts = offlineStore.incrementPendingAttempts(op.id);
                        if (attempts >= MAX_OFFLINE_SYNC_ATTEMPTS) {
                            // Keep the mutation for inspection/recovery rather than silently deleting it.
                            postUi(() -> status.setText("تعذر مزامنة مهمة Offline بعد عدة محاولات؛ ستظل محفوظة محليًا ولن تتكرر بلا حد."));
                        }
                        break;
                    }
                }
            } finally {
                syncInFlight = false;
                String activeOwner = readSecret(CURRENT_USER_ID);
                if (online && !activeOwner.isEmpty() && offlineStore.hasRetryablePending(MAX_OFFLINE_SYNC_ATTEMPTS, activeOwner)) syncHandler.postAtTime(this::syncPendingOperations, "offline-sync", android.os.SystemClock.uptimeMillis() + 15000);
                postUi(this::updateConnectivityStatus);
            }
        })) {
            syncInFlight = false;
        }
    }

    @Override protected void onDestroy() {
        if (isFinishing() && pendingCameraUri != null) {
            try { getContentResolver().delete(pendingCameraUri, null, null); } catch (Exception ignored) {}
            pendingCameraUri = null;
        }
        main.removeCallbacksAndMessages(null);
        syncHandler.removeCallbacksAndMessages(null);
        if (connectivityManager != null && networkCallback != null) { try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {} }
        networkCallback = null;
        appExecutors.shutdown();
        super.onDestroy();
    }

    private boolean isConfigured() {
        try {
            return services.configured()
                    && !supabaseKey().contains("xxx")
                    && !supabaseKey().contains("YOUR_PUBLIC");
        } catch (Exception e) { return false; }
    }

    private boolean isValidConfiguredEndpoint(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && !value.contains("YOUR_PROJECT")
                    && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        } catch (Exception e) { return false; }
    }

    private void showConfigurationError() {
        base("إعداد التطبيق");
        status.setText("لم يتم إعداد Supabase بعد. ضع SUPABASE_URL وSUPABASE_PUBLISHABLE_KEY في android/supabase.properties ثم أعد البناء.");
    }

    private void showBoot() {
        base(APP_NAME);
        ProgressBar p = new ProgressBar(this);
        root.addView(p, new LinearLayout.LayoutParams(-1, 80));
        status.setText("جارٍ تشغيل التطبيق...");
    }

    private boolean hasSession() { return !readSecret(ACCESS_TOKEN).isEmpty() && !readSecret(REFRESH_TOKEN).isEmpty(); }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable rounded(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private String beginOperation() {
        currentOperationId = UUID.randomUUID().toString();
        return currentOperationId;
    }

    private String currentOperationId() {
        String id = currentOperationId;
        if (id == null || id.isEmpty()) return beginOperation();
        return id;
    }

    private void base(String title) {
        beginOperation();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.setBackgroundColor(Color.rgb(250,250,250));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(4), 0, dp(10));
        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);
        brandBlock.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(APP_NAME);
        heading.setTextSize(23);
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.rgb(18,18,18));
        brandBlock.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView operationIdView = new TextView(this);
        operationIdView.setText("ID: " + currentOperationId());
        operationIdView.setTextSize(9);
        operationIdView.setTextColor(Color.rgb(105,105,105));
        operationIdView.setSingleLine(true);
        operationIdView.setContentDescription("معرّف عملية mr.x: " + currentOperationId());
        brandBlock.addView(operationIdView, new LinearLayout.LayoutParams(-1, -2));
        top.addView(brandBlock, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dot = new TextView(this);
        dot.setText("♡   ✉");
        dot.setTextSize(21);
        dot.setTextColor(Color.rgb(18,18,18));
        dot.setContentDescription("معلومات وحقوق تطبيق mr.x");
        dot.setOnClickListener(v -> showLegalNotice());
        top.addView(dot, new LinearLayout.LayoutParams(-2, -2));
        root.addView(top);

        status = new TextView(this);
        status.setTextColor(Color.rgb(105,105,105));
        status.setTextSize(12);
        status.setPadding(0, 0, 0, dp(6));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        updateConnectivityStatus();
        final int baseLeft = dp(16), baseTop = dp(8), baseRight = dp(16), baseBottom = dp(8);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = i.left; top = i.top; right = i.right; bottom = i.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void showLegalNotice() {
        final String text =
                "mr.x\n\n" +
                "Copyright © 2026 mr.x. All rights reserved.\n\n" +
                "هذا التطبيق والاسم والعلامات والهوية البصرية والشفرة البرمجية الأصلية الخاصة بالمشروع محمية بالحقوق المطبقة عليها. لا يجوز نسخ أو إعادة توزيع أو تعديل أو بيع أو إعادة نشر الأجزاء الأصلية من المشروع دون إذن من صاحب الحقوق، ما لم ينص ترخيص مستقل على خلاف ذلك.\n\n" +
                "المكونات أو الخدمات التابعة لأطراف أخرى تظل خاضعة لتراخيصها وشروطها الخاصة، ولا يمنح هذا الإشعار حقوقًا إضافية عليها. استخدام Supabase أو أي خدمة خارجية لا يعني أن تلك الخدمة مملوكة لـ mr.x.\n\n" +
                "هذا الإشعار يوضح حقوق الملكية للمشروع ولا يُعد بديلًا عن التسجيل الرسمي للحقوق أو العلامات التجارية حيث يكون التسجيل مطلوبًا قانونًا.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("حول mr.x وحقوق النشر")
                .setMessage(text)
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setPadding(18,14,18,14);
        if (password) e.setInputType(0x81);
        root.addView(e, new LinearLayout.LayoutParams(-1,-2)); return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(25,25,25));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(rounded(Color.WHITE, 18));
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(48)));
        return b;
    }

    private void showLogin() {
        base(APP_NAME); status.setText("تسجيل الدخول إلى mr.x");
        EditText email=input("البريد الإلكتروني",false), password=input("كلمة المرور",true);
        Button login=button("تسجيل الدخول"), signup=button("إنشاء حساب"), forgot=button("نسيت كلمة المرور؟");
        login.setOnClickListener(v -> authenticate(email.getText().toString().trim(),password.getText().toString(),false));
        signup.setOnClickListener(v -> authenticate(email.getText().toString().trim(),password.getText().toString(),true));
        forgot.setOnClickListener(v -> requestPasswordReset(email.getText().toString().trim()));
    }

    private String randomRecoveryState() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aa = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aa, bb);
    }

    private void clearRecoveryState() {
        try { prefs.edit().remove(RECOVERY_STATE_AT).apply(); } catch (Exception ignored) {}
        try { prefs.edit().remove(RECOVERY_STATE).apply(); } catch (Exception ignored) {}
    }

    private void requestPasswordReset(String email) {
        if (!isValidEmail(email)) { toast("أدخل بريدًا إلكترونيًا صحيحًا أولًا."); return; }
        if (passwordResetInFlight) return;
        passwordResetInFlight = true;
        status.setText("جارٍ إرسال رسالة استعادة كلمة المرور...");
        if (!submitIo(() -> {
            try {
                String recoveryState = randomRecoveryState();
                saveSecret(RECOVERY_STATE, recoveryState);
                prefs.edit().putLong(RECOVERY_STATE_AT, System.currentTimeMillis()).apply();
                String redirectTo = AUTH_CALLBACK_SCHEME + "://" + AUTH_CALLBACK_HOST + AUTH_CALLBACK_PATH
                        + "?state=" + Uri.encode(recoveryState);
                JSONObject body = new JSONObject().put("email", email).put("redirect_to", redirectTo);
                HttpResult r = request("POST", supabaseUrl() + "/auth/v1/recover", body.toString(), null);
                if (r.code < 200 || r.code >= 300) throw new Exception(errorMessage(r.body));
                postUi(() -> status.setText("إذا كان البريد مرتبطًا بحساب، ستصلك الآن رسالة من mr.x لإعادة تعيين كلمة المرور."));
            } catch (Exception e) {
                clearRecoveryState();
                postUi(() -> status.setText("تعذر إرسال رسالة الاستعادة: " + safeMessage(e)));
            } finally { passwordResetInFlight = false; }
        })) {
            passwordResetInFlight = false;
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.length() <= 254 && Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matcher(email).matches();
    }

    private boolean handleAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri data = intent.getData();
        if (!AUTH_CALLBACK_SCHEME.equalsIgnoreCase(data.getScheme())
                || !AUTH_CALLBACK_HOST.equalsIgnoreCase(data.getHost())
                || !AUTH_CALLBACK_PATH.equals(data.getPath())) return false;
        String callbackState = data.getQueryParameter("state");
        String access = data.getQueryParameter("access_token");
        String refresh = data.getQueryParameter("refresh_token");
        String error = data.getQueryParameter("error_description");
        if (access == null || access.isEmpty()) {
            String fragment = data.getFragment();
            if (fragment != null) {
                for (String part : fragment.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    try {
                        String k = Uri.decode(part.substring(0, eq));
                        String v = Uri.decode(part.substring(eq + 1));
                        if ("access_token".equals(k)) access = v;
                        else if ("refresh_token".equals(k)) refresh = v;
                        else if ("error_description".equals(k)) error = v;
                        else if ("state".equals(k)) callbackState = v;
                    } catch (Exception ignored) {}
                }
            }
        }
        String expectedState = readSecret(RECOVERY_STATE);
        long issuedAt = prefs.getLong(RECOVERY_STATE_AT, 0L);
        boolean stateValid = !expectedState.isEmpty() && callbackState != null && !callbackState.isEmpty()
                && constantTimeEquals(expectedState, callbackState)
                && issuedAt > 0L && System.currentTimeMillis() - issuedAt <= RECOVERY_STATE_TTL_MS;
        if (!stateValid) {
            base(APP_NAME);
            status.setText("رابط الاستعادة غير صالح أو منتهي.");
            return true;
        }
        clearRecoveryState();
        if (error != null && !error.isEmpty()) {
            base(APP_NAME); status.setText("تعذر فتح رابط الاستعادة: " + error); return true;
        }
        if (access == null || access.isEmpty()) {
            base(APP_NAME); status.setText("رابط الاستعادة غير صالح أو منتهي."); return true;
        }
        if (refresh == null) refresh = "";
        try { saveSecret(ACCESS_TOKEN, access); if (!refresh.isEmpty()) saveSecret(REFRESH_TOKEN, refresh); }
        catch (Exception e) { clearSecrets(); base(APP_NAME); status.setText("تعذر تأمين جلسة الاستعادة."); return true; }
        showPasswordUpdate();
        return true;
    }

    private void showPasswordUpdate() {
        base(APP_NAME); status.setText("أنشئ كلمة مرور جديدة لحسابك.");
        EditText password=input("كلمة المرور الجديدة",true), confirm=input("تأكيد كلمة المرور",true);
        Button update=button("تحديث كلمة المرور"), cancel=button("إلغاء");
        update.setOnClickListener(v -> updatePassword(password.getText().toString(), confirm.getText().toString()));
        cancel.setOnClickListener(v -> { clearSecrets(); showLogin(); });
    }

    private void updatePassword(String password, String confirm) {
        if (password.length() < 8) { toast("كلمة المرور يجب أن تكون 8 أحرف على الأقل."); return; }
        if (!password.equals(confirm)) { toast("كلمتا المرور غير متطابقتين."); return; }
        status.setText("جارٍ تحديث كلمة المرور...");
        submitIo(() -> {
            try {
                HttpResult r = authenticatedRequest("PUT", supabaseUrl() + "/auth/v1/user", new JSONObject().put("password", password).toString());
                if (r.code < 200 || r.code >= 300) throw new Exception(errorMessage(r.body));
                postUi(() -> { toast("تم تغيير كلمة المرور بنجاح."); showFeed(); });
            } catch (Exception e) { postUi(() -> status.setText("تعذر تغيير كلمة المرور: " + safeMessage(e))); }
        });
    }

    private void authenticate(String email,String password,boolean signup) {
        if(email.isEmpty() || password.length()<8){toast("أدخل بريدًا صحيحًا وكلمة مرور من 8 أحرف على الأقل.");return;}
        status.setText("جارٍ الاتصال بالخادم...");
        submitIo(() -> {
            try {
                String endpoint=signup?"/auth/v1/signup":"/auth/v1/token?grant_type=password";
                JSONObject body=new JSONObject().put("email",email).put("password",password);
                HttpResult r=request("POST",supabaseUrl()+endpoint,body.toString(),null);
                if(r.code<200||r.code>=300) throw new Exception(errorMessage(r.body));
                JSONObject json=new JSONObject(r.body); String token=json.optString("access_token",""); String refresh=json.optString("refresh_token","");
                if(signup && token.isEmpty()){postUi(()->status.setText("تم إنشاء الحساب. إذا كان تأكيد البريد مفعّلًا، أكد البريد ثم سجّل الدخول."));return;}
                if(token.isEmpty()||refresh.isEmpty()) throw new Exception("الخادم لم يُرجع جلسة كاملة.");
                try {
                    saveSecret(ACCESS_TOKEN,token); saveSecret(REFRESH_TOKEN,refresh);
                    // Bind the offline queue to the authenticated account immediately.
                    // Supabase password-token responses normally include the user object;
                    // if a deployment omits it, currentUserId() will verify it lazily later.
                    String userId = json.optJSONObject("user") == null ? "" : json.optJSONObject("user").optString("id", "");
                    if (isUuid(userId)) saveSecret(CURRENT_USER_ID, userId);
                    clearRecoveryState();
                } catch (Exception secureError) {
                    clearSecrets(); throw secureError;
                }
                postUi(this::showFeed);
            }catch(Exception e){postUi(()->status.setText("فشل تسجيل الدخول: "+safeMessage(e)));}
        });
    }

    private boolean queueOfflineMedia(String targetUrl, Uri media, String mime, JSONObject metadata) {
        if (offlineStore == null || media == null || mime == null) return false;
        try {
            long size = querySize(media);
            if (size > MAX_MEDIA_BYTES) throw new Exception("حجم الملف أكبر من 50MB.");
            File dir = new File(getFilesDir(), "offline-media");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء مساحة التخزين المحلية.");
            File file = new File(dir, UUID.randomUUID().toString()+"."+mediaExtension(mime));
            try (InputStream in=getContentResolver().openInputStream(media); OutputStream out=new FileOutputStream(file)) {
                if (in == null) throw new Exception("تعذر قراءة الوسائط.");
                byte[] buf=new byte[8192]; int n; long total=0;
                while((n=in.read(buf))!=-1){ if(total > MAX_MEDIA_BYTES-n) throw new Exception("حجم الملف أكبر من 50MB."); out.write(buf,0,n); total+=n; }
            }
            metadata.put("_offline_media_path", file.getAbsolutePath());
            metadata.put("_offline_media_mime", mime);
            String ownerUserId = readSecret(CURRENT_USER_ID);
            if (ownerUserId.isEmpty()) throw new Exception("تعذر ربط العملية بحساب المستخدم الحالي.");
            long queuedId = offlineStore.enqueue("POST", targetUrl, metadata.toString(), ownerUserId, currentOperationId());
            if (queuedId < 0) {
                try { file.delete(); } catch (Exception ignored) {}
                throw new Exception("تعذر إضافة عملية الوسائط إلى طابور Offline.");
            }
            postUi(()->status.setText("تم حفظ الوسائط محليًا. ستُرفع تلقائيًا عند عودة الإنترنت."));
            return true;
        } catch(Exception e) {
            toast("تعذر حفظ الوسائط للعمل Offline: "+safeMessage(e));
            return false;
        }
    }

    private boolean pendingOperationBelongsToUser(OfflineStore.PendingOperation op, String userId) {
        if (op == null || userId == null || userId.isEmpty() || op.body == null) return false;
        try {
            JSONObject body = new JSONObject(op.body);
            String[] ownerFields = {"user_id", "sender_id", "follower_id"};
            for (String field : ownerFields) {
                String value = body.optString(field, "");
                if (!value.isEmpty()) return userId.equals(value);
            }
            String url = op.url == null ? "" : op.url;
            return url.contains("id=eq." + userId) || url.contains("user_id=eq." + userId)
                    || url.contains("follower_id=eq." + userId);
        } catch (Exception ignored) { return false; }
    }

    private boolean syncOfflineMediaOperation(OfflineStore.PendingOperation op) throws Exception {
        JSONObject m=new JSONObject(op.body==null?"{}":op.body);
        String path=m.optString("_offline_media_path","");
        String mime=m.optString("_offline_media_mime","");
        if(path.isEmpty() || mime.isEmpty()) return true;
        File f=new File(path);
        if(!f.isFile()) return true;
        Uri uri=Uri.fromFile(f);
        String folder=OFFLINE_STORY_URL.equals(op.url)?"stories":"posts";
        UploadedMedia uploaded=uploadMedia(uri,mime,folder);
        try {
            String userId=currentUserId();
            JSONObject body=new JSONObject();
            if (OFFLINE_STORY_URL.equals(op.url)) {
                body.put("user_id",userId).put("media_url",uploaded.url)
                    .put("media_type",mime.startsWith("video/")?"video":"image")
                    .put("caption",m.optString("caption","").isEmpty()?JSONObject.NULL:m.optString("caption"));
                HttpResult r=request("POST",supabaseUrl()+"/rest/v1/stories",body.toString(),readSecret(ACCESS_TOKEN));
                if(r.code<200||r.code>=300) throw new Exception(errorMessage(r.body));
            } else {
                String content=m.optString("content","");
                body.put("user_id",userId).put("content",content.isEmpty()?JSONObject.NULL:content)
                    .put("media_urls",new JSONArray().put(uploaded.url))
                    .put("media_type",mime.startsWith("video/")?"video":"image");
                HttpResult r=request("POST",supabaseUrl()+"/rest/v1/posts",body.toString(),readSecret(ACCESS_TOKEN));
                if(r.code<200||r.code>=300) throw new Exception(errorMessage(r.body));
            }
            if(!f.delete()) f.deleteOnExit();
            return true;
        } catch(Exception e) {
            deleteMedia(uploaded.path);
            throw e;
        }
    }

    private void showFeed(){
        base(APP_NAME);

        LinearLayout storiesBar = new LinearLayout(this);
        storiesBar.setOrientation(LinearLayout.HORIZONTAL);
        storiesBar.setPadding(0, dp(4), 0, dp(12));
        root.addView(storiesBar);
        loadStoryBar(storiesBar);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh=buttonIn(quick,"⌕ تحديث"); Button create=buttonIn(quick,"＋ منشور"); Button more=buttonIn(quick,"المزيد");
        refresh.setOnClickListener(v->{feedOffset=0;loadPosts(postsContainer,false);});
        create.setOnClickListener(v->showCreatePost(postsContainer));
        more.setOnClickListener(v->loadPosts(postsContainer,true));
        root.addView(quick,new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView scroll=new ScrollView(this);
        LinearLayout posts=new LinearLayout(this); posts.setOrientation(LinearLayout.VERTICAL); posts.setPadding(0,dp(8),0,dp(12));
        postsContainer=posts; scroll.addView(posts);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(0,dp(6),0,0);
        Button home=buttonIn(nav,"⌂\nالرئيسية"), people=buttonIn(nav,"⌕\nاستكشاف"), notifications=buttonIn(nav,"♡\nإشعارات"), messages=buttonIn(nav,"✉\nرسائل"), profile=buttonIn(nav,"◎\nحسابي");
        home.setOnClickListener(v->{feedOffset=0;loadPosts(postsContainer,false);});
        people.setOnClickListener(v->showPeople()); notifications.setOnClickListener(v->showNotifications()); messages.setOnClickListener(v->showMessages()); profile.setOnClickListener(v->showProfile());
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));

        storiesBar.setOnClickListener(v->showStories());
        loadPosts(posts,false);
    }

    private LinearLayout postsContainer;

    /** Builds the story strip from live backend data; no sample users are shown. */
    private void loadStoryBar(LinearLayout storiesBar) {
        if (storiesBar == null) return;
        storiesBar.removeAllViews();
        TextView own = new TextView(this);
        own.setText("◉\nقصتك");
        own.setGravity(Gravity.CENTER);
        own.setTextSize(11);
        own.setTextColor(Color.DKGRAY);
        own.setBackground(rounded(Color.WHITE, 32));
        own.setOnClickListener(v -> showStories());
        LinearLayout.LayoutParams ownParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        ownParams.setMargins(0, 0, dp(8), 0);
        storiesBar.addView(own, ownParams);

        submitIo(() -> {
            try {
                String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
                String filter = URLEncoder.encode(now, "UTF-8").replace("+", "%20");
                String url = supabaseUrl() + "/rest/v1/stories?select=user_id&expires_at=gt." + filter + "&order=created_at.desc&limit=50";
                HttpResult r = authenticatedRequest("GET", url, null);
                if (r.code < 200 || r.code >= 300) throw new Exception(errorMessage(r.body));
                JSONArray stories = new JSONArray(r.body);
                java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
                for (int i = 0; i < stories.length(); i++) {
                    JSONObject story = stories.optJSONObject(i);
                    String id = story == null ? "" : story.optString("user_id", "");
                    if (isUuid(id)) ids.add(id);
                }
                if (ids.isEmpty()) return;
                StringBuilder in = new StringBuilder();
                for (String id : ids) {
                    if (in.length() > 0) in.append(',');
                    in.append(id);
                }
                String profileUrl = supabaseUrl() + "/rest/v1/profiles?select=id,username,display_name&id=in.(" + in + ")&limit=50";
                HttpResult profilesResult = authenticatedRequest("GET", profileUrl, null);
                if (profilesResult.code < 200 || profilesResult.code >= 300) throw new Exception(errorMessage(profilesResult.body));
                JSONArray profiles = new JSONArray(profilesResult.body);
                java.util.HashMap<String, String> names = new java.util.HashMap<>();
                for (int i = 0; i < profiles.length(); i++) {
                    JSONObject profile = profiles.optJSONObject(i);
                    if (profile == null) continue;
                    String id = profile.optString("id", "");
                    String name = profile.optString("display_name", "").trim();
                    if (name.isEmpty()) name = profile.optString("username", "").trim();
                    if (!name.isEmpty() && isUuid(id)) names.put(id, name);
                }
                postUi(() -> {
                    for (String id : ids) {
                        String name = names.get(id);
                        if (name == null || name.isEmpty()) continue;
                        TextView bubble = new TextView(this);
                        bubble.setText("◉\n" + name);
                        bubble.setGravity(Gravity.CENTER);
                        bubble.setTextSize(11);
                        bubble.setTextColor(Color.DKGRAY);
                        bubble.setBackground(rounded(Color.WHITE, 32));
                        bubble.setOnClickListener(v -> showStories());
                        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(68), dp(68));
                        bp.setMargins(0, 0, dp(8), 0);
                        storiesBar.addView(bubble, bp);
                    }
                });
            } catch (Exception ignored) {
                // The feed remains usable when the optional story strip cannot load.
            }
        });
    }

    private void loadPosts(LinearLayout posts, boolean append){
        if (feedLoadInFlight) return;
        feedLoadInFlight = true;
        status.setText("جارٍ تحميل المنشورات...");
        final int requestedOffset = append ? feedOffset + FEED_PAGE_SIZE : 0;
        if (!submitIo(()->{
            try{
                String url=supabaseUrl()+"/rest/v1/posts?select=id,user_id,content,media_urls,media_type,likes_count,comments_count,created_at&order=created_at.desc&limit="+FEED_PAGE_SIZE+"&offset="+requestedOffset;
                HttpResult r=authenticatedRequest("GET",url,null);
                if(r.code==401){feedLoadInFlight=false;clearSecrets();postUi(this::showLogin);return;}
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                JSONArray a=new JSONArray(r.body);
                postUi(()->{
                    if(!append) posts.removeAllViews();
                    feedOffset=requestedOffset;
                    for(int i=0;i<a.length();i++){
                        try{
                            JSONObject post=a.getJSONObject(i);
                            addPostCard(posts,post);
                        }catch(Exception ignored){}
                    }
                    status.setText(a.length()+" منشورًا تم تحميلها. الصفحة الحالية تبدأ من "+(feedOffset+1)+".");
                    feedLoadInFlight = false;
                });
            }catch(Exception e){
                feedLoadInFlight = false;
                postUi(()->status.setText("تعذر تحميل المنشورات: "+safeMessage(e)));
            }
        })) {
            feedLoadInFlight = false;
        }
    }


    private String currentUserId() throws Exception {
        long now = System.currentTimeMillis();
        if (!verifiedUserId.isEmpty() && now - verifiedUserIdAt < VERIFIED_USER_CACHE_MS) return verifiedUserId;
        try {
            HttpResult r = authenticatedRequest("GET", supabaseUrl() + "/auth/v1/user", null);
            if (r.code < 200 || r.code >= 300) throw new Exception(errorMessage(r.body));
            JSONObject user = new JSONObject(r.body);
            String id = user.optString("id", "");
            if (id.isEmpty()) throw new Exception("جلسة المستخدم غير صالحة.");
            saveSecret(CURRENT_USER_ID, id);
            verifiedUserId = id;
            verifiedUserIdAt = now;
            return id;
        } catch (Exception networkError) {
            String cached = readSecret(CURRENT_USER_ID);
            if (!cached.isEmpty() && isUuid(cached)) { verifiedUserId = cached; verifiedUserIdAt = now; return cached; }
            throw networkError;
        }
    }

    private void showProfile() {
        base("الملف الشخصي");
        Button back=button("العودة"); back.setOnClickListener(v->showFeed());
        submitIo(()->{
            try {
                String id=currentUserId();
                HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/profiles?id=eq."+id+"&select=id,username,display_name,bio,avatar_url,cover_url,website,location,is_verified,is_private,followers_count,following_count,posts_count&limit=1",null);
                if(r.code<200||r.code>=300) throw new Exception(errorMessage(r.body));
                JSONArray a=new JSONArray(r.body); if(a.length()==0) throw new Exception("الملف الشخصي غير موجود.");
                JSONObject me=a.getJSONObject(0);
                postUi(()->{
                    EditText username=input("اسم المستخدم",false); username.setText(me.optString("username",""));
                    EditText name=input("الاسم الظاهر",false); name.setText(me.optString("display_name",""));
                    EditText bio=input("النبذة",false); bio.setText(me.optString("bio",""));
                    EditText location=input("الموقع",false); location.setText(me.optString("location",""));
                    EditText website=input("الموقع الإلكتروني",false); website.setText(me.optString("website",""));
                    Button save=button("حفظ التعديلات"); Button privateBtn=button(me.optBoolean("is_private",false)?"الحساب خاص — اضغط للتبديل":"الحساب عام — اضغط للتبديل");
                    Button logout=button("تسجيل الخروج");
                    logout.setOnClickListener(v->logout());
                    TextView stats=new TextView(this); stats.setText("المنشورات: "+me.optInt("posts_count")+"   المتابعون: "+me.optInt("followers_count")+"   المتابَعون: "+me.optInt("following_count")); stats.setTextColor(Color.DKGRAY); root.addView(stats);
                    final boolean[] isPrivate={me.optBoolean("is_private",false)};
                    privateBtn.setOnClickListener(v->{isPrivate[0]=!isPrivate[0]; privateBtn.setText(isPrivate[0]?"الحساب خاص — اضغط للتبديل":"الحساب عام — اضغط للتبديل");});
                    save.setOnClickListener(v->updateProfile(username.getText().toString().trim(),name.getText().toString().trim(),bio.getText().toString().trim(),location.getText().toString().trim(),website.getText().toString().trim(),isPrivate[0]));
                });
            } catch(Exception e){ postUi(()->status.setText("تعذر تحميل الملف: "+safeMessage(e))); }
        });
    }

    private void logout(){
        if (passwordResetInFlight || profileUpdateInFlight) return;
        submitIo(()->{
            try {
                String token=readSecret(ACCESS_TOKEN);
                if(!token.isEmpty() && isNetworkAvailable()) {
                    try { request("POST", authUrl()+"/auth/v1/logout", null, token); } catch(Exception ignored) { }
                }
            } finally {
                if (offlineStore != null) offlineStore.clearCache();
                clearRecoveryState();
                clearSecrets();
                postUi(this::showLogin);
            }
        });
    }

    private void updateProfile(String username,String name,String bio,String location,String website,boolean isPrivate){
        if(username.isEmpty()||name.isEmpty()){toast("اسم المستخدم والاسم الظاهر مطلوبان.");return;}
        if(username.length()>32||name.length()>100||bio.length()>2000||location.length()>120||website.length()>500){toast("بيانات الملف أطول من الحد المسموح.");return;}
        if(profileUpdateInFlight) return;
        profileUpdateInFlight=true;
        if (!submitIo(()->{
            try{
                String id=currentUserId();
                JSONObject body=new JSONObject().put("username",username).put("display_name",name).put("bio",bio.isEmpty()?JSONObject.NULL:bio).put("location",location.isEmpty()?JSONObject.NULL:location).put("website",website.isEmpty()?JSONObject.NULL:website).put("is_private",isPrivate);
                HttpResult r=authenticatedRequest("PATCH",supabaseUrl()+"/rest/v1/profiles?id=eq."+id,body.toString());
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                postUi(()->{toast(isOfflineQueued(r)?"تم حفظ تعديل الملف Offline وسيُزامن عند الاتصال.":"تم تحديث الملف الشخصي.");showProfile();});
            }catch(Exception e){postUi(()->toast("تعذر حفظ الملف: "+safeMessage(e)));}
            finally { profileUpdateInFlight=false; }
        })) {
            profileUpdateInFlight=false;
        }
    }

    private void showPeople(){
        base("المستخدمون والمتابعة"); Button back=button("العودة"); back.setOnClickListener(v->showFeed());
        EditText search=input("بحث باسم المستخدم",false); Button find=button("بحث");
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        find.setOnClickListener(v->loadPeople(list,search.getText().toString().trim())); loadPeople(list,"");
    }

    private void loadPeople(LinearLayout list,String search){
        if(peopleLoadInFlight) return;
        peopleLoadInFlight = true;
        if (!submitIo(()->{
            try{
                String normalizedSearch = normalizeUsernameSearch(search);
                String safeSearch=URLEncoder.encode(normalizedSearch,"UTF-8").replace("+","%20");
                String q=normalizedSearch.isEmpty()?"":"&username=ilike.*"+safeSearch+"*";
                HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/profiles?select=id,username,display_name,bio,is_private,followers_count&order=created_at.desc&limit=50"+q,null);
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                JSONArray a=new JSONArray(r.body); String me=currentUserId();
                postUi(()->{list.removeAllViews();for(int i=0;i<a.length();i++){try{JSONObject u=a.getJSONObject(i);if(me.equals(u.optString("id")))continue;addPerson(list,u);}catch(Exception ignored){}}status.setText(a.length()+" ملفًا متاحًا.");});
            }catch(Exception e){postUi(()->status.setText("تعذر تحميل المستخدمين: "+safeMessage(e)));}
            finally { peopleLoadInFlight = false; }
        })) {
            peopleLoadInFlight = false;
        }
    }

    private String normalizeUsernameSearch(String value){
        if(value==null)return "";
        String s=value.trim();
        if(s.length()>32) s=s.substring(0,32);
        StringBuilder out=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            if(ch=='*'||ch=='%'||ch=='_'||ch==','||ch=='('||ch==')'||ch=='\\') continue;
            if(Character.isISOControl(ch)) continue;
            out.append(ch);
        }
        return out.toString();
    }

    private void addPerson(LinearLayout list,JSONObject u){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(14,14,14,14);
        TextView t=new TextView(this);t.setText(u.optString("display_name",u.optString("username"))+"  @"+u.optString("username")+"\nمتابعون: "+u.optInt("followers_count"));t.setTextSize(17);row.addView(t);
        Button follow=buttonIn(row,"متابعة / إلغاء المتابعة"); follow.setOnClickListener(v->toggleFollow(u.optString("id"),follow)); list.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }

    private void toggleFollow(String target,Button b){
        if(!isUuid(target))return;
        synchronized(followTargetsInFlight){
            if(!followTargetsInFlight.add(target)) return;
        }
        b.setEnabled(false);
        submitIo(()->{
            try{
                String me=currentUserId();
                if(me.isEmpty()||me.equals(target)) throw new Exception("حساب المتابعة غير صالح.");
                String q=supabaseUrl()+"/rest/v1/follows?select=id&follower_id=eq."+me+"&following_id=eq."+target+"&limit=1";
                HttpResult c=authenticatedRequest("GET",q,null);
                if(c.code<200||c.code>=300)throw new Exception(errorMessage(c.body));
                JSONArray a=new JSONArray(c.body);
                HttpResult r;
                if(a.length()>0){
                    r=authenticatedRequest("DELETE",supabaseUrl()+"/rest/v1/follows?follower_id=eq."+me+"&following_id=eq."+target,null);
                }else{
                    r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/follows",new JSONObject().put("follower_id",me).put("following_id",target).toString());
                }
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                final boolean removed=a.length()>0;
                postUi(()->{b.setEnabled(true);toast(isOfflineQueued(r)?"تم حفظ تغيير المتابعة Offline وسيُزامن عند الاتصال.":(removed?"تم إلغاء المتابعة.":"تمت المتابعة."));});
            }catch(Exception e){
                postUi(()->{b.setEnabled(true);toast("تعذر تحديث المتابعة: "+safeMessage(e));});
            }finally{
                followTargetsInFlight.remove(target);
            }
        });
    }

    private void showNotifications(){
        base("الإشعارات"); Button back=button("العودة");back.setOnClickListener(v->showFeed()); ScrollView s=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);s.addView(list);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        submitIo(()->{
            try{
                String id=currentUserId();
                HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/notifications?user_id=eq."+id+"&select=id,type,actor_id,post_id,comment_id,is_read,created_at&order=created_at.desc&limit=100",null);
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                JSONArray a=new JSONArray(r.body);
                StringBuilder unread=new StringBuilder();
                for(int i=0;i<a.length();i++){
                    JSONObject n=a.optJSONObject(i);
                    if(n!=null&&!n.optBoolean("is_read",false)){
                        String nid=n.optString("id","");
                        if(isUuid(nid)){ if(unread.length()>0) unread.append(','); unread.append(nid); }
                    }
                }
                final String unreadIds=unread.toString();
                postUi(()->{
                    for(int i=0;i<a.length();i++){JSONObject n=a.optJSONObject(i);if(n==null)continue;TextView t=new TextView(this);t.setText(notificationText(n)+"\n"+n.optString("created_at"));t.setPadding(10,16,10,16);list.addView(t);}
                    status.setText(a.length()+" إشعارًا.");
                });
                if(!unreadIds.isEmpty()) markNotificationsRead(id,unreadIds);
            }catch(Exception e){postUi(()->status.setText("تعذر تحميل الإشعارات: "+safeMessage(e)));}
        });
    }

    private String notificationText(JSONObject n){String type=n.optString("type");if("like".equals(type))return "♥ لديك إعجاب جديد";if("comment".equals(type))return "💬 لديك تعليق جديد";if("follow".equals(type))return "👤 لديك متابع جديد";if("message".equals(type))return "✉ لديك رسالة جديدة";return "🔔 إشعار جديد";}
    private void markNotificationsRead(String userId,String ids){
        if(userId==null||userId.isEmpty()||ids==null||ids.isEmpty()||notificationsMarkReadInFlight)return;
        notificationsMarkReadInFlight=true;
        if (!submitIo(()->{
            try{
                HttpResult r=authenticatedRequest("PATCH",supabaseUrl()+"/rest/v1/notifications?user_id=eq."+userId+"&id=in.("+ids+")",new JSONObject().put("is_read",true).toString());
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
            }catch(Exception ignored){}
            finally{notificationsMarkReadInFlight=false;}
        })) {
            notificationsMarkReadInFlight=false;
        }
    }

    private boolean isUuid(String value){
        return value != null && value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    }

    private void showMessages(){
        base("الرسائل"); Button back=button("العودة");back.setOnClickListener(v->showFeed()); EditText recipient=input("معرّف المستخدم UUID للمستلم",false); Button open=button("فتح المحادثة");
        ScrollView s=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);s.addView(list);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        open.setOnClickListener(v->openConversation(recipient.getText().toString().trim(),list));
        loadConversations(list);
    }

    private void loadConversations(LinearLayout list){submitIo(()->{try{HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/conversations?select=id,created_at,updated_at&order=updated_at.desc&limit=30",null);if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));JSONArray a=new JSONArray(r.body);postUi(()->{for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);Button b=buttonIn(list,"محادثة "+c.optString("id"));b.setOnClickListener(v->loadMessages(c.optString("id"),list));}});}catch(Exception e){postUi(()->status.setText("تعذر تحميل المحادثات: "+safeMessage(e)));}});}

    private void openConversation(String other,LinearLayout list){if(!isUuid(other)){toast("أدخل UUID صحيحًا للمستخدم.");return;}submitIo(()->{try{String body=new JSONObject().put("p_other_user_id",other).toString();HttpResult r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/rpc/create_direct_conversation",body);if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));String id=r.body.replace("\"","").trim();postUi(()->loadMessages(id,list));}catch(Exception e){postUi(()->toast("تعذر فتح المحادثة: "+safeMessage(e)));}});}

    private void loadMessages(String conversationId,LinearLayout list){
        if(!isUuid(conversationId)){toast("معرّف المحادثة غير صالح.");return;}
        list.removeAllViews(); EditText message=input("اكتب رسالة",false); Button send=button("إرسال");
        submitIo(()->{try{String me=currentUserId();HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/messages?conversation_id=eq."+conversationId+"&select=id,sender_id,content,media_url,is_read,created_at&order=created_at.asc&limit=100",null);if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));JSONArray a=new JSONArray(r.body);postUi(()->{for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);TextView t=new TextView(this);t.setText(m.optString("sender_id").equals(me)?"أنا: "+m.optString("content"):"هو: "+m.optString("content"));t.setPadding(8,12,8,12);list.addView(t);}send.setOnClickListener(v->sendMessage(conversationId,message));});}catch(Exception e){postUi(()->status.setText("تعذر تحميل الرسائل: "+safeMessage(e)));}});
    }

    private void sendMessage(String conversationId,EditText field){
        String text=field.getText().toString().trim();
        if(text.isEmpty()||text.length()>5000){toast("الرسالة يجب أن تكون بين 1 و5000 حرف.");return;}
        if(messageSendInFlight) return;
        messageSendInFlight=true;
        field.setEnabled(false);
        if (!submitIo(()->{
            try{
                String me=currentUserId();
                HttpResult r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/messages",new JSONObject().put("conversation_id",conversationId).put("sender_id",me).put("content",text).toString());
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                postUi(()->{field.setText("");field.setEnabled(true);toast(isOfflineQueued(r)?"تم حفظ الرسالة Offline وستُرسل عند الاتصال.":"تم إرسال الرسالة.");});
            }catch(Exception e){postUi(()->{field.setEnabled(true);toast("تعذر إرسال الرسالة: "+safeMessage(e));});}
            finally { messageSendInFlight=false; }
        })) {
            messageSendInFlight=false;
            field.setEnabled(true);
        }
    }

    private void showStories(){
        base("القصص"); Button back=button("العودة");back.setOnClickListener(v->showFeed()); Button add=button("إضافة قصة من صورة/فيديو"); ScrollView s=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);s.addView(list);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        add.setOnClickListener(v->{
            clearSelectedMedia();
            pickingStoryMedia=true;
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
            startActivityForResult(i,PICK_MEDIA);
        });
        submitIo(()->{try{
            String now=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date());
            String expiresFilter=URLEncoder.encode(now,"UTF-8").replace("+","%20");
            HttpResult r=authenticatedRequest("GET",supabaseUrl()+"/rest/v1/stories?select=id,user_id,media_url,media_type,caption,views_count,expires_at,created_at&expires_at=gt."+expiresFilter+"&order=created_at.desc&limit=50",null);if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));JSONArray a=new JSONArray(r.body);postUi(()->{for(int i=0;i<a.length();i++){JSONObject st=a.optJSONObject(i);TextView t=new TextView(this);t.setText("📖 "+st.optString("media_type")+"\n"+st.optString("caption","")+"\nمشاهدات: "+st.optInt("views_count"));t.setPadding(8,16,8,16);list.addView(t);}});}catch(Exception e){postUi(()->status.setText("تعذر تحميل القصص: "+safeMessage(e)));}});
    }

    private void publishStoryFromSelectedMedia(){
        if(storyInFlight||selectedMedia==null)return;
        storyInFlight=true;
        final Uri media=selectedMedia; final String mime=selectedMime==null?"image/jpeg":selectedMime;
        if (!online) {
            if (!submitIo(()->{
                try {
                    String userId=currentUserId();
                    JSONObject queued=new JSONObject().put("user_id",userId).put("caption","");
                    if (queueOfflineMedia(OFFLINE_STORY_URL,media,mime,queued)) { clearSelectedMedia(); storyInFlight=false; postUi(()->toast("القصة محفوظة Offline وستُنشر تلقائيًا عند الاتصال.")); return; }
                } catch(Exception ignored) {}
                storyInFlight=false;
            })) {
                storyInFlight=false;
            }
            return;
        }
        if (!submitIo(()->{
            UploadedMedia uploaded=null;
            try{
                String storyUserId=currentUserId();
                uploaded=uploadMedia(media,mime,"stories");
                JSONObject body=new JSONObject().put("user_id",storyUserId)
                        .put("media_url",uploaded.url)
                        .put("media_type",mime.startsWith("video/")?"video":"image")
                        .put("caption",JSONObject.NULL);
                HttpResult r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/stories",body.toString());
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                clearSelectedMedia();
                postUi(()->{storyInFlight=false;toast("تم نشر القصة بنجاح.");showStories();});
            }catch(Exception e){
                if(uploaded!=null) deleteMedia(uploaded.path);
                // Same ambiguity rule as posts: an online attempt may already have
                // reached storage or the stories table, so never duplicate it by queueing
                // the original media after an uncertain transport failure.
                storyInFlight=false;
                postUi(()->toast("تعذر نشر القصة: "+safeMessage(e)));
            }
        })) {
            storyInFlight=false;
        }
    }

    private void addPostCard(LinearLayout posts, JSONObject post){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(10));
        card.setBackground(rounded(Color.WHITE,18));
        TextView author=new TextView(this); author.setText("●  مستخدم  ·  الآن"); author.setTextSize(14); author.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); author.setTextColor(Color.rgb(35,35,35)); card.addView(author);
        String content=post.optString("content","");
        TextView text=new TextView(this); text.setText(content.isEmpty()?"منشور بدون نص":content); text.setTextSize(16); text.setTextColor(Color.rgb(45,45,45)); text.setPadding(0,dp(10),0,dp(8)); card.addView(text);
        JSONArray media=post.optJSONArray("media_urls");
        if(media!=null&&media.length()>0){
            TextView mediaInfo=new TextView(this); mediaInfo.setText("▣  "+media.length()+" وسائط  ·  "+post.optString("media_type","media")); mediaInfo.setTextSize(14); mediaInfo.setGravity(Gravity.CENTER); mediaInfo.setTextColor(Color.rgb(70,70,70)); mediaInfo.setBackground(rounded(Color.rgb(245,245,245),14));
            mediaInfo.setPadding(0,dp(38),0,dp(38));
            mediaInfo.setOnClickListener(v->{String u=media.optString(0,""); String mt=post.optString("media_type",""); if(!u.isEmpty()) showNativeMediaViewer(u, mt);});
            card.addView(mediaInfo);
        }
        TextView counts=new TextView(this); counts.setText("♥ "+post.optInt("likes_count")+"    💬 "+post.optInt("comments_count")); counts.setTextColor(Color.GRAY); counts.setPadding(0,dp(10),0,dp(4)); card.addView(counts);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button like=buttonIn(actions,"♡ إعجاب"); Button comment=buttonIn(actions,"💬 تعليق"); Button share=buttonIn(actions,"↗ مشاركة");
        like.setOnClickListener(v->toggleLike(post.optString("id"),like,counts,post));
        comment.setOnClickListener(v->showCommentDialog(post.optString("id")));
        share.setOnClickListener(v->{
            String textToShare = content.isEmpty() ? "منشور من mr.x" : content;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
            try { startActivity(Intent.createChooser(shareIntent, "مشاركة المنشور")); }
            catch (Exception ignored) { toast("لا يوجد تطبيق متاح للمشاركة."); }
        });
        card.addView(actions);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); posts.addView(card,lp);
    }


    private void showNativeMediaViewer(String url, String mediaType){
        if(!isTrustedMediaUrl(url)){
            toast("رابط الوسائط غير موثوق.");
            return;
        }
        final boolean videoType = mediaType != null && mediaType.toLowerCase(Locale.US).startsWith("video/");
        android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView close=new TextView(this); close.setText("إغلاق"); close.setTextSize(16); close.setGravity(Gravity.CENTER); close.setPadding(0,dp(10),0,dp(10)); close.setOnClickListener(v->dialog.dismiss()); box.addView(close,new LinearLayout.LayoutParams(-1,-2));
        if(videoType){
            VideoView video=new VideoView(this);
            java.util.Map<String,String> videoHeaders=new java.util.HashMap<>();
            videoHeaders.put("android-allow-cross-domain-redirect","0");
            video.setVideoURI(Uri.parse(url), videoHeaders);
            MediaController controls=new MediaController(this); controls.setAnchorView(video); video.setMediaController(controls);
            box.addView(video,new LinearLayout.LayoutParams(-1,0,1));
            video.setOnPreparedListener(mp->{mp.setLooping(false); video.start();});
            video.setOnErrorListener((mp,what,extra)->{toast("تعذر تشغيل الفيديو داخل التطبيق."); return true;});
            dialog.setOnDismissListener(d->{try{if(video.isPlaying()) video.stopPlayback();}catch(Exception ignored){}});
        } else {
            ImageView image=new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER); box.addView(image,new LinearLayout.LayoutParams(-1,0,1));
            final Future<?>[] imageLoadTask = new Future<?>[1];
            imageLoadTask[0] = submitIoFuture(()->{
                HttpURLConnection c=null;
                try{
                    c=(HttpURLConnection)new URL(url).openConnection();
                    c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false); c.setUseCaches(false); c.setRequestProperty("Accept","image/*"); c.connect();
                    int code=c.getResponseCode();
                    if(code<200||code>=300) throw new Exception("HTTP "+code);
                    String contentType=c.getContentType();
                    if(contentType!=null && !contentType.toLowerCase(Locale.US).startsWith("image/")) throw new Exception("نوع الوسائط ليس صورة.");
                    byte[] data=readLimited(c.getInputStream(), MAX_IMAGE_PREVIEW_BYTES);
                    android.graphics.Bitmap bmp=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length);
                    if(bmp==null) throw new Exception("بيانات الصورة غير صالحة.");
                    postUi(()->{
                        if(!dialog.isShowing()) { bmp.recycle(); return; }
                        image.setImageBitmap(bmp);
                    });
                }catch(Exception e){postUi(()->{if(dialog.isShowing() && !Thread.currentThread().isInterrupted()) toast("تعذر تحميل الصورة: "+safeMessage(e));});}
                finally { if(c!=null) c.disconnect(); }
            });
            dialog.setOnDismissListener(d->{
                Future<?> task=imageLoadTask[0];
                if(task!=null) task.cancel(true);
                android.graphics.drawable.Drawable drawable=image.getDrawable();
                if(drawable instanceof android.graphics.drawable.BitmapDrawable){
                    android.graphics.Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();
                    image.setImageDrawable(null);
                    if(bitmap!=null && !bitmap.isRecycled()) bitmap.recycle();
                }
            });
        }
        dialog.setContentView(box); dialog.setOnShowListener(d->{android.view.Window w=dialog.getWindow(); if(w!=null) w.setLayout(-1,-1);}); dialog.show();
    }

    private boolean isTrustedMediaUrl(String value){
        try{
            Uri uri=Uri.parse(value);
            Uri base=Uri.parse(storageUrl());
            String path=uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null
                    || !uri.getHost().equalsIgnoreCase(base.getHost())
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || (uri.getPort()==-1 ? 443 : uri.getPort()) != (base.getPort()==-1 ? 443 : base.getPort())
                    || path==null || !path.startsWith("/storage/v1/object/public/media/")
                    || containsTraversal(path)) return false;
            // Reject encoded path traversal as well; Uri.getPath() can retain encoded
            // separators/dot segments that a downstream HTTP server may decode.
            String decoded=Uri.decode(path);
            return decoded!=null && !containsTraversal(decoded);
        }catch(Exception e){return false;}
    }

    private boolean containsTraversal(String path){
        if(path==null || path.isEmpty()) return true;
        String normalized=path.replace('\\','/');
        if(normalized.contains("%2e") || normalized.contains("%2E")
                || normalized.contains("%2f") || normalized.contains("%2F")
                || normalized.contains("%5c") || normalized.contains("%5C")) return true;
        for(String segment: normalized.split("/",-1)){
            if(".".equals(segment) || "..".equals(segment)) return true;
        }
        return normalized.contains("\\");
    }

    private Button buttonIn(LinearLayout parent,String label){
        Button b=new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.rgb(35,35,35)); b.setBackground(rounded(Color.WHITE,16)); b.setPadding(dp(5),dp(2),dp(5),dp(2)); parent.addView(b,new LinearLayout.LayoutParams(0,-2,1)); return b;
    }

    private void toggleLike(String postId, Button likeButton, TextView counts, JSONObject post){
        if(!isUuid(postId))return;
        likeButton.setEnabled(false);
        submitIo(()->{
            try{
                String uid=currentUserId();
                if(uid.isEmpty())throw new Exception("جلسة المستخدم غير صالحة.");
                String q=supabaseUrl()+"/rest/v1/likes?select=id&post_id=eq."+postId+"&user_id=eq."+uid+"&limit=1";
                HttpResult check=authenticatedRequest("GET",q,null);
                if(check.code<200||check.code>=300)throw new Exception(errorMessage(check.body));
                JSONArray existing=new JSONArray(check.body);
                HttpResult result;
                if(existing.length()>0){
                    result=authenticatedRequest("DELETE",supabaseUrl()+"/rest/v1/likes?post_id=eq."+postId+"&user_id=eq."+uid,null);
                }else{
                    JSONObject body=new JSONObject().put("post_id",postId).put("user_id",uid);
                    result=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/likes",body.toString());
                }
                if(result.code<200||result.code>=300)throw new Exception(errorMessage(result.body));
                postUi(()->loadPosts((LinearLayout)likeButton.getParent().getParent().getParent(), false));
            }catch(Exception e){postUi(()->{likeButton.setEnabled(true);toast("تعذر تحديث الإعجاب: "+safeMessage(e));});}
        });
    }

    private void showCommentDialog(String postId){
        if(postId==null||postId.isEmpty())return;
        final EditText input=new EditText(this); input.setHint("اكتب تعليقك..."); input.setMinLines(3);
        new android.app.AlertDialog.Builder(this).setTitle("تعليق").setView(input).setNegativeButton("إلغاء",null).setPositiveButton("نشر",(d,w)->createComment(postId,input.getText().toString().trim())).show();
    }

    private void createComment(String postId,String content){
        if(content.isEmpty()||content.length()>2000){toast("التعليق يجب أن يكون بين حرف واحد و2000 حرف.");return;}
        submitIo(()->{
            try{
                String uid=currentUserId(); if(uid.isEmpty())throw new Exception("جلسة المستخدم غير صالحة.");
                JSONObject body=new JSONObject().put("post_id",postId).put("user_id",uid).put("content",content);
                HttpResult r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/comments",body.toString());
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                postUi(()->{toast(isOfflineQueued(r)?"تم حفظ التعليق Offline وسيُنشر عند الاتصال.":"تم نشر التعليق.");showFeed();});
            }catch(Exception e){postUi(()->toast("تعذر نشر التعليق: "+safeMessage(e)));}
        });
    }

    private void showCreatePost(LinearLayout posts){
        clearSelectedMedia();
        base("منشور جديد"); EditText content=new EditText(this);content.setHint("اكتب منشورك...");content.setMinLines(5);root.addView(content,new LinearLayout.LayoutParams(-1,-2));
        Button pick=button("اختيار صورة/فيديو"),camera=button("التقاط صورة بالكاميرا"),publish=button("نشر"),cancel=button("إلغاء");
        pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);startActivityForResult(i,PICK_MEDIA);});
        camera.setOnClickListener(v->captureImage());
        publish.setOnClickListener(v->{ if(publishInFlight) return; publish.setEnabled(false); createPost(content.getText().toString().trim(), publish); });
        cancel.setOnClickListener(v->{clearSelectedMedia();showFeed();});
    }

    private void captureImage(){
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},9001);return;}
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"social_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        pendingCameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(pendingCameraUri==null){toast("تعذر إنشاء ملف الكاميرا.");return;}
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("output", pendingCameraUri));
        i.putExtra(MediaStore.EXTRA_OUTPUT,pendingCameraUri);
        startActivityForResult(i,CAMERA_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_MEDIA){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
                selectedMedia=data.getData();
                try { getContentResolver().takePersistableUriPermission(selectedMedia, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                selectedMime=getContentResolver().getType(selectedMedia);
                if(selectedMime==null || !(selectedMime.startsWith("image/") || selectedMime.startsWith("video/"))){
                    selectedMedia=null; selectedMime=null;
                    if(pickingStoryMedia) pickingStoryMedia=false;
                    toast("نوع الملف غير مدعوم."); return;
                }
                toast("تم اختيار الوسائط. اضغط نشر لرفعها فعليًا.");
                if(pickingStoryMedia){
                    pickingStoryMedia=false;
                    publishStoryFromSelectedMedia();
                }
            } else if (pickingStoryMedia) {
                pickingStoryMedia=false;
                clearSelectedMedia();
            }
        } else if(requestCode==CAMERA_CAPTURE){
            if(resultCode==RESULT_OK && pendingCameraUri!=null){
                selectedMedia=pendingCameraUri; selectedMime="image/jpeg"; toast("تم التقاط الصورة. اضغط نشر لرفعها فعليًا.");
            } else if(pendingCameraUri!=null){
                try{getContentResolver().delete(pendingCameraUri,null,null);}catch(Exception ignored){}
            }
            pendingCameraUri=null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==9001){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) captureImage();
            else toast("يلزم السماح بالكاميرا لالتقاط صورة.");
        }
    }

    private void createPost(String content, Button publishButton){
        if(content.isEmpty()&&selectedMedia==null){toast("اكتب محتوى أو اختر وسائط أولًا."); publishButton.setEnabled(true); return;}
        publishInFlight = true;
        final Uri media=selectedMedia; final String mime=selectedMime==null?"image/jpeg":selectedMime;
        if (!online && media != null) {
            if (!submitIo(()->{
                try {
                    String userId=currentUserId();
                    JSONObject queued=new JSONObject().put("user_id",userId).put("content",content);
                    if (queueOfflineMedia(OFFLINE_POST_URL,media,mime,queued)) { clearSelectedMedia(); publishInFlight=false; postUi(()->{publishButton.setEnabled(true); toast("المنشور والوسائط محفوظان Offline وسيتم نشرهما تلقائيًا عند الاتصال.");}); return; }
                } catch(Exception ignored) {}
                publishInFlight=false; postUi(()->publishButton.setEnabled(true));
            })) {
                publishInFlight=false;
                publishButton.setEnabled(true);
            }
            return;
        }
        status.setText("جارٍ تجهيز المنشور...");
        if (!submitIo(()->{
            UploadedMedia uploaded=null;
            try{
                String mediaType="none";
                if(media!=null){uploaded=uploadMedia(media,mime);mediaType=mime.startsWith("video/")?"video":"image";}
                String mediaUrl=uploaded==null?null:uploaded.url;
                String userId=currentUserId();
                if(userId.isEmpty()){
                    clearSecrets();
                    throw new Exception("جلسة المستخدم غير صالحة؛ سجّل الدخول مرة أخرى.");
                }
                JSONObject body=new JSONObject().put("user_id",userId).put("content",content.isEmpty()?JSONObject.NULL:content).put("media_urls",mediaUrl==null?new JSONArray():new JSONArray().put(mediaUrl)).put("media_type",mediaType);
                HttpResult r=authenticatedRequest("POST",supabaseUrl()+"/rest/v1/posts",body.toString());
                if(r.code==401){
                    clearSecrets(); throw new Exception("انتهت الجلسة؛ سجّل الدخول مرة أخرى.");
                }
                if(r.code<200||r.code>=300)throw new Exception(errorMessage(r.body));
                clearSelectedMedia(); publishInFlight = false; postUi(this::showFeed);
            }catch(Exception e){
                if(uploaded!=null) deleteMedia(uploaded.path);
                // Do not queue the original mutation after an online upload attempt.
                // The storage upload or the subsequent post request may already have
                // reached the server before the transport failure was observed.
                // Offline media is queued only by the explicit preflight-offline branch above.
                publishInFlight = false; postUi(()->{ publishButton.setEnabled(true); status.setText("فشل النشر: "+safeMessage(e)); });
            }
        })) {
            publishInFlight=false;
            publishButton.setEnabled(true);
        }
    }

    private UploadedMedia uploadMedia(Uri uri,String mime)throws Exception{
        return uploadMedia(uri,mime,"posts");
    }

    private UploadedMedia uploadMedia(Uri uri,String mime,String folder)throws Exception{
        if(uri==null)throw new Exception("لم يتم اختيار ملف.");
        String scheme = uri.getScheme();
        if(scheme==null || !("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme))) throw new Exception("مصدر الملف غير آمن.");
        if(mime==null || !(mime.startsWith("image/") || mime.startsWith("video/"))) throw new Exception("نوع الملف غير مدعوم.");
        UploadedMedia result=uploadMediaOnce(uri,mime,folder,true);
        return result;
    }

    private String mediaExtension(String mime)throws Exception{
        if(mime==null)return null;
        String m=mime.toLowerCase(Locale.US).split(";",2)[0].trim();
        if("image/jpeg".equals(m))return "jpg";
        if("image/png".equals(m))return "png";
        if("image/webp".equals(m))return "webp";
        if("image/gif".equals(m))return "gif";
        if("video/mp4".equals(m))return "mp4";
        if("video/webm".equals(m))return "webm";
        if("video/3gpp".equals(m))return "3gp";
        throw new Exception("نوع الوسائط غير مدعوم. استخدم JPG أو PNG أو WebP أو GIF أو MP4 أو WebM أو 3GP.");
    }

    private UploadedMedia uploadMediaOnce(Uri uri,String mime,String folder,boolean allowRefresh)throws Exception{
        String userId=currentUserId();
        if(userId.isEmpty())throw new Exception("جلسة غير صالحة.");
        long size=querySize(uri);
        if(size>MAX_MEDIA_BYTES)throw new Exception("حجم الملف أكبر من 50MB.");
        String ext=mediaExtension(mime);
        String safeFolder=folder==null||folder.isEmpty()?"posts":folder;
        String path=userId+"/"+safeFolder+"/"+UUID.randomUUID()+"."+ext;
        String requestRefreshToken=readSecret(REFRESH_TOKEN);
        HttpURLConnection c=null;
        boolean uploadStarted=false;
        try{
            c=(HttpURLConnection)new URL(storageUrl()+"/storage/v1/object/media/"+path).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST"); c.setConnectTimeout(20000); c.setReadTimeout(60000); c.setDoOutput(true);
            c.setRequestProperty("apikey",supabaseKey()); c.setRequestProperty("Authorization","Bearer "+readSecret(ACCESS_TOKEN));
            c.setRequestProperty("Content-Type",mime); c.setRequestProperty("x-upsert","false");
            if(size>=0)c.setFixedLengthStreamingMode(size);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=c.getOutputStream()){
                if(in==null)throw new Exception("تعذر قراءة الملف.");
                uploadStarted=true;
                byte[] buf=new byte[8192];int n;long total=0;
                while((n=in.read(buf))!=-1){
                    if(total > MAX_MEDIA_BYTES - n) throw new Exception("حجم الملف أكبر من 50MB.");
                    out.write(buf,0,n);
                    total+=n;
                }
            }
            int code=c.getResponseCode();String body=readResponse(c,code);
            if(code==401 && allowRefresh){
                if(!refreshSessionIfNeeded(requestRefreshToken))throw new Exception("انتهت الجلسة؛ سجّل الدخول مرة أخرى.");
                return uploadMediaOnce(uri,mime,folder,false);
            }
            if(code<200||code>=300)throw new Exception(errorMessage(body));
            return new UploadedMedia(storageUrl()+"/storage/v1/object/public/media/"+path,path);
        } catch(Exception e){
            if(uploadStarted) cleanupUploadedPath(path, requestRefreshToken);
            throw e;
        } finally { if(c!=null)c.disconnect(); }
    }

    private void cleanupUploadedPath(String path, String failedRefreshToken){
        if(path==null||path.isEmpty())return;
        try{
            HttpResult r=request("DELETE",storageUrl()+"/storage/v1/object/media/"+path,null,readSecret(ACCESS_TOKEN));
            if(r.code==401 && refreshSessionIfNeeded(failedRefreshToken)){
                request("DELETE",storageUrl()+"/storage/v1/object/media/"+path,null,readSecret(ACCESS_TOKEN));
            }
        }catch(Exception ignored){}
    }

    private boolean refreshSession(){
        return refreshSessionIfNeeded(null);
    }

    /**
     * Refresh-token rotation is single-use. If another request already refreshed the
     * session while this request was waiting for refreshLock, reuse the new session
     * instead of sending the old refresh token again.
     */
    private boolean refreshSessionIfNeeded(String failedRefreshToken){
        synchronized(refreshLock){
            try{
                String currentRefresh=readSecret(REFRESH_TOKEN);
                if(currentRefresh.isEmpty())return false;
                if(failedRefreshToken!=null && !failedRefreshToken.isEmpty() && !failedRefreshToken.equals(currentRefresh)) return true;
                HttpResult rr=request("POST",authUrl()+"/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",currentRefresh).toString(),null);
                if(rr.code<200||rr.code>=300)return false;
                JSONObject j=new JSONObject(rr.body);
                String a=j.optString("access_token","");
                String n=j.optString("refresh_token","");
                if(a.isEmpty()||n.isEmpty())return false;
                try {
                    saveSecret(ACCESS_TOKEN,a);
                    saveSecret(REFRESH_TOKEN,n);
                } catch (Exception secureError) {
                    clearSecrets();
                    return false;
                }
                return true;
            }catch(Exception e){return false;}
        }
    }

    private void deleteMedia(String path){
        if(path==null||path.isEmpty())return;
        submitIo(()->cleanupUploadedPath(path, readSecret(REFRESH_TOKEN)));
    }

    private long querySize(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst())return c.getLong(0);}catch(Exception ignored){}return -1;}

    /**
     * All REST/Auth calls must enter request(), which applies service routing.
     * Direct HttpURLConnection is reserved for Storage uploads and media downloads
     * because those paths need streaming/decoder-specific handling.
     */
    private HttpResult authenticatedRequest(String method,String url,String body)throws Exception{
        String token=readSecret(ACCESS_TOKEN);
        String refresh=readSecret(REFRESH_TOKEN);
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        boolean networkAvailableAtStart = isNetworkAvailable();
        online = networkAvailableAtStart;

        if (!networkAvailableAtStart && read) {
            String cached = offlineStore == null ? null : offlineStore.getCached(url, readSecret(CURRENT_USER_ID));
            if (cached != null) return new HttpResult(200, cached);
            throw new OfflineException("لا توجد شبكة ولا توجد نسخة محفوظة من هذه البيانات.");
        }

        // A write is queued only when we know the network was unavailable BEFORE
        // opening the request. If a POST/PATCH reaches the server and only its
        // response is lost, queueing it after the exception could duplicate the mutation.
        if (!networkAvailableAtStart && !read && shouldQueueOffline(method, url) && offlineStore != null) {
            String ownerUserId = readSecret(CURRENT_USER_ID);
            if (ownerUserId.isEmpty()) throw new Exception("تعذر حفظ العملية Offline قبل التحقق من حساب المستخدم.");
            long id = offlineStore.enqueue(method, url, body, ownerUserId, currentOperationId());
            if (id < 0) throw new Exception("تعذر حفظ العملية Offline: حجم الطلب أو طابور المزامنة تجاوز الحد الآمن.");
            postUi(() -> status.setText("تم الحفظ محليًا وسيتم الإرسال تلقائيًا عند عودة الإنترنت (مهمة #" + id + ")."));
            return new HttpResult(202, "{\"offline_queued\":true}", null);
        }

        try {
            HttpResult r=requestResilient(method,url,body,token);
            if(r.code==401 && !token.isEmpty() && !refresh.isEmpty() && refreshSessionIfNeeded(refresh))
                r=requestResilient(method,url,body,readSecret(ACCESS_TOKEN));
            if (read && r.code >= 200 && r.code < 300 && offlineStore != null) offlineStore.putCached(url, r.body, readSecret(CURRENT_USER_ID));
            if (!read && r.code >= 200 && r.code < 300 && offlineStore != null) scheduleSync(0);
            return r;
        } catch (Exception e) {
            if (read) {
                String cached = offlineStore == null ? null : offlineStore.getCached(url, readSecret(CURRENT_USER_ID));
                if (cached != null) return new HttpResult(200, cached);
            }
            if (!isNetworkAvailable()) online = false;
            // Do not enqueue here: for a write request the server may already have
            // accepted the mutation before the client lost the response.
            throw e;
        }
    }

    private boolean isOfflineQueued(HttpResult r) { return r != null && r.code == 202 && r.body != null && r.body.contains("offline_queued"); }

    private boolean shouldQueueOffline(String method, String url) {
        if (online) return false;
        if (url == null) return false;
        String lower=url.toLowerCase(Locale.US);
        if (lower.contains("/auth/v1/") || lower.contains("/storage/v1/") || lower.contains("/rpc/")) return false;
        return "POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private static final class OfflineException extends Exception { OfflineException(String m){super(m);} }

    /**
     * Retries only idempotent operations after transient transport/server throttling.
     * POST/PATCH are intentionally excluded to prevent duplicate mutations when a
     * server processes a request but the client loses the response.
     */
    private HttpResult requestResilient(String method,String url,String body,String token)throws Exception{
        boolean retryableMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
        HttpResult last = null;
        Exception lastError = null;
        for(int attempt=0; attempt<=TRANSIENT_RETRY_COUNT; attempt++){
            try {
                last = request(method,url,body,token);
                lastError = null;
                if(!retryableMethod || !isTransientHttpCode(last.code) || attempt==TRANSIENT_RETRY_COUNT) break;
                long delay = retryDelayMs(last,attempt);
                try { TimeUnit.MILLISECONDS.sleep(delay); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new Exception("تم إلغاء الاتصال."); }
            } catch (Exception e) {
                lastError=e;
                if(!retryableMethod || !isReadReplicaRequest(method,url) || attempt==TRANSIENT_RETRY_COUNT) throw e;
                try { TimeUnit.MILLISECONDS.sleep(Math.min(TRANSIENT_RETRY_MAX_MS,TRANSIENT_RETRY_BASE_MS*(1L << Math.min(attempt,3)))); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new Exception("تم إلغاء الاتصال."); }
            }
        }
        if(lastError!=null) throw lastError;
        if(last!=null && retryableMethod && isReadReplicaRequest(method,url) && isTransientHttpCode(last.code))
            return requestDirect(method,readFallbackUrl(url),body,token);
        return last;
    }

    private boolean isReadReplicaRequest(String method,String url){
        return services.isReadReplica(method, url);
    }

    private String readFallbackUrl(String url){
        return services.fallbackWrite(url);
    }

    private boolean isTransientHttpCode(int code){
        return code==408 || code==429 || code==500 || code==502 || code==503 || code==504;
    }

    private long retryDelayMs(HttpResult response,int attempt){
        long fallback = TRANSIENT_RETRY_BASE_MS * (1L << Math.min(attempt,3));
        long retryAfterMs = parseRetryAfterMs(response==null?null:response.retryAfter);
        long delay = retryAfterMs>0 ? retryAfterMs : fallback;
        return Math.min(TRANSIENT_RETRY_MAX_MS,Math.max(250L,delay));
    }

    private long parseRetryAfterMs(String value){
        if(value==null||value.trim().isEmpty())return -1L;
        try{
            long seconds=Long.parseLong(value.trim());
            if(seconds<0)return -1L;
            return Math.min(TRANSIENT_RETRY_MAX_MS,seconds*1000L);
        }catch(Exception ignored){return -1L;}
    }

    private boolean isTrustedEndpoint(String value){
        return services.trusts(value);
    }

    private HttpResult request(String method,String url,String body,String token)throws Exception{
        if(!isTrustedEndpoint(url))throw new Exception("وجهة اتصال غير موثوقة.");
        String routedUrl = routeServiceEndpoint(method, url);
        if(!isTrustedEndpoint(routedUrl))throw new Exception("وجهة اتصال غير موثوقة.");
        return requestDirect(method,routedUrl,body,token);
    }

    private HttpResult requestDirect(String method,String routedUrl,String body,String token)throws Exception{
        if(!isTrustedEndpoint(routedUrl))throw new Exception("وجهة اتصال غير موثوقة.");
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(routedUrl).openConnection();
            // Never follow redirects automatically: a redirect must not carry the
            // Supabase API key/session bearer token to a different origin.
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod(method);c.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);c.setReadTimeout(NETWORK_READ_TIMEOUT_MS);c.setUseCaches(false);
            c.setRequestProperty("apikey",supabaseKey());
            if(token!=null && !token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestProperty("X-MRX-Operation-ID", currentOperationId());
            c.setRequestProperty("X-Request-ID", UUID.randomUUID().toString());
            c.setRequestProperty("Accept","application/json");
            if(body!=null){
                byte[] payload=body.getBytes(StandardCharsets.UTF_8);
                if(payload.length>MAX_REQUEST_BODY_BYTES) throw new Exception("حجم الطلب أكبر من الحد المسموح.");
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json;charset=UTF-8");c.setFixedLengthStreamingMode(payload.length);try(OutputStream os=c.getOutputStream()){os.write(payload);}
            }
            int code=c.getResponseCode();String out=readResponse(c,code);String retryAfter=c.getHeaderField("Retry-After");return new HttpResult(code,out,retryAfter);
        } finally { if(c!=null)c.disconnect(); }
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        if (input == null) throw new Exception("تعذر قراءة الملف.");
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) != -1) {
                if (n > maxBytes - total) throw new Exception("الملف أكبر من الحد المسموح.");
                out.write(buffer, 0, n);
                total += n;
            }
            return out.toByteArray();
        }
    }

    private String readResponse(HttpURLConnection c,int code)throws Exception{
        InputStream stream=code>=400?c.getErrorStream():c.getInputStream();
        if(stream==null)return "";
        try(InputStream in=stream; BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            StringBuilder b=new StringBuilder(); String line; int bytes=0;
            while((line=br.readLine())!=null){
                bytes+=line.getBytes(StandardCharsets.UTF_8).length+1;
                if(bytes>MAX_RESPONSE_BYTES) throw new Exception("استجابة الخادم كبيرة جدًا.");
                b.append(line);
            }
            return b.toString();
        }
    }
    private String errorMessage(String body){try{JSONObject o=new JSONObject(body);String m=o.optString("msg","");if(m.isEmpty())m=o.optString("message","");if(m.isEmpty())m=o.optString("error_description","");return m.isEmpty()?body:m;}catch(Exception e){return body==null||body.isEmpty()?"خطأ غير معروف":body;}}
    private String safeMessage(Exception e){String s=e.getMessage();return s==null||s.isEmpty()?e.getClass().getSimpleName():s;}
    private void postUi(Runnable r){
        if(r==null || isFinishing() || isDestroyed()) return;
        main.post(()->{
            if(isFinishing() || isDestroyed()) return;
            r.run();
        });
    }
    private void toast(String t){if(isFinishing() || isDestroyed()) return; main.post(()->{if(isFinishing() || isDestroyed()) return; Toast.makeText(this,t,Toast.LENGTH_LONG).show();});}
    /**
     * Bounded executor submissions must never crash the UI thread when the queue is full.
     * Rejection is surfaced to the user instead of silently dropping work or running it on UI.
     */
    private boolean submitIo(Runnable task){
        if(task==null || isFinishing() || isDestroyed()) return false;
        try {
            appExecutors.io().execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            postUi(() -> {
                if(status != null) status.setText("التطبيق مشغول حاليًا. أعد المحاولة بعد لحظات.");
            });
            return false;
        }
    }

    /** Same bounded rejection handling as submitIo(), but retains Future cancellation for media previews. */
    private Future<?> submitIoFuture(Runnable task){
        if(task==null || isFinishing() || isDestroyed()) return null;
        try {
            return appExecutors.io().submit(task);
        } catch (RejectedExecutionException e) {
            postUi(() -> {
                if(status != null) status.setText("التطبيق مشغول حاليًا. أعد المحاولة بعد لحظات.");
            });
            return null;
        }
    }

    /**
     * API 23+ uses a native AES key in Android Keystore. API 21-22 do not expose
     * KeyGenParameterSpec, so they use an Android Keystore RSA key to wrap a
     * randomly generated AES-128 key. This preserves the minSdk=21 contract
     * without storing the session-encryption key in plaintext preferences.
     */
    private void ensureKeystoreKey(){
        try{
            KeyStore ks=KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if(android.os.Build.VERSION.SDK_INT>=23){
                if(!ks.containsAlias(KEY_ALIAS)){
                    KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");
                    kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build());
                    kg.generateKey();
                }
            }else{
                ensureLegacyWrappedKey(ks);
            }
        }catch(Exception ignored){}
    }

    private void ensureLegacyWrappedKey(KeyStore ks)throws Exception{
        if(!ks.containsAlias(LEGACY_RSA_KEY_ALIAS)){
            KeyPairGenerator kpg=KeyPairGenerator.getInstance("RSA",KEYSTORE);
            java.util.Calendar start=java.util.Calendar.getInstance();
            java.util.Calendar end=java.util.Calendar.getInstance();
            end.add(java.util.Calendar.YEAR,25);
            android.security.KeyPairGeneratorSpec spec=new android.security.KeyPairGeneratorSpec.Builder(this)
                    .setAlias(LEGACY_RSA_KEY_ALIAS)
                    .setSubject(new X500Principal("CN=mr.x"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .setKeySize(2048)
                    .build();
            kpg.initialize(spec);
            kpg.generateKeyPair();
        }
        if(prefs.getString(LEGACY_WRAPPED_AES,"").isEmpty()){
            KeyGenerator kg=KeyGenerator.getInstance("AES");
            kg.init(128);
            SecretKey aes=kg.generateKey();
            Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.ENCRYPT_MODE,ks.getCertificate(LEGACY_RSA_KEY_ALIAS).getPublicKey());
            String wrapped=Base64.encodeToString(rsa.doFinal(aes.getEncoded()),Base64.NO_WRAP);
            prefs.edit().putString(LEGACY_WRAPPED_AES,wrapped).apply();
        }
    }

    private SecretKey secretKey()throws Exception{
        KeyStore ks=KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if(android.os.Build.VERSION.SDK_INT>=23){
            if(!ks.containsAlias(KEY_ALIAS)){
                ensureKeystoreKey();
            }
            KeyStore.Entry entry=ks.getEntry(KEY_ALIAS,null);
            if(!(entry instanceof KeyStore.SecretKeyEntry)) throw new Exception("مفتاح الجلسة غير متاح.");
            return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        }
        ensureLegacyWrappedKey(ks);
        String wrapped=prefs.getString(LEGACY_WRAPPED_AES,"");
        if(wrapped.isEmpty()) throw new Exception("مفتاح الجلسة القديم غير متاح.");
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE,ks.getKey(LEGACY_RSA_KEY_ALIAS,null));
        byte[] raw=rsa.doFinal(Base64.decode(wrapped,Base64.NO_WRAP));
        return new SecretKeySpec(raw,"AES");
    }
    private void saveSecret(String name,String value){
        try{
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,secretKey());
            byte[] iv=c.getIV(),data=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            prefs.edit().putString(name,Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP)).apply();
        }catch(Exception e){
            prefs.edit().remove(name).apply();
            throw new IllegalStateException("تعذر تأمين جلسة المستخدم.",e);
        }
    }
    private String readSecret(String name){String stored=prefs.getString(name,"");if(stored.isEmpty())return "";try{String[] p=stored.split(":",2);if(p.length!=2)return "";Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secretKey(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){prefs.edit().remove(name).apply();return "";}}
    private void clearSecrets(){prefs.edit().remove(ACCESS_TOKEN).remove(REFRESH_TOKEN).remove(CURRENT_USER_ID).apply(); verifiedUserId=""; verifiedUserIdAt=0L;}
    private void clearSelectedMedia(){
        if(pendingCameraUri!=null){ try{getContentResolver().delete(pendingCameraUri,null,null);}catch(Exception ignored){} }
        selectedMedia=null; selectedMime=null; pendingCameraUri=null;
    }

    private static final class UploadedMedia{final String url;final String path;UploadedMedia(String u,String p){url=u;path=p;}}
    private static final class HttpResult{final int code;final String body;final String retryAfter;HttpResult(int c,String b){this(c,b,null);}HttpResult(int c,String b,String r){code=c;body=b;retryAfter=r;}}
}
