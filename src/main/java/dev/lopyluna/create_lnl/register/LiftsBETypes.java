package dev.lopyluna.create_lnl.register;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.lopyluna.create_lnl.content.blocks.lift.LiftBE;
import dev.lopyluna.create_lnl.content.blocks.lift.LiftRenderer;

import static dev.lopyluna.create_lnl.Lifts.REG;

public class LiftsBETypes {
    public static final BlockEntityEntry<LiftBE> CONTRAPTION_LIFT = REG
            .blockEntity("contraption_lift", LiftBE::new)
            .validBlocks(LiftsBlocks.CONTRAPTION_LIFT)
            .renderer(() -> LiftRenderer::new)
            .register();

    public static void register() {}
}
