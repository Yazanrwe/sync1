package dev.sync;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class SyncClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    public static KeyBinding menuKey;
    private final PositionHistory history = new PositionHistory();

    @Override
    public void onInitializeClient() {
        SyncConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sync.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.sync"));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sync.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.sync"));

        SyncAttack attack = new SyncAttack(history);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SyncConfig cfg = SyncConfig.get();
            while (toggleKey.wasPressed()) {
                cfg.enabled = !cfg.enabled;
                cfg.save();
                if (mc.inGameHud != null) {
                    mc.inGameHud.setOverlayMessage(Text.literal("Sync " + (cfg.enabled ? "ON" : "OFF")), false);
                }
            }
            while (menuKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new SyncScreen());
            }
            if (mc.player == null || mc.world == null) return;
            LatencyTracker.tick(mc);
            history.record(mc.world, mc.player, cfg.scanRadius);
            attack.tick(mc);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            history.clear();
            LatencyTracker.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            history.clear();
            LatencyTracker.reset();
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            SyncConfig c = SyncConfig.get();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!c.hud || mc.player == null || mc.options.hudHidden) return;
            double rtt = LatencyTracker.measuredRttMs();
            String rttText = Double.isNaN(rtt) ? "--" : String.format(Locale.ROOT, "%.0f", rtt);
            String leadText = String.format(Locale.ROOT, "%.0f", LatencyTracker.leadMs(mc, c));
            String text = "Sync " + (c.enabled ? "ON" : "OFF") + " " + c.strength + "% | rtt " + rttText + " ms | lead " + leadText + " ms";
            ctx.drawTextWithShadow(mc.textRenderer, text, 4, 4, c.enabled ? 0x55FF55 : 0xFF5555);
        });
    }
}
