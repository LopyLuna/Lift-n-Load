package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.engine_room.flywheel.lib.model.Models;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftBE;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftRenderer;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftBE;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftRenderer;
import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterBE;
import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterRenderer;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelRenderer;
import dev.simulated_team.simulated.service.SimInventoryService;
import net.minecraft.core.Direction;

import static dev.lopyluna.create_lnl.Lifts.REG;

public class LiftsBETypes {

    public static final BlockEntityEntry<SpringShaftBE> SPRING_SHAFT = REG
            .blockEntity("spring_shaft", SpringShaftBE::new)
            .visual(() -> (ctx, be, pt) -> new OrientedRotatingVisual<>(ctx, be, pt, Direction.SOUTH, be.facing.getOpposite(), Models.partial(AllPartialModels.SHAFT_HALF)), true)
            .validBlocks(LiftsBlocks.SPRING_SHAFT)
            .renderer(() -> SpringShaftRenderer::new)
            .register();

    public static final BlockEntityEntry<LiftBE> CONTRAPTION_LIFT = REG
            .blockEntity("contraption_lift", LiftBE::new)
            .validBlocks(LiftsBlocks.CONTRAPTION_LIFT)
            .renderer(() -> LiftRenderer::new)
            .register();

    public static final BlockEntityEntry<ThrusterBE> THRUSTER = REG
            .blockEntity("thruster", ThrusterBE::new)
            .onRegister(SimInventoryService.INSTANCE.registerInventory((be, dir) -> be.inventory))
            .validBlocks(LiftsBlocks.THRUSTER)
            .renderer(() -> ThrusterRenderer::new)
            .register();


    public static final BlockEntityEntry<WheelBE> WHEEL = REG
            .blockEntity("wheel", WheelBE::new)
            .visual(() -> SingleAxisRotatingVisual::shaft, true)
            .validBlocks(LiftsBlocks.WHEEL)
            .renderer(() -> WheelRenderer::new)
            .register();

    public static void register() {}
}
