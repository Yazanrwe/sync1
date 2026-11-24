package dev.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SyncConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SyncConfig instance = new SyncConfig();

    public boolean enabled = true;
    // 0 = no prediction (vanilla ray), 100 = full configured lead and margin
    public int strength = 100;
    public boolean hud = true;
    // attack key held => the mod picks the swing tick
    public boolean holdToSync = true;
    public boolean targetPlayers = true;
    public boolean targetMobs = false;

    // lead = rtt * rttScale + interpolationTicks * 50 + extraLeadMs, capped at maxLeadMs
    public double rttScale = 1.0;
    // [assumption | build 1.20.1 | not measured | unverified] client lerp of remote entities, tune on the real server
    public double interpolationTicks = 2.0;
    public double extraLeadMs = 0.0;
    public double maxLeadMs = 400.0;

    public double velocityWindowMs = 150.0;
    public double maxPredictBlocks = 3.5;
    public boolean predictVertical = false;

    // [vanilla 1.20.1 survival entity reach | server check is squared distance < 36] kept at 3.0
    public double reachBlocks = 3.0;
    public double hitboxMargin = 0.2;
    // the ray is tested against the target's whole path from displayed position to predicted position (steps + 1 boxes)
    public int sweepSteps = 6;
    // path end = lead * overshoot, covers jitter and misprediction; 1.0 = exact lead
    public double sweepOvershoot = 1.3;
    public double minCooldown = 0.93;

    public int probeIntervalTicks = 40;
    public double scanRadius = 12.0;

    public double factor() {
        return Math.max(0, Math.min(100, strength)) / 100.0;
    }

    public static SyncConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("sync.json");
    }

    public static void load() {
        Path p = path();
        SyncConfig loaded = null;
        if (Files.isRegularFile(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, SyncConfig.class);
            } catch (IOException | RuntimeException e) {
                System.err.println("[sync] config unreadable, using defaults: " + e);
            }
        }
        instance = loaded != null ? loaded : new SyncConfig();
        instance.clamp();
        instance.save();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[sync] config not saved: " + e);
        }
    }

    private static double c(double v, double lo, double hi) {
        if (!Double.isFinite(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private void clamp() {
        strength = Math.max(0, Math.min(100, strength));
        rttScale = c(rttScale, 0.0, 3.0);
        interpolationTicks = c(interpolationTicks, 0.0, 6.0);
        extraLeadMs = c(extraLeadMs, -100.0, 300.0);
        maxLeadMs = c(maxLeadMs, 0.0, 1000.0);
        velocityWindowMs = c(velocityWindowMs, 50.0, 400.0);
        maxPredictBlocks = c(maxPredictBlocks, 0.0, 8.0);
        reachBlocks = c(reachBlocks, 1.0, 3.0);
        hitboxMargin = c(hitboxMargin, 0.0, 0.6);
        sweepSteps = Math.max(1, Math.min(12, sweepSteps));
        sweepOvershoot = c(sweepOvershoot, 1.0, 2.0);
        minCooldown = c(minCooldown, 0.5, 1.0);
        scanRadius = c(scanRadius, 4.0, 32.0);
        probeIntervalTicks = Math.max(10, Math.min(400, probeIntervalTicks));
    }
}
