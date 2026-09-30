package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Adds the tail to the existing player renderer. PlayerModel is the parent's model.
public class FoxTailRenderLayers extends RenderLayer<AvatarRenderState, PlayerModel> {

    // The extra model drawn by this layer is separate from the player's model.
    private final FoxTailModel model;
    
    // Path inside assets/foxsfoxtail; must match the PNG's actual location.
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        FoxsFoxTail.MODID,
        "texture/entity/fox_tail.png"
    );

    public FoxTailRenderLayers(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet entityModelSet) {
        super(renderer);
        // Baking turns the registered geometry recipe into drawable model parts.
        this.model = new FoxTailModel(entityModelSet.bakeLayer(FoxTailModel.MY_LAYER));
    }

    @Override 
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState renderState, float yRot, float xRot) {

        var minecraft = Minecraft.getInstance();
        boolean sampleBlocks = minecraft.level != null && minecraft.player != null
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
        FoxTailClient.recordBaseRoll(renderState, playerModel.body.zRot);

        // Follow both overall model motion and the torso's local pose, in that order.
        playerModel.root().translateAndRotate(poseStack);
        playerModel.body.translateAndRotate(poseStack);

        // Lower-back attachment in torso coordinates; 16 model units equal one block.
        poseStack.translate(0, 10.0/16.0, 2.0/16.0);

        // Turn the exported tail's +X length toward the player's back (+Z).
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));

        // Compensate for this export's base position; revisit after moving its pivot.
        poseStack.translate(8.0/16.0, -18.0/16.0, 0);

        if (sampleBlocks) {
            var bends = TailBlockCollision.sample(this.model, poseStack, renderState,
                minecraft.level, minecraft.player, entityOrigin);
            FoxTailClient.recordBlockCollision(renderState, bends);
        }

        // Use the player's hurt/death overlay so the tail flashes red with the body.
        collector
            .order(1)
            .submitModel(
                this.model, 
                renderState, 
                poseStack, 
                RenderTypes.entityCutout(TEXTURE), 
                lightCoords, 
                LivingEntityRenderer.getOverlayCoords(renderState, 0.0F),
                renderState.outlineColor, 
                null
        );
        poseStack.popPose();   
    }

}
