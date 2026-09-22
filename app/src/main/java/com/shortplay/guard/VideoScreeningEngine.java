package com.shortplay.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.util.Locale;

/**
 * Lightweight pre-playback eligibility gate.
 *
 * The duration check is strict and authoritative. Personal-video eligibility
 * uses the MediaStore camera-folder provenance signal. Frame heuristics that
 * previously caused ordinary camera videos to be falsely blocked have been
 * removed from the blocking path; a future ML classifier can be added without
 * making normal personal videos fail because of skin tones, lighting, or cuts.
 */
final class VideoScreeningEngine {
    static final int VERSION = 4;

    static final class Result {
        final boolean allowed;
        final String reason;
        Result(boolean a, String r) { allowed = a; reason = r; }
    }

    private static String key(Uri uri, long date, long limit) {
        return "screen_" + VERSION + "_" + uri.toString().hashCode()
                + "_" + date + "_" + limit;
    }

    static Result screen(Context context, Uri uri, long storeDuration, String location,
                         String name, long date, int width, int height, long limit) {
        SharedPreferences cache = context.getSharedPreferences("screen_cache", Context.MODE_PRIVATE);
        String k = key(uri, date, limit);
        String cached = cache.getString(k, null);
        if ("allow".equals(cached)) return new Result(true, "");
        if ("block_duration".equals(cached))
            return new Result(false, "Blocked: the verified video duration exceeds the limit.");
        if ("block_location".equals(cached))
            return new Result(false, "Blocked: this video is not in the phone camera folder.");

        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(context, uri);
            String raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long realDuration = raw == null ? -1 : Long.parseLong(raw);

            if (realDuration <= 0 || realDuration > limit) {
                cache.edit().putString(k, "block_duration").apply();
                return new Result(false, "Blocked: the verified video duration exceeds " + format(limit) + ".");
            }

            String p = location == null ? "" : location.toLowerCase(Locale.US);
            boolean cameraPath = p.contains("dcim") && p.contains("camera");

            if (!cameraPath) {
                cache.edit().putString(k, "block_location").apply();
                return new Result(false, "Blocked: this video is not in the phone camera folder.");
            }

            // Conservative provenance heuristic. Require multiple independent
            // indicators before rejecting something that lives in DCIM/Camera.
            String bitrateRaw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String frameRateRaw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            String mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            long bitrate = parseLong(bitrateRaw, 0);
            float frameRate = parseFloat(frameRateRaw, 0f);
            long pixels = (long) width * (long) height;
            int signals = 0;
            if (pixels >= 3840L * 2160L) signals++;
            if (bitrate >= 35_000_000L) signals++;
            if (frameRate >= 59f) signals++;
            if (mime != null && !mime.toLowerCase(Locale.US).startsWith("video/")) signals++;
            if (signals >= 2) {
                cache.edit().putString(k, "block_professional").apply();
                return new Result(false, "Blocked: this video does not look like a normal phone-camera recording.");
            }

            cache.edit().putString(k, "allow").apply();
            return new Result(true, "");
        } catch (Exception e) {
            return new Result(false, "Blocked: the video could not be safely verified.");
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); }
        catch (Exception ignored) { return fallback; }
    }

    private static float parseFloat(String s, float fallback) {
        try { return s == null ? fallback : Float.parseFloat(s); }
        catch (Exception ignored) { return fallback; }
    }

    private static String format(long ms) {
        long sec = Math.max(0, ms / 1000), min = sec / 60, h = min / 60;
        if (h > 0) return h + "h " + (min % 60) + "m";
        return min + "m";
    }
}