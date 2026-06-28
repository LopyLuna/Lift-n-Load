package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.IBlockFromHandInteraction;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftBlock;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.register.LiftsArmInteractions;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.nbt.NBTHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Lifts.MOD_ID)
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

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);

        if (state.getBlock() instanceof IBlockFromHandInteraction interactable) {
            var result = interactable.rightClickBlock(event.getItemStack(), state, event.getLevel(), pos, event.getEntity(), event.getHand(), event.getHitVec());
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LiftsBETypes.THRUSTER.get(), (be, context) -> be.tank);
    }

    @SubscribeEvent
    public static void register(final RegisterEvent event) {
        LiftsArmInteractions.init();
    }

    public static void onPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        for (var be : PHYSIC_HOLDERS) be.physicsTick(physicsSystem);
        WheelBE.applyAllBatchedForces(timeStep);
    }
    public static void addPhysicHolder(PhysicHoldingBEs be) {
        if (!PHYSIC_HOLDERS.contains(be)) PHYSIC_HOLDERS.add(be);
    }
    public static void removePhysicHolder(PhysicHoldingBEs be) {
        PHYSIC_HOLDERS.remove(be);
    }
}
