package net.foxlemon.foxsfoxtail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

// Tail animation. FoxTailGeometry supplies the geometry and collision guides.
public class FoxTailModel extends Model {

    // Child segments inherit their parent's motion: tail -> middle -> tip.
    private final ModelPart tail;
    private final ModelPart middle;
    private final ModelPart tip;
    private final ModelPart root;
    private final Vec3 attachment;

    record Pose(Vec3 root, Vec3 middle, Vec3 tip, double angle, Vec3 avoidance) {}

    public FoxTailModel(ModelPart root, Vec3 attachment) {
        // Cull back faces so zero-thickness planes do not draw both UV faces together.
        super(RenderType::entityCutout);
        this.root = root;
        this.attachment = attachment;
        // These bone names are the shared contract for model packs and animation.
        this.tail = root.getChild("tail");
        this.middle = tail.getChild("middle");
        this.tip = middle.getChild("tip");
    }

    // Collision probes follow these same animated parts without adding cubes to the render layer.
    ModelPart rootCollisionPart() { return tail; }
    ModelPart middleCollisionPart() { return middle; }
    ModelPart tipCollisionPart() { return tip; }

    void setupPose(Pose pose) {
        root.getAllParts().forEach(ModelPart::resetPose);
        setupAvoidance(pose);
        setupPhysics(middle, pose.middle());
        setupPhysics(tip, pose.tip());
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }

    private void setupPhysics(ModelPart part, Vec3 rotation) {
        part.zRot += (float) rotation.z;
        part.yRot += (float) rotation.y;
        part.xRot += (float) rotation.x;
    }

    private void setupAvoidance(Pose pose) {
        Vec3 rootRotation = pose.root();
        float angle = (float) -Math.toRadians(pose.angle())
            + (float) rootRotation.z;

        Vec3 avoidance = pose.avoidance();
        float twist = (float) Math.toRadians(avoidance.x) + (float) rootRotation.x;
        float sway = (float) Math.toRadians(avoidance.y) + (float) rootRotation.y;

        tail.zRot += angle;
        tail.yRot += sway;
        tail.xRot += twist;

        // Include spring rotation in the compensation so the root stays attached.
        // ModelPart applies Y rotation before Z rotation.
        float cosAngle = (float) Math.cos(angle);
        float sinAngle = (float) Math.sin(angle);
        float cosSway = (float) Math.cos(sway);
        float sinSway = (float) Math.sin(sway);
        float cosTwist = (float) Math.cos(twist);
        float sinTwist = (float) Math.sin(twist);

        // Rotate the model pack's attachment point:
        // first around X (twist), then Y (sway), then Z (elevation).
        float afterX_Y = (float) attachment.y * cosTwist - (float) attachment.z * sinTwist;
        float afterX_Z = (float) attachment.y * sinTwist + (float) attachment.z * cosTwist;

        float afterY_X = (float) attachment.x * cosSway + afterX_Z * sinSway;
        float afterY_Z = -(float) attachment.x * sinSway + afterX_Z * cosSway;

        float rotatedX = afterY_X * cosAngle - afterX_Y * sinAngle;
        float rotatedY = afterY_X * sinAngle + afterX_Y * cosAngle;

        // Original attachment minus rotated attachment keeps the base fixed.
        tail.x += (float) attachment.x - rotatedX;
        tail.y += (float) attachment.y - rotatedY;
        tail.z += (float) attachment.z - afterY_Z;
    }

}
