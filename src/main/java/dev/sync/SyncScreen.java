package dev.sync;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class SyncScreen extends Screen {
    private ButtonWidget enabledButton;
    private ButtonWidget hudButton;
    private ButtonWidget playersButton;
    private ButtonWidget mobsButton;

    public SyncScreen() {
        super(Text.literal("Sync"));
    }

    @Override
    protected void init() {
        SyncConfig cfg = SyncConfig.get();
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 60;

        addDrawableChild(new StrengthSlider(x, y, w, 20));

        enabledButton = addDrawableChild(ButtonWidget.builder(label("Enabled", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(label("Enabled", cfg.enabled));
        }).dimensions(x, y + 26, w, 20).build());

        hudButton = addDrawableChild(ButtonWidget.builder(label("HUD", cfg.hud), b -> {
            cfg.hud = !cfg.hud;
            b.setMessage(label("HUD", cfg.hud));
        }).dimensions(x, y + 52, w, 20).build());

        playersButton = addDrawableChild(ButtonWidget.builder(label("Target players", cfg.targetPlayers), b -> {
            cfg.targetPlayers = !cfg.targetPlayers;
            b.setMessage(label("Target players", cfg.targetPlayers));
        }).dimensions(x, y + 78, w, 20).build());

        mobsButton = addDrawableChild(ButtonWidget.builder(label("Target mobs", cfg.targetMobs), b -> {
            cfg.targetMobs = !cfg.targetMobs;
            b.setMessage(label("Target mobs", cfg.targetMobs));
        }).dimensions(x, y + 104, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 134, w, 20).build());
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SyncClient.menuKey != null && SyncClient.menuKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        SyncConfig.get().save();
    }

    private static final class StrengthSlider extends SliderWidget {
        StrengthSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), SyncConfig.get().strength / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Strength: " + (int) Math.round(this.value * 100.0) + "%"));
        }

        @Override
        protected void applyValue() {
            SyncConfig.get().strength = (int) Math.round(this.value * 100.0);
        }
    }
}
