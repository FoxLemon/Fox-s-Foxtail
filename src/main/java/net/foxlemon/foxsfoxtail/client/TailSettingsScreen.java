package net.foxlemon.foxsfoxtail.client;

import java.util.Locale;
import net.foxlemon.foxsfoxtail.FoxTailConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Like the Forge screen, settings remain a draft until Save is pressed.
public final class TailSettingsScreen extends Screen {
    
    private final Screen parent;
    private final ModConfigSpec.DoubleValue[] settings = {
        FoxTailConfig.MOVEMENT_STRENGTH, FoxTailConfig.FREQUENCY,
        FoxTailConfig.DAMPING, FoxTailConfig.RESPONSE, FoxTailConfig.MAX_BEND,
        FoxTailConfig.TAIL_ANGLE
    };

    private final String[] labels = {"Movement strength", "Frequency (Hz)",
        "Damping", "Response", "Maximum target bend", "Root angle (degrees)"};
        
    private final double[] minimum = {0, 0.1, 0, -2, 0, -90};
    private final double[] maximum = {10, 10, 2, 2, 90, 90};
    private final double[] draft = new double[6];
    private final TailPhysics previewSpring = new TailPhysics(1, 0.5f, 0, Vec3.ZERO);
    private Vec3 previousPreview = Vec3.ZERO, preview = Vec3.ZERO;
    private int previewTicks;

    public TailSettingsScreen(Screen parent) {
        super(Component.literal("Fox's Foxtail Settings"));
        this.parent = parent;
        for (int i = 0; i < draft.length; i++) draft[i] = settings[i].get();
    }

    @Override
    protected void init() {
        int x = width / 2 - 30;
        int top = height / 2 - 100;
        for (int i = 0; i < draft.length; i++) {
            addRenderableWidget(new SettingSlider(x, top + 24 + i * 21, i));
        }
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), button -> {
            for (int i = 0; i < draft.length; i++) draft[i] = settings[i].getDefault();
            rebuildWidgets();
        }).bounds(x, top + 150, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            for (int i = 0; i < draft.length; i++) settings[i].set(draft[i]);
            FoxTailConfig.SPEC.save();
            onClose();
        }).bounds(x, top + 174, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(x + 94, top + 174, 86, 20).build());
    }

    @Override
    public void tick() {
        // A repeating test impulse lets the draft settings be previewed while paused.
        previewTicks++;
        double force = previewTicks % 60 < 10 ? 0.04 * draft[0] : 0;
        double limit = Math.toRadians(draft[4]);
        previewSpring.configure((float) draft[1], (float) draft[2], (float) draft[3]);
        previousPreview = preview;
        preview = previewSpring.Update(0.05f, new Vec3(0, 0, Math.min(limit, force)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 160;
        int top = height / 2 - 100;
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFFFFFFF);
        if (minecraft.player != null) {
            // Use a separate render state so the preview cannot move the real player's tail.
            var state = minecraft.getEntityRenderDispatcher().getPlayerRenderer(minecraft.player)
                .createRenderState(minecraft.player, partialTick);
            state.bodyRot = 25;
            state.yRot = 0;
            state.xRot = 0;
            state.setRenderData(FoxTailClient.TAIL_ANGLE, draft[5]);
            Vec3 previewRotation = previousPreview.lerp( preview, Math.max(0, Math.min(1, partialTick)));
            state.setRenderData(FoxTailClient.MIDDLE_ROTATION, previewRotation);
            state.setRenderData(FoxTailClient.TIP_ROTATION, previewRotation);
            FoxTailClient.disablePhysicsRecording(state);
            graphics.entity(state, 55, new Vector3f(0, state.boundingBoxHeight / 2, 0),
                new Quaternionf().rotateZ((float) Math.PI), null,
                left + 4, top + 25, left + 120, top + 173);
            graphics.centeredText(font, "Test impulse preview", left + 62, top + 181, 0xFFAAAAAA);
        } else {
            graphics.centeredText(font, "Join a world", left + 62, top + 85, 0xFFAAAAAA);
            graphics.centeredText(font, "for a preview", left + 62, top + 98, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        // Escape and Cancel discard the draft; only Save writes settings.
        minecraft.setScreen(parent);
    }

    private final class SettingSlider extends AbstractSliderButton {
        private final int index;

        SettingSlider(int x, int y, int index) {
            super(x, y, 180, 20, Component.empty(),
                (draft[index] - minimum[index]) / (maximum[index] - minimum[index]));
            this.index = index;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(labels[index] + ": " +
                String.format(Locale.ROOT, "%.2f", draft[index])));
        }

        @Override
        protected void applyValue() {
            draft[index] = Math.round((minimum[index] + value *
                (maximum[index] - minimum[index])) * 100) / 100.0;
            updateMessage();
        }
    }

}
