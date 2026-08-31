package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.IBlockFromHandInteraction;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBE;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import dev.lopyluna.create_lnl.content.nodes.NodePayload;
import net.minecraft.core.BlockPos;
import dev.lopyluna.create_lnl.register.LiftsArmInteractions;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@EventBusSubscriber(modid = Lifts.MOD_ID)
public class CommonEvents {
    private final static List<PhysicHoldingBEs> PHYSIC_HOLDERS = new ArrayList<>();

    @SubscribeEvent
    public static void onPistonMove(final PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var graph = Node.Graphs.get(level);
        if (graph.nodes.isEmpty()) return;
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve() || resolver.getToPush().isEmpty()) return;
        var dir = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        var moves = new HashMap<BlockPos, BlockPos>();
        for (var pos : resolver.getToPush()) moves.put(pos.immutable(), pos.relative(dir).immutable());
        var pushed = new HashSet<>(moves.keySet());
        for (var pos : NodePayload.candidates(graph, resolver.getToPush())) {
            if (moves.containsKey(pos)) continue;
            var node = graph.node(pos);
            if (node == null) continue;
            for (var id : node.loose) if (pushed.contains(NodePayload.anchor(pos, id))) {
                moves.put(pos, pos.relative(dir).immutable());
                break;
            }
        }
        graph.remap(moves);
        Node.Graphs.flush(level);
    }

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);

        if (event.getItemStack().is(LiftsItems.NODE_PLUG.get()) && NodeHosts.pluggable(level, pos)) {
            if (!level.isClientSide) {
                var graph = Node.Graphs.get(level);
                if (!graph.anchored(pos)) {
                    graph.anchor(pos);
                    Node.Graphs.flush(level);
                    if (!event.getEntity().hasInfiniteMaterials()) event.getItemStack().shrink(1);
                    level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1f, level.random.nextFloat() * 0.2f + 1.2f);
                }
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
            return;
        }

        if (state.getBlock() instanceof IBlockFromHandInteraction interactable) {
            var result = interactable.rightClickBlock(event.getItemStack(), state, event.getLevel(), pos, event.getEntity(), event.getHand(), event.getHitVec());
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) Node.Graphs.tick(level);
    }

    @SubscribeEvent
    public static void onJoin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Node.Graphs.sync(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Node.Graphs.sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(final PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Node.Graphs.sync(player);
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) Node.Tracker.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        DockingLiftBE.clearAll();
        PHYSIC_HOLDERS.clear();
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
