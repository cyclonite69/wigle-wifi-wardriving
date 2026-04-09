package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.BuildConfig;

/**
 * Utility class that holds the WiGLE URLs used throughout the app
 */
public class UrlConfig {
    public static final String API_DOMAIN = "api.wigle.net";
    public static final String API_HOST = "https://" + API_DOMAIN; /*+ ":" + API_PORT*/;

    public static final String CSV_TRANSID_URL_STEM = API_HOST+"/api/v2/file/csv/";
    // form auth
    public static final String TOKEN_URL = API_HOST+"/api/v2/activate";

    // no auth
    public static final String SITE_STATS_URL = API_HOST+"/api/v2/stats/site";
    public static final String RANK_STATS_URL = API_HOST+"/api/v2/stats/standings";
    public static final String NEWS_URL = API_HOST+"/api/v2/news/latest";

    // optional auth
    public static final String FILE_POST_URL = API_HOST+"/api/v2/file/upload";

    // ShadowCheck S3 import pipeline URL
    public static final String SHADOWCHECK_POST_URL = BuildConfig.SHADOWCHECK_POST_URL;
    public static final String SHADOWCHECK_API_KEY = BuildConfig.SHADOWCHECK_API_KEY;

    private static final String SHADOWCHECK_INGEST_PATH = "/api/v1/ingest";
    private static final String SHADOWCHECK_REQUEST_UPLOAD_PATH = SHADOWCHECK_INGEST_PATH + "/request-upload";
    private static final String SHADOWCHECK_COMPLETE_PATH = SHADOWCHECK_INGEST_PATH + "/complete";

    // api token auth
    public static final String UPLOADS_STATS_URL = API_HOST+"/api/v2/file/transactions";
    public static final String USER_STATS_URL = API_HOST+"/api/v2/stats/user";
    public static final String OBSERVED_URL = API_HOST+"/api/v2/network/mine";
    public static final String KML_TRANSID_URL_STEM = API_HOST+"/api/v2/file/kml/";
    public static final String SEARCH_WIFI_URL = API_HOST+"/api/v2/network/search";
    public static final String SEARCH_CELL_URL = API_HOST+"/api/v2/cell/search";
    public static final String SEARCH_BT_URL = API_HOST+"/api/v2/bluetooth/search";

    public static final String WIGLE_BASE_URL = "https://wigle.net";

    // registration web view
    public static final String REG_URL = "https://wigle.net/register";

    public static boolean hasShadowCheckIngestConfig() {
        return !getShadowCheckIngestBaseUrl().isEmpty();
    }

    public static String getShadowCheckRequestUploadUrl() {
        final String baseUrl = getShadowCheckIngestBaseUrl();
        if (baseUrl.isEmpty()) {
            return SHADOWCHECK_POST_URL;
        }
        return baseUrl + "/request-upload";
    }

    public static String getShadowCheckCompleteUrl() {
        final String baseUrl = getShadowCheckIngestBaseUrl();
        if (baseUrl.isEmpty()) {
            return SHADOWCHECK_POST_URL;
        }
        return baseUrl + "/complete";
    }

    private static String getShadowCheckIngestBaseUrl() {
        String configured = SHADOWCHECK_POST_URL == null ? "" : SHADOWCHECK_POST_URL.trim();
        if (configured.isEmpty()) {
            return "";
        }
        configured = trimTrailingSlash(configured);
        if (configured.endsWith(SHADOWCHECK_REQUEST_UPLOAD_PATH)) {
            return configured.substring(0, configured.length() - "/request-upload".length());
        }
        if (configured.endsWith(SHADOWCHECK_COMPLETE_PATH)) {
            return configured.substring(0, configured.length() - "/complete".length());
        }
        if (configured.endsWith(SHADOWCHECK_INGEST_PATH)) {
            return configured;
        }
        return "";
    }

    private static String trimTrailingSlash(final String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

}
