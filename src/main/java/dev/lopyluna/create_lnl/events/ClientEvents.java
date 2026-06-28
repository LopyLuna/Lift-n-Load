package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftActions;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftItem;
import dev.lopyluna.create_lnl.content.utils.LiftSoundDistUtil;
import dev.lopyluna.create_lnl.register.client.LiftKeys;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Lifts.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
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
        var mc = Minecraft.getInstance();
        if (mc.level == null || isPreEvent) return; //mc.level.getGameTime() % 2 != 0
        if (mc.player != null) {
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
        //if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        //var ms = event.getPoseStack();
        //ms.pushPose();
        //var buffer = DefaultSuperRenderTypeBuffer.getInstance();
        //var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();


        //buffer.draw();
        //RenderSystem.enableCull();
        //ms.popPose();
    }

    protected static boolean isGameActive() {
        return !(Minecraft.getInstance().level == null || Minecraft.getInstance().player == null);
    }
}
