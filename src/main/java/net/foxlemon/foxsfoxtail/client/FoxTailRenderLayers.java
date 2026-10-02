package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Adds a separate tail model to each player renderer.
public class FoxTailRenderLayers extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private FoxTailGeometry.Geometry geometry;
    private FoxTailModel model;

    public FoxTailRenderLayers(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
        geometry = FoxTailGeometry.current();
        model = geometry.bakeModel();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        FoxTailGeometry.Geometry current = FoxTailGeometry.current();
        if (current != geometry) {
            geometry = current;
            model = current.bakeModel();
        }

        var minecraft = Minecraft.getInstance();
        boolean shadowPass = TailShaderCompat.isShadowPass();
        boolean sampleBlocks = !shadowPass && minecraft.level != null
            && FoxTailClient.shouldSampleBlockCollision(player);
        // The renderer translated the model down 1.501 units before calling layers.
        Vector3f entityOrigin = sampleBlocks
            ? poseStack.last().pose().transformPosition(0, 1.501F, 0, new Vector3f()) : null;

        poseStack.pushPose();
        var playerModel = getParentModel();
        Vec3 avoidance = TailPose.legAvoidance(playerModel.leftLeg.xRot, playerModel.rightLeg.xRot, 1.0F);
        FoxTailModel.Pose tailPose = FoxTailClient.samplePose(player, partialTick, avoidance);
        model.setupPose(tailPose);
        if (!shadowPass) FoxTailClient.recordBaseRoll(player, playerModel.body.zRot);

        playerModel.body.translateAndRotate(poseStack);
        poseStack.translate(0, 10.0 / 16.0, 2.0 / 16.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
        Vec3 offset = geometry.renderOffset();
        poseStack.translate(offset.x, offset.y, offset.z);

        if (sampleBlocks) {
            var bends = TailBlockCollision.sample(model, geometry, poseStack,
                minecraft.level, player, player.getPosition(partialTick), entityOrigin);
            FoxTailClient.recordBlockCollision(player, bends);
        }

        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutout(geometry.texture())),
            light, LivingEntityRenderer.getOverlayCoords(player, 0.0F), -1);
        poseStack.popPose();
    }
}
