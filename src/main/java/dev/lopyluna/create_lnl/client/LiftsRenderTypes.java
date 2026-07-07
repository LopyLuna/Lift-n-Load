package dev.lopyluna.create_lnl.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lopyluna.create_lnl.Lifts;
import net.minecraft.client.renderer.RenderType;

public class LiftsRenderTypes extends RenderType {
    public LiftsRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static final RenderType CONNECTOR = create(
            Lifts.MOD_ID + ":connector", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, TRANSIENT_BUFFER_SIZE,
            true, false, connectorBuilder(""));
    public static final RenderType CONNECTOR_WIRE = create(
            Lifts.MOD_ID + ":connector_wire", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, TRANSIENT_BUFFER_SIZE,
            true, false, connectorBuilder("wire"));

    private static RenderType.CompositeState connectorBuilder(String id) {
        return RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setTextureState(new TextureStateShard(Lifts.loc("textures/misc/connector" + (id.isEmpty() ? "" : "_" + id) + ".png"), false, false))
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(true);
    }
}
