package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets.LiftRelease;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class DockingLiftHandler {
    private static final AttributeModifier CARRY_MODIFIER = new AttributeModifier(
            Lifts.loc("carrying_sub_level"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final Map<UUID, int[]> INPUT = new ConcurrentHashMap<>();

    public static void setInput(Player player, int mov, int rot) {
        if (mov == 0 && rot == 0) INPUT.remove(player.getUUID());
        else INPUT.put(player.getUUID(), new int[]{mov, rot});
    }
    public static boolean holdingNoLiftItem(Player player) {
        return !player.getMainHandItem().is(LiftsBlocks.CONTRAPTION_LIFT.asItem())
                && !player.getOffhandItem().is(LiftsBlocks.CONTRAPTION_LIFT.asItem());
    }

    private static void driveLift(Player player) {
        var input = INPUT.get(player.getUUID());
        if (input == null || input[0] == 0) return;
        var lift = DockingLiftBE.controlledBy(player);
        if (lift == null || lift.placing || lift.cantControl(player)) return;
        lift.setTarget(lift.target + input[0] * DockingLiftBE.MOVE_RATE);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (!player.level().isClientSide) driveLift(player);

        var holding = LiftHolding.isHolding(player);
        if (holding && holdingNoLiftItem(player)) {
            if (!player.level().isClientSide) LiftHolding.release(player);
            applyCarryPenalty(player, false);
            return;
        }
        applyCarryPenalty(player, holding);
        if (holding && player.isSprinting()) player.setSprinting(false);
    }

    private static void applyCarryPenalty(Player player, boolean carrying) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        var applied = speed.getModifier(CARRY_MODIFIER.id()) != null;
        if (carrying == applied) return;
        if (carrying) speed.addTransientModifier(CARRY_MODIFIER);
        else speed.removeModifier(CARRY_MODIFIER.id());
    }

    @SubscribeEvent public static void onLivingFall(LivingFallEvent event) { if (DockingLiftBE.carriedRecently(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) { release(event.getEntity()); }
    @SubscribeEvent public static void onAttackEntity(AttackEntityEvent event) { release(event.getEntity()); }
    @SubscribeEvent public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) { releaseFromClient(event.getEntity()); }
    @SubscribeEvent public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) { if (event.getEntity().isShiftKeyDown()) releaseFromClient(event.getEntity()); }

    private static void release(Player player) {
        if (player.level().isClientSide || !LiftHolding.isHolding(player)) return;
        LiftHolding.release(player);
    }
    private static void releaseFromClient(Player player) {
        if (!player.level().isClientSide || !LiftHolding.isHolding(player)) return;
        LiftHolding.release(player);
        CatnipServices.NETWORK.sendToServer(LiftRelease.INSTANCE);
    }

    @SubscribeEvent public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer player) LiftHolding.sync(player); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer player) LiftHolding.sync(player); }
    @SubscribeEvent public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) { INPUT.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LiftHolding.release(player);
            LiftHolding.sync(player);
        }
    }
}
