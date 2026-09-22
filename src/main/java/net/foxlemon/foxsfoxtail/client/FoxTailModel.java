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
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;

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

        setupAvoidance(state);

        setupPhysics(middle, state, FoxTailClient.MIDDLE_ROTATION);
        setupPhysics(tip, state, FoxTailClient.TIP_ROTATION);
    }

    private void setupPhysics(ModelPart part, AvatarRenderState state, ContextKey<Vec3> key) {
        Vec3 rotation = state.getRenderDataOrDefault(key, Vec3.ZERO);
        part.zRot += (float) rotation.z;
        part.yRot += (float) rotation.y;
        part.xRot += (float) rotation.x;
    }

    private void setupAvoidance(AvatarRenderState state) {
        float angle = (float) -Math.toRadians(state.getRenderDataOrDefault(FoxTailClient.TAIL_ANGLE, 0.0));

        Vec3 avoidance = state.getRenderDataOrDefault(FoxTailClient.TAIL_AVOIDANCE, Vec3.ZERO);
        float twist = (float) Math.toRadians(avoidance.x);
        float sway = (float) Math.toRadians(avoidance.y);

        tail.zRot += angle;
        tail.yRot += sway;
        tail.xRot += twist;

        // Keep the attachment fixed while rotating around both axes.
        // ModelPart applies Y rotation before Z rotation.
        float cosAngle = (float) Math.cos(angle);
        float sinAngle = (float) Math.sin(angle);
        float cosSway = (float) Math.cos(sway);
        float sinSway = (float) Math.sin(sway);
        float cosTwist = (float) Math.cos(twist);
        float sinTwist = (float) Math.sin(twist);

        // Rotate the attachment point (-8, -6, 0):
        // first around X (twist), then Y (sway), then Z (elevation).
        float afterX_Y = -6 * cosTwist;
        float afterX_Z = -6 * sinTwist;

        float afterY_X = -8 * cosSway + afterX_Z * sinSway;
        float afterY_Z = 8 * sinSway + afterX_Z * cosSway;

        float rotatedX = afterY_X * cosAngle - afterX_Y * sinAngle;
        float rotatedY = afterY_X * sinAngle + afterX_Y * cosAngle;

        // Original attachment minus rotated attachment keeps the base fixed.
        tail.x += -8 - rotatedX;
        tail.y += -6 - rotatedY;
        tail.z += -afterY_Z;
    }

}
