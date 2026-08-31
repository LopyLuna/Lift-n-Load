package dev.lopyluna.create_lnl.register.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.lopyluna.create_lnl.Lifts;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@SuppressWarnings("unused")
public class LiftIcons extends AllIcons {
    public static final ResourceLocation ATLAS = Lifts.loc("textures/gui/icons.png");

    private static int x = 0, y = -1;

    public static final LiftIcons
            I_OR = newRow(),
            I_NOR = next(),
            I_AND = next(),
            I_NAND = next(),
            I_XOR = next(),
            I_XNOR = next(),
            I_ADD = next(),
            I_SUB = next(),
            I_MUL = next(),
            I_DIV = next(),
            I_AVG = next(),
            I_INV = next(),
            I_MAX = next(),
            I_MIN = next(),
            I_MEM = next();

    private final int iconX, iconY;

    public LiftIcons(int x, int y) {
        super(x, y);
        iconX = x * 16;
        iconY = y * 16;
    }

    private static LiftIcons next() { return new LiftIcons(++x, y); }
    private static LiftIcons newRow() { return new LiftIcons(x = 0, ++y); }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void bind() {
        RenderSystem.setShaderTexture(0, ATLAS);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(ATLAS, x, y, 0, iconX, iconY, 16, 16, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(PoseStack ms, MultiBufferSource buffer, int color) {
        var builder = buffer.getBuffer(RenderType.text(ATLAS));
        var matrix = ms.last().pose();
        var rgb = new Color(color);
        var u1 = iconX * 1f / ICON_ATLAS_SIZE;
        var u2 = (iconX + 16) * 1f / ICON_ATLAS_SIZE;
        var v1 = iconY * 1f / ICON_ATLAS_SIZE;
        var v2 = (iconY + 16) * 1f / ICON_ATLAS_SIZE;

        vertex(builder, matrix, 0, 0, rgb, u1, v1);
        vertex(builder, matrix, 0, 1, rgb, u1, v2);
        vertex(builder, matrix, 1, 1, rgb, u2, v2);
        vertex(builder, matrix, 1, 0, rgb, u2, v1);
    }

    @OnlyIn(Dist.CLIENT)
    private static void vertex(VertexConsumer builder, Matrix4f matrix, float x, float y, Color rgb, float u, float v) {
        builder.addVertex(matrix, x, y, 0)
                .setColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255)
                .setUv(u, v)
                .setLight(LightTexture.FULL_BRIGHT);
    }
}
