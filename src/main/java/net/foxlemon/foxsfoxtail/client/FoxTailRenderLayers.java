package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

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
    this.model = new FoxTailModel(
        entityModelSet.bakeLayer(FoxTailModel.MY_LAYER)
    );
}

    @Override 
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState renderState, float yRot, float xRot) {

        // Save transforms so the tail's positioning does not affect other layers.
        poseStack.pushPose();
        var playerModel = getParentModel();
        playerModel.setupAnim(renderState);

        // Follow both overall model motion and the torso's local pose, in that order.
        playerModel.root().translateAndRotate(poseStack);
        playerModel.body.translateAndRotate(poseStack);

        // Lower-back attachment in torso coordinates; 16 model units equal one block.
        poseStack.translate(0, 10.0/16.0, 2.0/16.0);

        // Turn the exported tail's +X length toward the player's back (+Z).
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));

        // Compensate for this export's base position; revisit after moving its pivot.
        poseStack.translate(8.0/16.0, -18.0/16.0, 0);

        // Queue the textured model with world lighting and no damage overlay.
        collector
            .order(1)
            .submitModel(
                this.model, 
                renderState, 
                poseStack, 
                RenderTypes.entityCutout(TEXTURE), 
                lightCoords, 
                OverlayTexture.NO_OVERLAY, 
                renderState.outlineColor, 
                null);
        
        poseStack.popPose();
    }

}
