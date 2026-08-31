package dev.lopyluna.create_lnl.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lopyluna.create_lnl.Lifts;
import net.minecraft.client.renderer.RenderType;

@SuppressWarnings("SameParameterValue")
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

    public static final RenderType CONNECTOR_ARROW = create(
            Lifts.MOD_ID + ":connector_arrow", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, TRANSIENT_BUFFER_SIZE,
            true, false, textured("arrow"));

    public static final RenderType GHOST_LINES = ghostLines("ghost_lines", 2.5, LEQUAL_DEPTH_TEST);

    private static RenderType ghostLines(String name, double width, DepthTestStateShard depth) {
        return create(Lifts.MOD_ID + ":" + name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, TRANSIENT_BUFFER_SIZE,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(java.util.OptionalDouble.of(width)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(depth)
                        .createCompositeState(false));
    }

    private static CompositeState connectorBuilder(String id) {
        return textured("connector" + (id.isEmpty() ? "" : "_" + id));
    }

    private static CompositeState textured(String name) {
        return CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setTextureState(new TextureStateShard(Lifts.loc("textures/misc/" + name + ".png"), false, false))
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(true);
    }
}
