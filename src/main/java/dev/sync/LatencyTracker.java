package dev.sync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RTT probe: ClientStatusC2SPacket(REQUEST_STATS) out, StatisticsS2CPacket back.
 * The reply is stamped on the network thread (see ClientPlayNetworkHandlerMixin).
 * The server answers on its tick thread, same as an attack, so the sample includes the tick wait.
 */
public final class LatencyTracker {
    private static final int WINDOW = 8;
    private static final long TIMEOUT_NANOS = 3_000_000_000L;

    private static final AtomicLong sentNanos = new AtomicLong(0L);
    private static final ArrayDeque<Double> samples = new ArrayDeque<>();
    private static int ticksSinceProbe = 0;

    private LatencyTracker() {}

    public static void reset() {
        sentNanos.set(0L);
        synchronized (samples) {
            samples.clear();
        }
        ticksSinceProbe = 0;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null) return;
        SyncConfig cfg = SyncConfig.get();
        long sent = sentNanos.get();
        if (sent != 0L && System.nanoTime() - sent > TIMEOUT_NANOS) sentNanos.compareAndSet(sent, 0L);
        ticksSinceProbe++;
        if (!cfg.enabled) return;
        if (sentNanos.get() == 0L && ticksSinceProbe >= cfg.probeIntervalTicks) {
            ticksSinceProbe = 0;
            sentNanos.set(System.nanoTime());
            h.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.REQUEST_STATS));
        }
    }

    public static void onProbeReply() {
        long sent = sentNanos.getAndSet(0L);
        if (sent == 0L) return;
        double ms = (System.nanoTime() - sent) / 1.0e6;
        if (!(ms >= 0.0 && ms < 5000.0)) return;
        synchronized (samples) {
            samples.addLast(ms);
            while (samples.size() > WINDOW) samples.removeFirst();
        }
    }

    /** Median of the probe samples. NaN when none yet. */
    public static double measuredRttMs() {
        double[] a;
        synchronized (samples) {
            a = new double[samples.size()];
            int i = 0;
            for (double d : samples) a[i++] = d;
        }
        if (a.length == 0) return Double.NaN;
        Arrays.sort(a);
        int n = a.length;
        return (n % 2 == 1) ? a[n / 2] : (a[n / 2 - 1] + a[n / 2]) / 2.0;
    }

    /** Measured RTT, else the tab-list latency, else NaN. */
    public static double rttMs(MinecraftClient mc) {
        double m = measuredRttMs();
        if (!Double.isNaN(m)) return m;
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null || mc.player == null) return Double.NaN;
        PlayerListEntry e = h.getPlayerListEntry(mc.player.getUuid());
        if (e == null || e.getLatency() <= 0) return Double.NaN;
        return e.getLatency();
    }

    public static double leadMs(MinecraftClient mc, SyncConfig cfg) {
        double rtt = rttMs(mc);
        if (Double.isNaN(rtt)) rtt = 0.0;
        double lead = rtt * cfg.rttScale + cfg.interpolationTicks * 50.0 + cfg.extraLeadMs;
        return Math.max(0.0, Math.min(cfg.maxLeadMs, lead)) * cfg.factor();
    }
}
