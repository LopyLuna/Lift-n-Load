package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.content.blocks.lift.LiftBlock;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.nbt.NBTHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Lifts.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class CommonEvents {
    private final static List<PhysicHoldingBEs> PHYSIC_HOLDERS = new ArrayList<>();

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var data = event.getEntity().getPersistentData();
        if (data.contains("LiftPos")) {
            var pos = NBTHelper.readBlockPos(data, "LiftPos");
            if (event.getEntity().level().getBlockState(pos).getBlock() instanceof LiftBlock) event.getEntity().level().removeBlock(pos, false);
            event.getEntity().getPersistentData().remove("LiftPos");
        }
    }
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        var data = event.getEntity().getPersistentData();
        if (data.contains("LiftPos")) {
            var pos = NBTHelper.readBlockPos(data, "LiftPos");
            if (event.getEntity().level().getBlockState(pos).getBlock() instanceof LiftBlock) event.getEntity().level().removeBlock(pos, false);
            event.getEntity().getPersistentData().remove("LiftPos");
        }
    }

    public static void onPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        for (var be : PHYSIC_HOLDERS) be.physicsTick(physicsSystem);
    }
    public static void addPhysicHolder(PhysicHoldingBEs be) {
        if (!PHYSIC_HOLDERS.contains(be)) PHYSIC_HOLDERS.add(be);
    }
    public static void removePhysicHolder(PhysicHoldingBEs be) {
        PHYSIC_HOLDERS.remove(be);
    }
}
