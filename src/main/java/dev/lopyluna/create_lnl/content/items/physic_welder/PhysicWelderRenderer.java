package dev.lopyluna.create_lnl.content.items.physic_welder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static dev.lopyluna.create_lnl.content.items.physic_welder.PhysicWelderItem.canSupportGlue;

public class PhysicWelderRenderer extends CustomRenderedItemModelRenderer {
    protected static final PartialModel HANDLE = PartialModel.of(Lifts.loc("item/physic_welder/handle"));
    protected static final PartialModel WELDER = PartialModel.of(Lifts.loc("item/physic_welder/welder"));

    private WelderStage stage = WelderStage.IDLE;
    private long animationStart = -2;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        final var mc = Minecraft.getInstance();
        final var player = mc.player;
        final var level = mc.level;
        if (player == null || level == null || (!transformType.firstPerson() && transformType != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && transformType != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            renderer.render(model.getOriginalModel(), light);
            return;
        }
        tick(stack, level, player, mc.hitResult == null ? HitResult.Type.MISS : mc.hitResult.getType());
        var right = transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var a = anim(false, true);

        ms.pushPose();
        switch (stage) {
            case SELECTING ->  {
                var xOff = (level.random.nextFloat() * 0.002f) - 0.001f;
                var yOff = (level.random.nextFloat() * 0.002f) - 0.001f;
                var zOff = (level.random.nextFloat() * 0.002f) - 0.001f;

                ms.translate(xOff, yOff, zOff);
                renderer.render(HANDLE.get(), light);
                ms.translate(-xOff, -yOff, -zOff);

                xOff = (level.random.nextFloat() * 0.01f) - 0.005f;
                yOff = (level.random.nextFloat() * 0.01f) - 0.005f;
                zOff = (level.random.nextFloat() * 0.01f) - 0.005f;

                a *= 4f;
                a -= 3f;
                a = Mth.clamp(a, 0f, 1f);

                ms.translate(0, -.5, 0);
                ms.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(a*90, 0, 90) * (right ? -1 : 1)));
                ms.translate(0, .5, 0);
                ms.translate(xOff, yOff, zOff);
            }
            case FINISHED ->  {
                renderer.render(HANDLE.get(), light);

                a *= 4f;
                a -= 3f;
                a = Mth.clamp(a, 0f, 1f);

                ms.translate(0, -.5, 0);
                ms.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(a*360, 90, 360) * (right ? -1 : 1)));
                ms.translate(0, .5, 0);

                if (a >= 1f) {
                    animationStart = -2;
                    this.stage = WelderStage.IDLE;
                }
            }
            case INVALID -> {
                renderer.render(HANDLE.get(), light);

                a *= 4f;
                a -= 3f;
                a = Mth.clamp(a, 0f, 1f);

                ms.translate(0, -.5, 0);
                ms.mulPose(Axis.ZP.rotationDegrees(Mth.clamp((1f-a)*90, 0, 90) * (right ? -1 : 1)));
                ms.translate(0, .5, 0);

                if (a >= 1f) {
                    animationStart = -2;
                    this.stage = WelderStage.IDLE;
                }
            }
            default -> renderer.render(HANDLE.get(), light);
        }
        renderer.render(WELDER.get(), light);
        ms.popPose();
    }

    public void startAnim(WelderStage stage) {
        if (animationStart != -2 && stage != WelderStage.FINISHED && stage != WelderStage.INVALID) return;
        animationStart = -1;
        this.stage = stage;
    }

    public void stopAnim() {
        if (stage == WelderStage.FINISHED || stage == WelderStage.INVALID) return;
        animationStart = -2;
        this.stage = WelderStage.IDLE;
    }

    public float anim(boolean smoothing, boolean expo) {
        if (animationStart == -2) return -2;
        if (animationStart < 0) animationStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        float progress = (now - animationStart) / (float) 500;
        if (smoothing) progress = progress * progress * (3 - 2 * progress);
        progress = Mth.clamp(progress, 0f, 1f);
        if (expo) progress = (progress == 1f) ? 1f : 1f - (float)Math.pow(2, -10f * progress);
        return progress;
    }

    public enum WelderStage {
        IDLE, SELECTING, INVALID, FINISHED
    }

    public void tick(ItemStack stack, Level level, Player player, HitResult.Type type) {
        final var firstPos = stack.get(LiftsDataComps.FIRST_POS);
        final var firstDir = stack.get(LiftsDataComps.FIRST_DIR);

        final var clientHit = Minecraft.getInstance().hitResult;
        if (clientHit == null || firstPos == null || firstDir == null) {
            if (stage == WelderStage.SELECTING && !(player.isShiftKeyDown() || type == HitResult.Type.MISS)) startAnim(WelderStage.FINISHED);
            else if (stage == WelderStage.SELECTING) startAnim(WelderStage.INVALID);
            stopAnim();
            return;
        }

        startAnim(WelderStage.SELECTING);

        final var linkVec = new Vec3(firstDir.getStepX(), firstDir.getStepY(), firstDir.getStepZ());
        final var linkAABB = new AABB(firstPos).contract(-linkVec.x, -linkVec.y, -linkVec.z).inflate(-0.1);
        //var color = new Color(150, 255, 224).getRGB();
        var color = SimColors.SUCCESS_LIME;
        Outliner.getInstance().showAABB(firstPos + "MergingObjects", linkAABB)
                .colored(color)
                .withFaceTexture(AllSpecialTextures.CHECKERED)
                .lineWidth(1.0f / 16.0f);

        if (clientHit.getType() != HitResult.Type.MISS && clientHit instanceof final BlockHitResult hit && Sable.HELPER.getContaining(level, hit.getBlockPos()) != null) {
            final var pos = hit.getBlockPos();
            final var normal = hit.getDirection();

            final var maxRange = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF() * 6;

            final var replaceState = level.getBlockState(pos.relative(normal));
            var invalid = !replaceState.canBeReplaced()
                    || canSupportGlue(level, pos, normal)
                    || firstPos.relative(firstDir).equals(pos.relative(normal))
                    || Sable.HELPER.distanceSquaredWithSubLevels(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    firstPos.getX() + 0.5, firstPos.getY() + 0.5, firstPos.getZ() + 0.5) > maxRange * maxRange;

            if (firstDir.getAxis().isHorizontal() != normal.getAxis().isHorizontal() || normal.getAxis().isVertical() && normal == firstDir) invalid = true;

            final var subLevel = Sable.HELPER.getContaining(level, pos);
            if (subLevel == null || subLevel == Sable.HELPER.getContaining(level, firstPos)) invalid = true;
            if (invalid) color = SimColors.NUH_UH_RED;

            final var hitAABB = new AABB(pos).contract(-normal.getStepX(), -normal.getStepY(), -normal.getStepZ()).inflate(-0.1);

            final var globalFirstPoint = Sable.HELPER.projectOutOfSubLevel(level, linkAABB.getCenter());
            final var globalTarget = Sable.HELPER.projectOutOfSubLevel(level, hitAABB.getCenter());

            final var data = new DustParticleOptions(new net.createmod.catnip.theme.Color(color).asVectorF(), 1);
            final var segments = 1;

            for (int i = 0; i < segments; i++) {
                Vec3 vec = globalFirstPoint.lerp(globalTarget, level.getRandom().nextFloat() * 0.8 + 0.1);
                final float variation = 0.8f;
                vec = vec.add(variation * (level.getRandom().nextFloat() * 0.5f - 0.25f),
                        variation * (level.getRandom().nextFloat() * 0.5f - 0.25f),
                        variation * (level.getRandom().nextFloat() * 0.5f - 0.25f));
                level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
            }

            Outliner.getInstance().showAABB(firstPos + " Merging Welding Selection", hitAABB)
                    .colored(color)
                    .withFaceTexture(AllSpecialTextures.CHECKERED)
                    .lineWidth(1 / 16f);
        }
    }
}
