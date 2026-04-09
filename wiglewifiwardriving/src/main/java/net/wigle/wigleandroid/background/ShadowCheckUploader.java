package net.wigle.wigleandroid.background;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.BuildConfig;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Upload the database to ShadowCheck using the 3-step presigned URL pattern.
 */
public class ShadowCheckUploader extends AbstractProgressApiRequest {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType SQLITE_MEDIA_TYPE = MediaType.parse("application/x-sqlite3");
    private static final String TEST_TRUST_MODE = "test_untrusted";
    private static final String DEFAULT_SOURCE_TAG = "android_shadowcheck_test";

    private static final class UploadSession {
        private final String uploadUrl;
        private final String s3Key;
        private final String uploadId;

        private UploadSession(final String uploadUrl, final String s3Key, final String uploadId) {
            this.uploadUrl = uploadUrl;
            this.s3Key = s3Key;
            this.uploadId = uploadId;
        }
    }

    public ShadowCheckUploader(final FragmentActivity context, final DatabaseHelper dbHelper, final ApiListener listener) {
        super(context, dbHelper, "ShadowUL", null, UrlConfig.getShadowCheckRequestUploadUrl(), false,
                false, false, false,
                AbstractApiRequest.REQUEST_POST, listener, true);
    }

    @Override
    protected void subRun() throws WiGLEAuthException {
        try {
            doUpload();
        } catch ( final InterruptedException ex ) {
            Logging.info( "shadowcheck upload interrupted" );
        } catch ( final Throwable throwable ) {
            MainActivity.writeError( Thread.currentThread(), throwable, context );
            throw new RuntimeException( "ShadowCheckUploader throwable: " + throwable, throwable );
        } finally {
            if (listener != null) {
                listener.requestComplete(null, false);
            }
        }
    }

