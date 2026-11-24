package dev.sync;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Per-entity ring of client-side positions, one sample per client tick. */
final class PositionHistory {
    record Sample(long nanos, Vec3d pos) {}

    private static final int MAX_SAMPLES = 16;
    private static final int STALE_TICKS = 20;
    private static final double TELEPORT_SQ = 25.0;

    private final Map<Integer, ArrayDeque<Sample>> history = new HashMap<>();
    private final Map<Integer, Integer> lastSeen = new HashMap<>();
    private int tick = 0;

    void record(ClientWorld world, ClientPlayerEntity self, double radius) {
        tick++;
        long now = System.nanoTime();
        double rSq = radius * radius;
        for (Entity e : world.getEntities()) {
            if (e == self || !(e instanceof LivingEntity)) continue;
            if (e.squaredDistanceTo(self) > rSq) continue;
            Vec3d p = e.getPos();
            ArrayDeque<Sample> d = history.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
            if (!d.isEmpty() && d.peekLast().pos().squaredDistanceTo(p) > TELEPORT_SQ) d.clear();
            d.addLast(new Sample(now, p));
            while (d.size() > MAX_SAMPLES) d.removeFirst();
            lastSeen.put(e.getId(), tick);
        }
        Iterator<Map.Entry<Integer, Integer>> it = lastSeen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> en = it.next();
            if (en.getValue() < tick - STALE_TICKS) {
                history.remove(en.getKey());
                it.remove();
            }
        }
    }

    List<Sample> get(int entityId) {
        ArrayDeque<Sample> d = history.get(entityId);
        return d == null ? new ArrayList<>() : new ArrayList<>(d);
    }

    void clear() {
        history.clear();
        lastSeen.clear();
        tick = 0;
    }
}
