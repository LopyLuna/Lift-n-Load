package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchematicRenderer.class, remap = false)
public class SchematicRendererMixin {
    @Shadow @Final protected SchematicLevel schematic;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderNodes(PoseStack ms, SuperRenderTypeBuffer buffers, CallbackInfo ci) {
        if (!(schematic instanceof Node.Template template) || template.lifts$nodes().isEmpty()) return;
        var vb = buffers.getBuffer(RenderType.cutout());
        for (var entry : template.lifts$nodes().entrySet()) {
            var local = entry.getKey().subtract(schematic.anchor);
            for (var id : entry.getValue().getIntArray("Loose")) {
                var offset = NodeCell.local(id);
                ms.pushPose();
                ms.translate(local.getX(), local.getY(), local.getZ());
                CachedBuffers.partial(LiftsPartialModels.NODE, Blocks.AIR.defaultBlockState())
                        .rotateAround(NodeCell.rotation(NodeCell.face(id)), 0.5f, 0.5f, 0.5f)
                        .translate(offset.x, offset.y, offset.z)
                        .light(LightTexture.FULL_BRIGHT)
                        .renderInto(ms, vb);
                ms.popPose();
            }
        }
    }
}
