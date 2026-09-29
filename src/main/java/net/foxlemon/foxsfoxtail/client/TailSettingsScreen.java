package net.foxlemon.foxsfoxtail.client;

import net.foxlemon.foxsfoxtail.FoxTailConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Like the Forge screen, settings remain a draft until Save is pressed.
public final class TailSettingsScreen extends Screen {
    private static final int STRENGTH = 0;
    private static final int FREQUENCY = 1;
    private static final int DAMPING = 2;
    private static final int SOFT_LIMIT_DAMPING = 3;
    private static final int RESPONSE = 4;
    private static final int MAX_BEND = 5;
    private static final int TAIL_ANGLE = 6;

    private final Screen parent;
    private final ModConfigSpec.DoubleValue[] settings = {
        FoxTailConfig.MOVEMENT_STRENGTH, FoxTailConfig.FREQUENCY,
        FoxTailConfig.DAMPING, FoxTailConfig.SOFT_LIMIT_DAMPING_MULTIPLIER,
        FoxTailConfig.RESPONSE, FoxTailConfig.MAX_BEND, FoxTailConfig.TAIL_ANGLE
    };

    private final String[] labels = {
        "Movement strength", 
        "Frequency (Hz)", 
        "Damping", 
        "Limit damping power",
        "Response", 
        "Soft bend limit (degrees)", 
        "Root angle (degrees)"
    };
        
    private final double[] minimum = {0, 0.1, 0, 0, -2, 0, -90};
    private final double[] maximum = {10, 10, 2, 3, 2, 90, 90};
    private final double[] draft = new double[settings.length];
    private final EditBox[] numberInputs = new EditBox[settings.length];
    private final SettingSlider[] sliders = new SettingSlider[settings.length];
    private Button saveButton;
    private int previewLeft, previewWidth;
    private final TailPhysics rootPreviewSpring = new TailPhysics(1, 0.5f, 0, Vec3.ZERO);
    private final TailPhysics middlePreviewSpring = new TailPhysics(1, 0.5f, 0, Vec3.ZERO);
    private final TailPhysics tipPreviewSpring = new TailPhysics(1, 0.5f, 0, Vec3.ZERO);
    private Vec3 previousRootPreview = Vec3.ZERO, rootPreview = Vec3.ZERO;
    private Vec3 previousMiddlePreview = Vec3.ZERO, middlePreview = Vec3.ZERO;
    private Vec3 previousTipPreview = Vec3.ZERO, tipPreview = Vec3.ZERO;
    private int previewTicks;

    public TailSettingsScreen(Screen parent) {
        super(Component.literal("Fox's Foxtail Settings"));
        this.parent = parent;
        for (int i = 0; i < draft.length; i++) draft[i] = settings[i].get();
    }

