package dev.lopyluna.create_lnl.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.client.LiftsRenderTypes;
import dev.lopyluna.create_lnl.content.blocks.connectors.*;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets.LiftActions;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftItem;
import dev.lopyluna.create_lnl.content.utils.LiftSoundDistUtil;
import dev.lopyluna.create_lnl.register.client.LiftKeys;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Lifts.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    static Minecraft mc = Minecraft.getInstance();
    public static HashMap<BlockPos, LerpedFloat> HOVERING = new HashMap<>();
    public static List<BlockPos> TO_REMOVE_HOVERING = new ArrayList<>();

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
    public static void onTick(boolean isPreEvent) {
        if (!isGameActive()) return;
        if (mc.level == null || isPreEvent) return;
        if (mc.player != null) {
            {
                var hit = ConnectorUtils.hit(mc.level, mc.player, 1);
                var pos = hit == null || hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
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

        int movDelta;
        int rotDelta;

        if (LiftKeys.RAISE_LIFT.getKeybind().isDown()) movDelta = 1;
        else if (LiftKeys.LOWER_LIFT.getKeybind().isDown()) movDelta = -1;
        else movDelta = 0;

        if (LiftKeys.R0T_CC_LIFT.getKeybind().isDown()) rotDelta = 1;
        else if (LiftKeys.ROT_C_LIFT.getKeybind().isDown()) rotDelta = -1;
        else rotDelta = 0;

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


        if (mc.player != null && mc.player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH) && !IConnection.connections.isEmpty()) {
            var list = new ArrayList<>(IConnection.connections.entrySet().stream().sorted((a, b) -> {
                var posA = a.getKey().getCenter();
                posA = !(Sable.HELPER.getContaining(mc.level, posA) instanceof ClientSubLevel sub) ?
                        posA : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(posA)));
                var posB = b.getKey().getCenter();
                posB = !(Sable.HELPER.getContaining(mc.level, posB) instanceof ClientSubLevel sub) ?
                        posB : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(posB)));
                return -Double.compare(cam.distanceTo(posA), cam.distanceTo(posB));
            }).toList());
            var hit = ConnectorUtils.hit(mc.level, mc.player, 1);

            var holdPos = ConnectionClientHandler.holdingPos; //if null then dont render line
            var endPos = hit == null || hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
            var holdPoint = holdPos == null ? null : !(Sable.HELPER.getContaining(mc.level, holdPos) instanceof ClientSubLevel sub) ? holdPos.getCenter() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(holdPos.getCenter())));
            var endPoint = holdPos == null ? null : endPos == null ? mc.hitResult != null ?
                    !(Sable.HELPER.getContaining(mc.level, mc.hitResult.getLocation()) instanceof ClientSubLevel sub) ?
                            mc.hitResult.getLocation() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(mc.hitResult.getLocation()))) :
                    cam.add(mc.player.getLookAngle().scale(4)) :
                    !(Sable.HELPER.getContaining(mc.level, endPos) instanceof ClientSubLevel sub) ?
                            endPos.getCenter() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(endPos.getCenter())));

            ps.pushPose();
            RenderSystem.disableDepthTest();

            var orientation = mc.getEntityRenderDispatcher().cameraOrientation();

            var rendered = new HashSet<ConnectionKey>();
            for (var entry : list) {
                var connection = entry.getKey();
                if (!(mc.level.getBlockEntity(connection) instanceof IConnection<?> c) || c.getConnections().isEmpty()) continue;
                var pos = !(Sable.HELPER.getContaining(mc.level, connection) instanceof ClientSubLevel sub) ? connection.getCenter() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(connection.getCenter())));
                var dist = cam.distanceTo(pos);
                if (dist > 32) continue;
                var lerp = HOVERING.get(connection);
                var scale = lerp == null ? 1f : lerp.getValue(pt) * (.55f/.80f) / 2f + 1f;

                var clr = Connection.getRGB(entry.getValue());

                var mul = (float) ((dist/48) + 0.55f) * scale;
                var color = FastColor.ARGB32.color(Mth.clamp((int) Math.round(Mth.clamp(32-dist, 0, 1)*255), 0, 255),
                        FastColor.ARGB32.red(clr), FastColor.ARGB32.green(clr), FastColor.ARGB32.blue(clr));

                var vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR_WIRE);


                var sortedConnections = new ArrayList<>(c.getConnections().stream().sorted((a, b) -> {
                    var posA = a.getCenter();
                    posA = !(Sable.HELPER.getContaining(mc.level, posA) instanceof ClientSubLevel sub) ?
                            posA : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(posA)));
                    posA = posA.add(pos).scale(0.5f);
                    var posB = b.getCenter();
                    posB = !(Sable.HELPER.getContaining(mc.level, posB) instanceof ClientSubLevel sub) ?
                            posB : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(posB)));
                    posB = posB.add(pos).scale(0.5f);
                    return -Double.compare(cam.distanceTo(posA), cam.distanceTo(posB));
                }).toList());

                for (var connected : sortedConnections) {
                    var key = new ConnectionKey(connection, connected);
                    if (rendered.contains(key)) continue;
                    rendered.add(key);

                    var data = IConnection.connections.get(connected);
                    if (data == null) {
                        IConnection.connections.remove(connected);
                        var remove = new ArrayList<BlockPos>();
                        for (var cd : list) if (cd.getKey().equals(connected)) { remove.add(connected); break; }
                        remove.forEach(IConnection.connections::remove);
                        continue;
                    }
                    var outCen = !(Sable.HELPER.getContaining(mc.level, connected) instanceof ClientSubLevel sub) ? connected.getCenter() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(connected.getCenter())));
                    var distO = cam.distanceTo(outCen);
                    if (distO > 64) continue;
                    var lerpO = HOVERING.get(connection);
                    var scaleO = lerpO == null ? 1f : lerpO.getValue(pt) * (.55f/.80f) / 2f + 1f;
                    var clrO = Connection.getRGB(data);

                    var mulO = (float) ((distO/48) + 0.55f) * scaleO;
                    var colorO = FastColor.ARGB32.color(Mth.clamp((int) Math.round(Mth.clamp(32-distO, 0, 1)*255), 0, 255),
                            FastColor.ARGB32.red(clrO), FastColor.ARGB32.green(clrO), FastColor.ARGB32.blue(clrO));

                    renderWire(pos, outCen, cam, ps, vc, color, colorO, mul, mulO);
                }
            }

            Connection.Self connectionHold = null;
            if (holdPoint != null) connectionHold = IConnection.connections.get(holdPos);
            if (holdPoint != null && connectionHold != null) {
                var connectionEnd = IConnection.connections.get(endPos);
                var vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR_WIRE);

                var distS = cam.distanceTo(holdPoint);
                var distE = cam.distanceTo(endPoint);
                var mulS = (float) ((distS/48) + 0.55f);
                var mulE = (float) ((distE/48) + 0.55f);

                int sClr = Connection.getRGB(connectionHold);
                var sColor = FastColor.ARGB32.color(Mth.clamp((int) Math.round(Mth.clamp(32-distS, 0, 1)*255), 0, 255),
                        FastColor.ARGB32.red(sClr), FastColor.ARGB32.green(sClr), FastColor.ARGB32.blue(sClr));
                int eClr = connectionEnd == null ? sClr : Connection.getRGB(connectionEnd);
                var eColor = FastColor.ARGB32.color(Mth.clamp((int) Math.round(Mth.clamp(32-distE, 0, 1)*255), 0, 255),
                        FastColor.ARGB32.red(eClr), FastColor.ARGB32.green(eClr), FastColor.ARGB32.blue(eClr));

                renderWire(holdPoint, endPoint, cam, ps, vc, sColor, eColor, mulS, mulE);
                if (connectionEnd == null) renderNode(endPoint, cam, ps, bs.getBuffer(LiftsRenderTypes.CONNECTOR), orientation, eColor, mulE/1.75f);
            }

            for (var entry : list) {
                var connection = entry.getKey();
                var pos = !(Sable.HELPER.getContaining(mc.level, connection) instanceof ClientSubLevel sub) ? connection.getCenter() : JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(connection.getCenter())));
                var dist = cam.distanceTo(pos);
                if (dist > 32) continue;
                var lerp = HOVERING.get(connection);
                var scale = lerp == null ? 1f : lerp.getValue(pt) * (.55f/.80f) / 2f + 1f;

                var clr = Connection.getRGB(entry.getValue());
                //final var type = Connection.getType(entry.getValue());

                var mul = (float) ((dist/48) + 0.55f) * scale;
                var color = FastColor.ARGB32.color(Mth.clamp((int) Math.round(Mth.clamp(32-dist, 0, 1)*255), 0, 255),
                        FastColor.ARGB32.red(clr), FastColor.ARGB32.green(clr), FastColor.ARGB32.blue(clr));

                var vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR);
                //if (type == Connection.Type.IN) vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR_IN);
                //else if (type == Connection.Type.OUT) vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR_OUT);
                //else vc = bs.getBuffer(LiftsRenderTypes.CONNECTOR);

                renderNode(pos, cam, ps, vc, orientation, color, mul * (12/16f));
            }
            ps.popPose();
        }

        buffer.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        ps.popPose();
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

    protected static boolean isGameActive() {
        return !(Minecraft.getInstance().level == null || Minecraft.getInstance().player == null);
    }

    private record ConnectionKey(BlockPos a, BlockPos b) {
        public ConnectionKey(BlockPos a, BlockPos b) {
            if (a.compareTo(b) <= 0) {
                this.a = a;
                this.b = b;
            } else {
                this.a = b;
                this.b = a;
            }
        }
    }
}