    private void doUpload() throws InterruptedException {
        final Bundle bundle = new Bundle();
        sendBundledMessage( Status.UPLOADING.ordinal(), bundle );

        if (!UrlConfig.hasShadowCheckIngestConfig()) {
            failUpload(bundle, context.getString(R.string.status_fail) + ": SHADOWCHECK_POST_URL must target /api/v1/ingest or /api/v1/ingest/request-upload");
            return;
        }

        final File dbFile = dbHelper.getDbFile();
        if (dbFile == null || !dbFile.exists()) {
            failUpload(bundle, "DB file not found");
            return;
        }

        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final String caseId = prefs.getString(PreferenceKeys.PREF_CASE_ID, "");
        try {
            final UploadSession session = requestUploadSession(dbFile, caseId);
            putToPresignedUrl(session, dbFile);
            final JSONObject completionResponse = completeUpload(session, dbFile, caseId);
            bundle.putString(BackgroundGuiHandler.TRANSIDS, formatSuccessReference(session, completionResponse));
            sendBundledMessage(Status.SUCCESS.ordinal(), bundle);
        } catch (final Exception ex) {
            Logging.error("ShadowCheck upload failed: " + ex, ex);
            failUpload(bundle, ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    private UploadSession requestUploadSession(final File dbFile, final String caseId) throws Exception {
        final JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("fileName", dbFile.getName());
        jsonRequest.put("filesize", dbFile.length());
        if (!caseId.isEmpty()) {
            jsonRequest.put("case_id", caseId);
        }

        final Request request = new Request.Builder()
                .url(UrlConfig.getShadowCheckRequestUploadUrl())
                .addHeader("Authorization", "Bearer " + UrlConfig.SHADOWCHECK_API_KEY)
                .post(RequestBody.create(jsonRequest.toString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload request failed: HTTP " + response.code() + " " + response.message());
            }
            final String responseBody = getRequiredBody(response);
            final JSONObject jsonResponse = new JSONObject(responseBody);
            return new UploadSession(
                    jsonResponse.getString("uploadUrl"),
                    jsonResponse.getString("s3Key"),
                    jsonResponse.optString("uploadId", "")
            );
        }
    }

    private void putToPresignedUrl(final UploadSession session, final File dbFile) throws IOException {
        final RequestBody putBody = RequestBody.create(dbFile, SQLITE_MEDIA_TYPE);
        final CountingRequestBody countingBody = new CountingRequestBody(putBody, (bytesWritten, contentLength) -> {
            if (contentLength > 0L) {
                final int progress = (int) ((bytesWritten * 1000L) / contentLength);
                getHandler().sendEmptyMessage(BackgroundGuiHandler.WRITING_PERCENT_START + progress);
            }
        });

        final Request putRequest = new Request.Builder()
                .url(session.uploadUrl)
                .addHeader("Content-Type", "application/x-sqlite3")
                .put(countingBody)
                .build();

        try (Response response = new OkHttpClient().newCall(putRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("S3 upload failed: HTTP " + response.code() + " " + response.message());
            }
        }
    }

    private JSONObject completeUpload(final UploadSession session, final File dbFile, final String caseId) throws Exception {
        final JSONObject completionRequest = new JSONObject();
        completionRequest.put("uploadId", session.uploadId);
        completionRequest.put("s3Key", session.s3Key);
        completionRequest.put("sourceTag", caseId.isEmpty() ? DEFAULT_SOURCE_TAG : caseId);
        completionRequest.put("deviceModel", Build.MODEL);
        completionRequest.put("deviceId", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        completionRequest.put("osVersion", Build.VERSION.RELEASE);
        completionRequest.put("appVersion", getAppVersion());
        completionRequest.put("trustMode", TEST_TRUST_MODE);
        completionRequest.put("operatorLabel", "manual-s22-test");
        completionRequest.put("deviceLabel", Build.MANUFACTURER + " " + Build.MODEL);
        completionRequest.put("manualBackupConfirmed", false);

        final int batteryLevel = getBatteryLevel();
        if (batteryLevel >= 0) {
            completionRequest.put("batteryLevel", batteryLevel);
        }

        completionRequest.put("storageFreeGb", dbFile.getUsableSpace() / (1024d * 1024d * 1024d));

        final JSONObject extraMetadata = new JSONObject();
        extraMetadata.put("caseId", caseId);
        extraMetadata.put("fileName", dbFile.getName());
        extraMetadata.put("brand", Build.BRAND);
        extraMetadata.put("sdkInt", Build.VERSION.SDK_INT);
        extraMetadata.put("securityPatch", Build.VERSION.SECURITY_PATCH);
        extraMetadata.put("buildFingerprint", Build.FINGERPRINT);
        extraMetadata.put("hardware", Build.HARDWARE);
        extraMetadata.put("deviceCodename", Build.DEVICE);
        extraMetadata.put("bootloader", Build.BOOTLOADER);
        extraMetadata.put("packageName", context.getPackageName());
        extraMetadata.put("buildFlavor", BuildConfig.FLAVOR);
        extraMetadata.put("buildType", BuildConfig.BUILD_TYPE);
        extraMetadata.put("provenanceNote", "Dry run from Android collector; quarantine until verified");
        completionRequest.put("extraMetadata", extraMetadata);

        final Request request = new Request.Builder()
                .url(UrlConfig.getShadowCheckCompleteUrl())
                .addHeader("Authorization", "Bearer " + UrlConfig.SHADOWCHECK_API_KEY)
                .post(RequestBody.create(completionRequest.toString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload completion failed: HTTP " + response.code() + " " + response.message());
            }
            return new JSONObject(getRequiredBody(response));
        }
    }

    private int getBatteryLevel() {
        final MainActivity mainActivity = MainActivity.getMainActivity();
        if (mainActivity == null) {
            return -1;
        }
        final BatteryLevelReceiver receiver = mainActivity.getBatteryLevelReceiver();
        if (receiver == null) {
            return -1;
        }
        return receiver.getBatteryLevel();
    }

    private String getAppVersion() {
        try {
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception ex) {
            Logging.warn("Unable to determine app version: " + ex);
            return "";
        }
    }

    private String formatSuccessReference(final UploadSession session, final JSONObject completionResponse) {
        final String dbId = completionResponse.optString("dbId", "");
        if (!dbId.isEmpty()) {
            return "dbId=" + dbId;
        }
        if (!session.uploadId.isEmpty()) {
            return "uploadId=" + session.uploadId;
        }
        return session.s3Key;
    }

    private String getRequiredBody(final Response response) throws IOException {
        if (response.body() == null) {
            throw new IOException("Empty response body");
        }
        return response.body().string();
    }

    private void failUpload(final Bundle bundle, final String errorMessage) {
        bundle.putString(BackgroundGuiHandler.ERROR, errorMessage);
        sendBundledMessage(Status.FAIL.ordinal(), bundle);
    }
}