    @Override
    protected void init() {
        boolean compact = width < 390;
        previewWidth = compact ? 100 : 116;
        int inputWidth = compact ? 50 : 56;
        int sliderWidth = compact ? 150 : 180;
        int gap = compact ? 8 : 10;
        int totalWidth = previewWidth + gap + inputWidth + 8 + sliderWidth;
        previewLeft = (width - totalWidth) / 2;
        int inputX = previewLeft + previewWidth + gap;
        int sliderX = inputX + inputWidth + 8;
        int top = height / 2 - 111;
        for (int i = 0; i < draft.length; i++) {
            final int index = i;
            int y = top + 24 + i * 21;
            EditBox input = new EditBox(font, inputX, y, inputWidth, 20,
                Component.literal(labels[i] + " value"));
            input.setMaxLength(24);
            input.setValue(Double.toString(draft[i]));
            input.setResponder(value -> onNumberChanged(index, value));
            numberInputs[i] = addRenderableWidget(input);
            sliders[i] = addRenderableWidget(new SettingSlider(sliderX, y, sliderWidth, i));
        }
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"), button -> {
            for (int i = 0; i < draft.length; i++) draft[i] = settings[i].getDefault();
            rebuildWidgets();
        }).bounds(sliderX, top + 171, sliderWidth, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            for (int i = 0; i < draft.length; i++) settings[i].set(draft[i]);
            FoxTailConfig.SPEC.save();
            onClose();
        }).bounds(sliderX, top + 195, (sliderWidth - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(sliderX + (sliderWidth + 8) / 2, top + 195,
                (sliderWidth - 8) / 2, 20).build());
        updateInputValidity();
    }

    private void onNumberChanged(int index, String text) {
        Double parsed = parseInput(index, text);
        if (parsed != null) {
            draft[index] = parsed;
            sliders[index].showDraftValue();
        }
        updateInputValidity();
    }

    private Double parseInput(int index, String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) && value >= minimum[index] && value <= maximum[index]
                ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void updateInputValidity() {
        boolean valid = true;
        for (int i = 0; i < numberInputs.length; i++) {
            boolean inputValid = parseInput(i, numberInputs[i].getValue()) != null;
            numberInputs[i].setTextColor(inputValid ? 0xFFE0E0E0 : 0xFFFF5555);
            valid &= inputValid;
        }
        if (saveButton != null) saveButton.active = valid;
    }

    @Override
    public void tick() {
        // A repeating test impulse lets the draft settings be previewed while paused.
        previewTicks++;
        double force = previewTicks % 60 < 10 ? 0.04 * draft[STRENGTH] : 0;
        double limit = Math.toRadians(draft[MAX_BEND]);
        rootPreviewSpring.configure((float) draft[FREQUENCY],
            (float) draft[DAMPING], (float) draft[RESPONSE]);
        middlePreviewSpring.configure((float) draft[FREQUENCY],
            (float) draft[DAMPING], (float) draft[RESPONSE]);
        tipPreviewSpring.configure((float) draft[FREQUENCY],
            (float) draft[DAMPING], (float) draft[RESPONSE]);
        rootPreviewSpring.setBendLimit(limit);
        middlePreviewSpring.setBendLimit(limit);
        tipPreviewSpring.setBendLimit(limit);
        rootPreviewSpring.setSoftLimitDampingMultiplier(draft[SOFT_LIMIT_DAMPING]);
        middlePreviewSpring.setSoftLimitDampingMultiplier(draft[SOFT_LIMIT_DAMPING]);
        tipPreviewSpring.setSoftLimitDampingMultiplier(draft[SOFT_LIMIT_DAMPING]);
        previousRootPreview = rootPreview;
        previousMiddlePreview = middlePreview;
        previousTipPreview = tipPreview;
        rootPreview = rootPreviewSpring.Update(0.05f,
            new Vec3(0, 0, force * FoxTailClient.ROOT_STRENGTH_MULTIPLIER));
        middlePreview = middlePreviewSpring.Update(0.05f, new Vec3(0, 0, force));
        tipPreview = tipPreviewSpring.Update(0.05f,
            middlePreview.scale(FoxTailClient.TIP_STRENGTH_MULTIPLIER));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - 111;
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFFFFFFF);
        if (minecraft.player != null) {
            // Use a separate render state so the preview cannot move the real player's tail.
            var state = minecraft.getEntityRenderDispatcher().getPlayerRenderer(minecraft.player)
                .createRenderState(minecraft.player, partialTick);
            state.bodyRot = 25;
            state.yRot = 0;
            state.xRot = 0;
            state.setRenderData(FoxTailClient.TAIL_ANGLE, draft[TAIL_ANGLE]);
            float fraction = Math.max(0, Math.min(1, partialTick));
            state.setRenderData(FoxTailClient.ROOT_ROTATION,
                previousRootPreview.lerp(rootPreview, fraction));
            state.setRenderData(FoxTailClient.MIDDLE_ROTATION,
                previousMiddlePreview.lerp(middlePreview, fraction));
            state.setRenderData(FoxTailClient.TIP_ROTATION,
                previousTipPreview.lerp(tipPreview, fraction));
            FoxTailClient.disablePhysicsRecording(state);
            graphics.entity(state, 55, new Vector3f(0, state.boundingBoxHeight / 2, 0),
                new Quaternionf().rotateZ((float) Math.PI), null,
                previewLeft + 2, top + 25, previewLeft + previewWidth, top + 194);
            graphics.centeredText(font, "Impulse preview",
                previewLeft + previewWidth / 2, top + 202, 0xFFAAAAAA);
        } else {
            graphics.centeredText(font, "Join a world",
                previewLeft + previewWidth / 2, top + 85, 0xFFAAAAAA);
            graphics.centeredText(font, "for a preview",
                previewLeft + previewWidth / 2, top + 98, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        // Escape and Cancel discard the draft; only Save writes settings.
        minecraft.setScreen(parent);
    }

    private final class SettingSlider extends AbstractSliderButton {
        private final int index;

        SettingSlider(int x, int y, int width, int index) {
            super(x, y, width, 20, Component.empty(),
                (draft[index] - minimum[index]) / (maximum[index] - minimum[index]));
            this.index = index;
            updateMessage();
        }

        private void showDraftValue() {
            value = (draft[index] - minimum[index]) / (maximum[index] - minimum[index]);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(labels[index]));
        }

        @Override
        protected void applyValue() {
            draft[index] = Math.round((minimum[index] + value *
                (maximum[index] - minimum[index])) * 100) / 100.0;
            numberInputs[index].setValue(Double.toString(draft[index]));
        }
    }

}
