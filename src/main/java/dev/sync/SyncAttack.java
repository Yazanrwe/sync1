package dev.sync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Coordinate system: Minecraft world, Y up, units are blocks.
 * Cooldown needs no RTT term: client and server both time the gap between consecutive attack arrivals.
 * Only the target position is shifted: displayed position + velocity * lead = estimated server position on arrival.
 */
final class SyncAttack {
    private final PositionHistory history;

    SyncAttack(PositionHistory history) {
        this.history = history;
    }

    void tick(MinecraftClient mc) {
        SyncConfig cfg = SyncConfig.get();
        ClientPlayerEntity self = mc.player;
        ClientWorld world = mc.world;
        if (!cfg.enabled || !cfg.holdToSync || self == null || world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || mc.isPaused()) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (!self.isAlive() || self.isSpectator() || self.isUsingItem()) return;
        if (self.getAttackCooldownProgress(0.5f) < cfg.minCooldown) return;

        double lead = LatencyTracker.leadMs(mc, cfg);
        Vec3d eye = self.getCameraPosVec(1.0f);
        Vec3d dir = self.getRotationVec(1.0f);

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : world.getEntities()) {
            if (!valid(e, self, cfg)) continue;
            if (e.squaredDistanceTo(self) > 64.0) continue;

            List<PositionHistory.Sample> samples = history.get(e.getId());
            Vec3d vel = SyncMath.velocityPerMs(samples, cfg.velocityWindowMs);
            Box base = e.getBoundingBox().expand(cfg.hitboxMargin * cfg.factor() + e.getTargetingMargin());
            double overshoot = 1.0 + (cfg.sweepOvershoot - 1.0) * cfg.factor();

            double d = SyncMath.sweepHitDistance(eye, dir, base, vel, lead, overshoot,
                    cfg.sweepSteps, cfg.maxPredictBlocks, cfg.predictVertical, cfg.reachBlocks);
            if (Double.isNaN(d) || d >= bestDist) continue;

            Vec3d hitPoint = eye.add(dir.multiply(d));
            BlockHitResult block = world.raycast(new RaycastContext(
                    eye, hitPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, self));
            if (block.getType() != HitResult.Type.MISS) continue;

            best = e;
            bestDist = d;
        }

        if (best == null) return;
        mc.interactionManager.attackEntity(self, best);
        self.swingHand(Hand.MAIN_HAND);
    }

    private static boolean valid(Entity e, ClientPlayerEntity self, SyncConfig cfg) {
        if (e == self) return false;
        if (!(e instanceof LivingEntity)) return false;
        if (!e.isAlive() || e.isSpectator() || !e.isAttackable()) return false;
        return (e instanceof PlayerEntity) ? cfg.targetPlayers : cfg.targetMobs;
    }
}
