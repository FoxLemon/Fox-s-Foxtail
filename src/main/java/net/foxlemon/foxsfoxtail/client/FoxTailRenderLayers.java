package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Adds the tail to the existing player renderer. PlayerModel is the parent's model.
public class FoxTailRenderLayers extends RenderLayer<AvatarRenderState, PlayerModel> {

    // Reloading resource packs can replace the model and its texture path.
    private FoxTailGeometry.Geometry geometry;
    private FoxTailModel model;

    public FoxTailRenderLayers(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
        this.geometry = FoxTailGeometry.current();
        this.model = geometry.bakeModel();
    }

    @Override 
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState renderState, float yRot, float xRot) {
        FoxTailGeometry.Geometry current = FoxTailGeometry.current();
        if (current != geometry) {
            geometry = current;
            model = current.bakeModel();
        }

        var minecraft = Minecraft.getInstance();
        boolean shadowPass = TailShaderCompat.isShadowPass();
        // Shadow passes can render first with light-space transforms. Only normal draws
        // may record contacts or torso roll; the tail still renders in every pass.
        boolean sampleBlocks = !shadowPass && minecraft.level != null && minecraft.player != null
            && FoxTailClient.shouldSampleBlockCollision(renderState);
        // Undo the renderer's final -1.501 model offset when converting probes to world positions.
        Vector3f entityOrigin = sampleBlocks
            ? poseStack.last().pose().transformPosition(0, 1.501F, 0, new Vector3f()) : null;

        // Save transforms so the tail's positioning does not affect other layers.
        poseStack.pushPose();
        var playerModel = getParentModel();

        // Minecraft has already posed the parent model before submitting its layers.
        Vec3 avoidance = TailPose.legAvoidance(playerModel.leftLeg.xRot, playerModel.rightLeg.xRot, 1.0F);

        renderState.setRenderData(FoxTailClient.TAIL_AVOIDANCE, avoidance);

        // Supply torso roll in radians for the next physics tick.
        if (!shadowPass) {
            FoxTailClient.recordBaseRoll(renderState, playerModel.body.zRot);
        }

        // Follow both overall model motion and the torso's local pose, in that order.
        playerModel.root().translateAndRotate(poseStack);
        playerModel.body.translateAndRotate(poseStack);

        // Lower-back attachment in torso coordinates; 16 model units equal one block.
        poseStack.translate(0, 10.0/16.0, 2.0/16.0);

        // Turn the exported tail's +X length toward the player's back (+Z).
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));

        // Place the model pack's attachment point at the lower back.
        Vec3 offset = geometry.renderOffset();
        poseStack.translate(offset.x, offset.y, offset.z);

        if (sampleBlocks) {
            var bends = TailBlockCollision.sample(this.model, geometry, poseStack, renderState,
                minecraft.level, minecraft.player, entityOrigin);
            FoxTailClient.recordBlockCollision(renderState, bends);
        }

        // Cull back faces so zero-thickness planes do not draw both UV faces together.
        // Use the player's hurt/death overlay so the tail flashes red with the body.
        collector
            .order(1)
            .submitModel(
                this.model, 
                renderState, 
                poseStack, 
                RenderTypes.entityCutoutCull(geometry.texture()),
                lightCoords, 
                LivingEntityRenderer.getOverlayCoords(renderState, 0.0F),
                renderState.outlineColor, 
                null
        );
        poseStack.popPose();   
    }

}
