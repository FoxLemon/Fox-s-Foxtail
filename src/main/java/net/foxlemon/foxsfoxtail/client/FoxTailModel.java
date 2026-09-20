package net.foxlemon.foxsfoxtail.client;

import net.foxlemon.foxsfoxtail.FoxsFoxTail;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

// Tail geometry and poses. AvatarRenderState supplies the player's rendering data.
public class FoxTailModel extends EntityModel<AvatarRenderState> {

    // Child segments inherit their parent's motion: tail -> middle -> tip.
    private final ModelPart tail;
    private final ModelPart middle;
    private final ModelPart tip;

    public FoxTailModel(ModelPart root) {
        super(root);
        // Retrieve baked parts using the exact group names from the geometry below.
        this.tail = root.getChild("tail");
        this.middle = tail.getChild("middle");
        this.tip = middle.getChild("tip");
    }

    // Blockbench export. Extra _r1 children preserve the decorative planes' rotations.
    // addBox defines local geometry; PartPose defines its pivot relative to its parent.
	@SuppressWarnings("unused")
    public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition tail = partdefinition.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(22, 20).addBox(-8.0F, -8.0F, -2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition middle = tail.addOrReplaceChild("middle", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -3.0F, -3.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, -6.0F, 0.0F));

		PartDefinition tailPlaneMain2_r1 = middle.addOrReplaceChild("tailPlaneMain2_r1", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, 0.0F, -4.0F, 3.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

		PartDefinition tailPlaneMain1_r1 = middle.addOrReplaceChild("tailPlaneMain1_r1", CubeListBuilder.create().texOffs(0, 12).addBox(-1.0F, 0.0F, -4.0F, 3.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		PartDefinition tip = middle.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(22, 12).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(7.0F, 0.0F, 0.0F));

		PartDefinition tailPlaneTip2_r1 = tip.addOrReplaceChild("tailPlaneTip2_r1", CubeListBuilder.create().texOffs(26, 4).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

		PartDefinition tailPlaneTip1_r1 = tip.addOrReplaceChild("tailPlaneTip1_r1", CubeListBuilder.create().texOffs(26, 0).addBox(-1.0F, 0.0F, -2.0F, 2.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

        // Texture dimensions used to interpret the UV coordinates.
		return LayerDefinition.create(meshdefinition, 64, 64);
	}

    // Shared identifier used when registering the geometry and baking the model.
    public static final ModelLayerLocation MY_LAYER = new ModelLayerLocation(
        Identifier.fromNamespaceAndPath(FoxsFoxTail.MODID, "fox_tail"), 
        "main"
    );

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);

        // This export runs along +X, so elevation rotates the root around Z.
        float angle = (float) -Math.toRadians(state.getRenderDataOrDefault(FoxTailClient.TAIL_ANGLE, 0.0));
        tail.zRot += angle;
        // Keep the attachment fixed: the exported base is (-8, -6) from its pivot.
        tail.x += -8 + 8 * (float) Math.cos(angle) - 6 * (float) Math.sin(angle);
        tail.y += -6 + 8 * (float) Math.sin(angle) + 6 * (float) Math.cos(angle);

        var middleRotation = state.getRenderDataOrDefault(FoxTailClient.TAIL_ROTATION, net.minecraft.world.phys.Vec3.ZERO);

        middle.zRot += (float) middleRotation.z;
        middle.yRot += (float) middleRotation.y;
        middle.xRot += (float) middleRotation.x;

        var tipRotation = state.getRenderDataOrDefault(FoxTailClient.TAIL_ROTATION, net.minecraft.world.phys.Vec3.ZERO);

        tip.zRot += (float) tipRotation.z;
        tip.yRot += (float) tipRotation.y;
        tip.xRot += (float) tipRotation.x;
    }
}
