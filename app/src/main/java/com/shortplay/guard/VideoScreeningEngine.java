package com.shortplay.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.util.Locale;

/**
 * Lightweight, on-device eligibility gate.
 *
 * It deliberately runs before VideoView is created. It samples a handful of
 * low-resolution frames, checks the real stream duration, and combines:
 *  - camera-folder provenance
 *  - scene-change frequency
 *  - overlay/text-like edge density
 *  - a conservative skin-pixel safety heuristic
 *
 * This is a heuristic gate, not a forensic guarantee. Results are cached by
 * media URI + modification time + duration-limit so repeat opens are cheap.
 */
final class VideoScreeningEngine {
    static final int VERSION = 2;
    private static final int SAMPLE_COUNT = 6;
    private static final int W = 96, H = 54;

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
        if ("block_content".equals(cached))
            return new Result(false, "Blocked: this video did not pass the personal-video safety check.");
        if ("block_adult".equals(cached))
            return new Result(false, "Blocked: the video did not pass the local content-safety check.");

        MediaMetadataRetriever r = new MediaMetadataRetriever();
        Bitmap previous = null;
        int sceneCuts = 0;
        double sceneScore = 0;
        double maxSkin = 0;
        int skinFrames = 0;
        double overlayScore = 0;
        long realDuration = -1;

        try {
            r.setDataSource(context, uri);

            String raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (raw != null) realDuration = Long.parseLong(raw);
            if (realDuration <= 0 || realDuration > limit) {
                cache.edit().putString(k, "block_duration").apply();
                return new Result(false, "Blocked: the verified video duration exceeds " + format(limit) + ".");
            }

            // A phone camera normally writes to DCIM/Camera. This is a strong
            // provenance signal but not cryptographic proof.
            boolean cameraPath = location != null
                    && location.toLowerCase(Locale.US).contains("dcim")
                    && location.toLowerCase(Locale.US).contains("camera");

            for (int i = 0; i < SAMPLE_COUNT; i++) {
                long atUs = Math.max(0L, (realDuration * i / Math.max(1, SAMPLE_COUNT - 1)) * 1000L);
                Bitmap full = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (full == null) continue;
                Bitmap b = Bitmap.createScaledBitmap(full, W, H, true);
                if (b != full) full.recycle();

                double skin = skinRatio(b);
                maxSkin = Math.max(maxSkin, skin);
                if (skin > .34) skinFrames++;

                overlayScore += overlayDensity(b);

                if (previous != null) {
                    double d = difference(previous, b);
                    sceneScore += d;
                    if (d > .28) sceneCuts++;
                    previous.recycle();
                }
                previous = b;
            }

            if (previous != null) previous.recycle();

            double avgScene = sceneScore / Math.max(1, SAMPLE_COUNT - 1);
            double avgOverlay = overlayScore / SAMPLE_COUNT;

            // Conservative local safety screen. It only blocks when a large
            // skin-pixel ratio persists in multiple samples; this avoids using
            // a single frame as a definitive adult-content decision.
            if (skinFrames >= 4 && maxSkin > .46) {
                cache.edit().putString(k, "block_adult").apply();
                return new Result(false, "Blocked: the video did not pass the local content-safety check.");
            }

            // Outside-content heuristic. Fast scene changes plus persistent
            // overlay-like edges are characteristic of edited clips. Videos
            // outside the normal camera folder are treated more strictly.
            boolean editedSignals = sceneCuts >= 3 || (sceneCuts >= 2 && avgOverlay > .19);
            boolean suspiciousNonCamera = !cameraPath && (editedSignals || avgScene > .18 || avgOverlay > .24);

            // If it is not in a normal camera folder, require a clearly stable,
            // camera-like stream rather than automatically allowing it.
            if (!cameraPath && suspiciousNonCamera) {
                cache.edit().putString(k, "block_content").apply();
                return new Result(false, "Blocked: this video does not look like a personal camera recording.");
            }

            // Camera-folder clips still get the frame screen; edited material
            // with very frequent cuts is rejected even when its path is forged.
            if (cameraPath && sceneCuts >= 5 && avgOverlay > .22) {
                cache.edit().putString(k, "block_content").apply();
                return new Result(false, "Blocked: this video does not look like a personal camera recording.");
            }

            cache.edit().putString(k, "allow").apply();
            return new Result(true, "");
        } catch (Exception e) {
            return new Result(false, "Blocked: the video could not be safely screened.");
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private static double difference(Bitmap a, Bitmap b) {
        int[] pa = new int[W * H], pb = new int[W * H];
        a.getPixels(pa, 0, W, 0, 0, W, H);
        b.getPixels(pb, 0, W, 0, 0, W, H);
        long total = 0;
        for (int i = 0; i < pa.length; i++) {
            total += Math.abs(Color.red(pa[i]) - Color.red(pb[i]));
            total += Math.abs(Color.green(pa[i]) - Color.green(pb[i]));
            total += Math.abs(Color.blue(pa[i]) - Color.blue(pb[i]));
        }
        return total / (double)(pa.length * 765);
    }

    private static double skinRatio(Bitmap b) {
        int[] p = new int[W * H];
        b.getPixels(p, 0, W, 0, 0, W, H);
        int skin = 0;
        for (int x : p) {
            int r = Color.red(x), g = Color.green(x), bl = Color.blue(x);
            int max = Math.max(r, Math.max(g, bl));
            int min = Math.min(r, Math.min(g, bl));
            if (r > 80 && g > 35 && bl > 20 && r > g * 1.18
                    && g > bl * 1.10 && (max - min) > 35) skin++;
        }
        return skin / (double)p.length;
    }

    private static double overlayDensity(Bitmap b) {
        int[] p = new int[W * H];
        b.getPixels(p, 0, W, 0, 0, W, H);
        int edges = 0, total = 0;
        // Text/subtitles tend to create many high-contrast transitions in
        // narrow top/bottom bands. This is intentionally only a weak signal.
        for (int y = 3; y < H - 3; y++) {
            if (y > H * .22 && y < H * .70) continue;
            for (int x = 1; x < W - 1; x++) {
                int c = luminance(p[y * W + x]);
                int l = luminance(p[y * W + x - 1]);
                int rr = luminance(p[y * W + x + 1]);
                total++;
                if (Math.abs(c-l) > 70 || Math.abs(c-rr) > 70) edges++;
            }
        }
        return total == 0 ? 0 : edges / (double) total;
    }

    private static int luminance(int c) {
        return (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10;
    }

    private static String format(long ms) {
        long sec = Math.max(0, ms / 1000), min = sec / 60, h = min / 60;
        if (h > 0) return h + "h " + (min % 60) + "m";
        return min + "m";
    }
}
