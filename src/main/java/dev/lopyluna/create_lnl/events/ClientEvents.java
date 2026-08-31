package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.client.LiftsRenderTypes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import dev.lopyluna.create_lnl.content.nodes.client.NodeClient;
import dev.lopyluna.create_lnl.content.blocks.logic_byte.LogicOp;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBE;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.client.DockingLiftGhost;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets.LiftActions;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftItem;
import dev.lopyluna.create_lnl.content.utils.LiftSoundDistUtil;
import dev.lopyluna.create_lnl.register.LiftsTags;
import dev.lopyluna.create_lnl.register.client.LiftKeys;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Lifts.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    static Minecraft mc = Minecraft.getInstance();
    public static HashMap<Node.Key, LerpedFloat> HOVERING = new HashMap<>();
    public static List<Node.Key> TO_REMOVE_HOVERING = new ArrayList<>();

    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre event) {
        onTick( true);
    }
    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post event) {
        onTick(false);
    }

    private static int oMovDelta;
    private static int oRotDelta;
    private static java.lang.ref.WeakReference<net.minecraft.client.player.LocalPlayer> lastPlayer = new java.lang.ref.WeakReference<>(null);
    public static void onTick(boolean isPreEvent) {
        if (!isGameActive()) return;
        if (mc.level == null || isPreEvent) return;
        if (mc.player != null) {
            {
                var pos = mc.player.getMainHandItem().is(LiftsTags.NODE_CONNECTOR) ? NodeClient.pick(mc.level, mc.player, 1) : null;
                if (pos != null && !HOVERING.containsKey(pos)) HOVERING.put(pos, LerpedFloat.linear().chase(1f, 0.65f, LerpedFloat.Chaser.EXP));
                if (!HOVERING.isEmpty()) {
                    HOVERING.forEach((p, l) -> {
                        l.updateChaseTarget(p.equals(pos) ? 1f : 0f);
                        l.tickChaser();
                        if (l.getValue() < 0.01) TO_REMOVE_HOVERING.add(p);
                    });
                    if (!TO_REMOVE_HOVERING.isEmpty()) TO_REMOVE_HOVERING.forEach(HOVERING::remove);
                    TO_REMOVE_HOVERING.clear();
                }
            }

            var main = mc.player.getMainHandItem();
            var off = mc.player.getOffhandItem();
            var hit = mc.hitResult;
            if (main.getItem() instanceof SpringShaftItem item) item.tick(mc.level, main, hit instanceof BlockHitResult result ? result : null);
            else if (off.getItem() instanceof SpringShaftItem item) item.tick(mc.level, off, hit instanceof BlockHitResult result ? result : null);
        }
        LiftSoundDistUtil.tickGlobalThrusterSound();

        if (lastPlayer.get() != mc.player) {
            lastPlayer = new java.lang.ref.WeakReference<>(mc.player);
            oMovDelta = 0;
            oRotDelta = 0;
            DockingLiftGhost.invalidate();
        }
        DockingLiftGhost.tick(mc);

        int movDelta;
        int rotDelta;

        if (LiftKeys.RAISE_LIFT.getKeybind().isDown()) movDelta = 1;
        else if (LiftKeys.LOWER_LIFT.getKeybind().isDown()) movDelta = -1;
        else movDelta = 0;

        if (LiftKeys.R0T_CC_LIFT.getKeybind().isDown()) rotDelta = 1;
        else if (LiftKeys.ROT_C_LIFT.getKeybind().isDown()) rotDelta = -1;
        else rotDelta = 0;

        if (movDelta != 0 && mc.player != null) {
            var lift = DockingLiftBE.controlledBy(mc.player);
            if (lift != null && !lift.placing && !lift.cantControl(mc.player)) lift.predictTarget(movDelta);
        }

        if (oMovDelta != movDelta || oRotDelta != rotDelta) {
            oMovDelta = movDelta;
            oRotDelta = rotDelta;
            CatnipServices.NETWORK.sendToServer(new LiftActions(movDelta, rotDelta));
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (mc.level == null) return;
        var ps = event.getPoseStack();
        var pt = event.getPartialTick().getGameTimeDeltaTicks();

        ps.pushPose();
        var buffer = DefaultSuperRenderTypeBuffer.getInstance();
        var bs = (MultiBufferSource) buffer;
        var cam = mc.gameRenderer.getMainCamera().getPosition();

        DockingLiftGhost.render(ps, cam, bs::getBuffer);

        renderLoose(mc.level, cam, ps, bs);

        if (mc.player != null && (mc.player.getMainHandItem().is(LiftsTags.NODE_VIEWER) || mc.player.getOffhandItem().is(LiftsTags.NODE_VIEWER))) {
            var level = mc.level;
            var visible = new ArrayList<RenderedPort>();
            for (var pos : NodeClient.positions()) {
                if (!level.isLoaded(pos)) continue;
                for (var port : NodeHosts.ports(level, pos)) {
                    var point = NodeClient.render(level, pos, port);
                    var dist = cam.distanceTo(point);
                    if (dist > 32) continue;
                    visible.add(new RenderedPort(pos, port, point, dist));
                }
            }
            visible.sort((a, b) -> -Double.compare(a.dist(), b.dist()));

            var holding = NodeClient.holding;
            var target = NodeClient.pick(level, mc.player, pt);

            ps.pushPose();
            RenderSystem.disableDepthTest();

            var orientation = mc.getEntityRenderDispatcher().cameraOrientation();
            var links = new ArrayList<RenderedLink>();

            for (var node : visible) {
                if (!node.port().flow().canOutput()) continue;
                for (var link : Node.Graphs.CLIENT.outputs(node.port().key(node.pos()))) {
                    if (!level.isLoaded(link.pos())) continue;
                    var other = NodeHosts.port(level, link);
                    if (other == null) continue;
                    var point = NodeClient.render(level, link.pos(), other);
                    var dist = cam.distanceTo(point);
                    if (dist > 64) continue;
                    var order = other.merge() instanceof LogicOp op && op.ordered() ? Node.Graphs.CLIENT.inputs(link) : List.<Node.Key>of();
                    links.add(new RenderedLink(node.point(), point, argb(node.port().color(), node.dist()), argb(other.color(), dist),
                            scale(node, pt), scale(dist, 1f), node.dist(), dist,
                            order.size() < 2 ? 0 : order.indexOf(node.port().key(node.pos())) + 1));
                }
            }

            Vec3 ghost = null;
            var ghostClr = 0;
            var ghostScale = 0f;
            if (holding != null) {
                var from = NodeHosts.port(level, holding);
                if (from != null) {
                    var start = NodeClient.render(level, holding.pos(), from);
                    var toPort = target == null ? null : NodeHosts.port(level, target);
                    var end = toPort == null ? crosshair(level, cam) : NodeClient.render(level, target.pos(), toPort);
                    var distS = cam.distanceTo(start);
                    var distE = cam.distanceTo(end);
                    var endClr = toPort == null ? from.color() : toPort.color();
                    links.add(new RenderedLink(start, end, argb(from.color(), distS), argb(endClr, distE),
                            scale(distS, 1f), scale(distE, 1f), distS, distE, 0));
                    if (toPort == null) {
                        ghost = end;
                        ghostClr = argb(endClr, distE);
                        ghostScale = scale(distE, 1f) / 1.75f;
                    }
                }
            }

            var wires = bs.getBuffer(LiftsRenderTypes.CONNECTOR_WIRE);
            for (var link : links) renderWire(link.start(), link.end(), cam, ps, wires, link.sClr(), link.eClr(), link.sScale(), link.eScale());

            var arrows = bs.getBuffer(LiftsRenderTypes.CONNECTOR_ARROW);
            for (var link : links) renderFlow(link.start(), link.end(), cam, ps, arrows, orientation, link.sClr(), link.eClr(), link.sDist(), link.eDist());

            var dots = bs.getBuffer(LiftsRenderTypes.CONNECTOR);
            for (var node : visible) renderNode(node.point(), cam, ps, dots, orientation, argb(node.port().color(), node.dist()), scale(node, pt) * (12/16f));
            if (ghost != null) renderNode(ghost, cam, ps, dots, orientation, ghostClr, ghostScale);

            for (var link : links) {
                if (link.order() <= 0) continue;
                renderOrder(link, cam, ps, bs, orientation);
            }
            ps.popPose();
        }

        buffer.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        ps.popPose();
    }

    public static void renderLoose(final Level level, final Vec3 cam, final PoseStack ps, final MultiBufferSource bs) {
        if (Node.Graphs.CLIENT.nodes.isEmpty()) return;
        var vb = bs.getBuffer(RenderType.cutout());
        for (var entry : Node.Graphs.CLIENT.nodes.entrySet()) {
            var node = entry.getValue();
            if (node.loose.isEmpty()) continue;
            var pos = entry.getKey();
            if (!level.isLoaded(pos)) continue;
            var sub = Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel client ? client : null;
            var origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());
            if (sub != null) origin = sub.renderPose().transformPosition(origin);
            if (cam.distanceToSqr(origin) > 64 * 64) continue;
            var state = level.getBlockState(pos);
            var light = LevelRenderer.getLightColor(level, pos);
            ps.pushPose();
            TransformStack.of(ps).translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
            if (sub != null) ps.mulPose(new Quaternionf(sub.renderPose().orientation()));
            for (var id : node.loose) {
                var local = NodeCell.local(id);
                CachedBuffers.partial(LiftsPartialModels.NODE, state)
                        .rotateAround(NodeCell.rotation(NodeCell.face(id)), 0.5f, 0.5f, 0.5f)
                        .translate(local.x, local.y, local.z)
                        .light(light)
                        .renderInto(ps, vb);
            }
            ps.popPose();
        }
    }

    public static void renderNode(final Vec3 pos, final Vec3 cam, final PoseStack ps, final VertexConsumer vc, final Quaternionf orientation, final int argb, final float scale) {
        ps.pushPose();

        TransformStack.of(ps).translate(pos.subtract(cam));
        ps.mulPose(orientation);
        ps.scale(scale, scale, scale);

        var pose = ps.last();

        vc.addVertex(pose, 0f - 0.5f, 0f - 0.5f, 0f).setColor(argb).setUv(0f, 1f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 0f - 0.5f, 1f - 0.5f, 0f).setColor(argb).setUv(0f, 0f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 1f - 0.5f, 1f - 0.5f, 0f).setColor(argb).setUv(1f, 0f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 1f - 0.5f, 0f - 0.5f, 0f).setColor(argb).setUv(1f, 1f).setLight(LightTexture.FULL_BRIGHT);

        ps.popPose();
    }

    public static void renderWire(final Vec3 sPos, final Vec3 ePos, final Vec3 cam, final PoseStack ps, final VertexConsumer vc, int sARGB, int eARGB, final float startScale, final float endScale) {
        var start = new Vector3f((float) (sPos.x - cam.x), (float) (sPos.y - cam.y), (float) (sPos.z - cam.z));
        var end = new Vector3f((float) (ePos.x - cam.x), (float) (ePos.y - cam.y), (float) (ePos.z - cam.z));

        var line = new Vector3f(end).sub(start);
        if (line.lengthSquared() < 1.0e-8f) return;

        var dir = new Vector3f(line).normalize();
        var mid = new Vector3f(start).add(end).mul(0.5f);
        if (mid.lengthSquared() < 1.0e-8f) return;
        var viewDir = mid.normalize();
        var side = new Vector3f(dir).cross(viewDir);
        if (side.lengthSquared() < 1.0e-6f) {
            var f = Math.abs(dir.y) < 0.9f;
            side.set(f ? 0 : 1, f ? 1 : 0, 0);
            side.fma(-side.dot(dir), dir);
        }
        if (side.lengthSquared() < 1.0e-6f) return;
        side.normalize();

        ps.pushPose();
        var pose = ps.last();

        float baseHalfWidth = 0.75f/16f;

        float sHalf = baseHalfWidth * startScale;
        float eHalf = baseHalfWidth * endScale;

        var sA = new Vector3f(start).fma(sHalf, side);
        var sB = new Vector3f(start).fma(-sHalf, side);
        var eA = new Vector3f(end).fma(eHalf, side);
        var eB = new Vector3f(end).fma(-eHalf, side);

        vc.addVertex(pose, sA.x, sA.y, sA.z)
                .setColor(sARGB)
                .setUv(0f, 1f)
                .setLight(LightTexture.FULL_BRIGHT);

        vc.addVertex(pose, sB.x, sB.y, sB.z)
                .setColor(sARGB)
                .setUv(0f, 0f)
                .setLight(LightTexture.FULL_BRIGHT);

        vc.addVertex(pose, eB.x, eB.y, eB.z)
                .setColor(eARGB)
                .setUv(1f, 0f)
                .setLight(LightTexture.FULL_BRIGHT);

        vc.addVertex(pose, eA.x, eA.y, eA.z)
                .setColor(eARGB)
                .setUv(1f, 1f)
                .setLight(LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    public static void renderFlow(final Vec3 sPos, final Vec3 ePos, final Vec3 cam, final PoseStack ps, final VertexConsumer vc, final Quaternionf orientation, int sARGB, int eARGB, double sDist, double eDist) {
        var line = ePos.subtract(sPos);
        var length = line.length();
        if (length < 1.0e-4) return;
        var dir = line.scale(1 / length);
        if (length < 1) {
            renderArrow(sPos.add(line.scale(0.5)), dir, cam, ps, vc, orientation, sARGB, scale((sDist + eDist) / 2, 1f) * (12/16f));
            return;
        }
        renderArrow(sPos.add(line.scale(0.1)), dir, cam, ps, vc, orientation, sARGB, scale(sDist, 1f) * (12/16f));
        renderArrow(sPos.add(line.scale(0.9)), dir, cam, ps, vc, orientation, eARGB, scale(eDist, 1f) * (12/16f));
    }

    public static void renderArrow(final Vec3 pos, final Vec3 dir, final Vec3 cam, final PoseStack ps, final VertexConsumer vc, final Quaternionf orientation, final int argb, final float scale) {
        var local = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        new Quaternionf(orientation).conjugate().transform(local);
        if (local.x * local.x + local.y * local.y < 1.0e-8f) return;

        ps.pushPose();
        TransformStack.of(ps).translate(pos.subtract(cam));
        ps.mulPose(orientation);
        ps.mulPose(Axis.ZP.rotation((float) Math.atan2(-local.x, local.y)));
        ps.scale(scale, scale, scale);

        var pose = ps.last();

        vc.addVertex(pose, 0f - 0.5f, 0f - 0.5f, 0f).setColor(argb).setUv(0f, 1f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 0f - 0.5f, 1f - 0.5f, 0f).setColor(argb).setUv(0f, 0f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 1f - 0.5f, 1f - 0.5f, 0f).setColor(argb).setUv(1f, 0f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(pose, 1f - 0.5f, 0f - 0.5f, 0f).setColor(argb).setUv(1f, 1f).setLight(LightTexture.FULL_BRIGHT);

        ps.popPose();
    }

    @SuppressWarnings("ClassEscapesDefinedScope")
    public static void renderOrder(final RenderedLink link, final Vec3 cam, final PoseStack ps, final MultiBufferSource bs, final Quaternionf orientation) {
        var text = String.valueOf(link.order());
        var size = scale(link.sDist(), 1f) * (0.625f/16f);

        ps.pushPose();
        TransformStack.of(ps).translate(link.start().subtract(cam));
        ps.mulPose(orientation);
        ps.scale(size, -size, size);
        mc.font.drawInBatch(text, (1 - mc.font.width(text)) / 2f, -3.5f, argb(0xFFFFFF, link.sDist()), false, ps.last().pose(), bs,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    protected static boolean isGameActive() {
        return !(Minecraft.getInstance().level == null || Minecraft.getInstance().player == null);
    }

    private record RenderedPort(BlockPos pos, Node.Port port, Vec3 point, double dist) {}

    private record RenderedLink(Vec3 start, Vec3 end, int sClr, int eClr, float sScale, float eScale, double sDist, double eDist, int order) {}

    private static int argb(int rgb, double dist) {
        var alpha = Mth.clamp((int) Math.round(Mth.clamp(32 - dist, 0, 1) * 255), 0, 255);
        return FastColor.ARGB32.color(alpha, FastColor.ARGB32.red(rgb), FastColor.ARGB32.green(rgb), FastColor.ARGB32.blue(rgb));
    }

    private static float scale(double dist, float hover) {
        return (float) ((dist / 48) + 0.55f) * hover;
    }

    private static float scale(RenderedPort node, float pt) {
        var lerp = HOVERING.get(node.port().key(node.pos()));
        return scale(node.dist(), lerp == null ? 1f : lerp.getValue(pt) * (.55f/.80f) / 2f + 1f);
    }

    private static Vec3 crosshair(Level level, Vec3 cam) {
        if (mc.hitResult != null) return NodeClient.render(level, mc.hitResult.getLocation());
        return mc.player == null ? cam : cam.add(mc.player.getLookAngle().scale(4));
    }
}
