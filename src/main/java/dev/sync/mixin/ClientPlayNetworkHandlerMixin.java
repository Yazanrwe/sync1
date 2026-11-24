package dev.sync.mixin;

import dev.sync.LatencyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    // First call runs on the network thread (true arrival time), the re-dispatched call on the client thread is skipped.
    @Inject(method = "onStatistics", at = @At("HEAD"))
    private void sync$onStatistics(StatisticsS2CPacket packet, CallbackInfo ci) {
        if (MinecraftClient.getInstance().isOnThread()) return;
        LatencyTracker.onProbeReply();
    }
}
