package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBE;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public class SubLevelEntityCollisionMixin {
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;"
            , at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;getSubLevelEntityCollisionShape(Lnet/minecraft/world/entity/Entity;Lorg/joml/Vector3dc;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lnet/minecraft/world/level/block/state/BlockState;Ldev/ryanhcode/sable/util/LevelAccelerator;Lnet/minecraft/core/BlockPos;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape collide(Entity entity, Vector3dc boundsCenter, Pose3dc subLevelPose,
                                      BlockState state, LevelAccelerator level, BlockPos pos,
                                      LevelReusedVectors sink, Operation<VoxelShape> original,
                                      @Local(name = "subLevel") SubLevel subLevel, @Local(name = "existingTrackingSubLevel") SubLevel existingTrackingSubLevel) {
        if (subLevel != null && DockingLiftBE.TRACKING_NO_COLLISIONS.contains(subLevel.getUniqueId())) return Shapes.empty();
        if (existingTrackingSubLevel != null && DockingLiftBE.TRACKING_NO_COLLISIONS.contains(existingTrackingSubLevel.getUniqueId())) return Shapes.empty();
        return original.call(entity, boundsCenter, subLevelPose, state, level, pos, sink);
    }
}