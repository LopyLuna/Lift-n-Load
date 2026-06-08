package dev.lopyluna.create_lnl.content.blocks.thruster;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

@SuppressWarnings("NullableProblems")
public class ThrusterTank extends FluidTank {
    private final ThrusterBE be;
    public ThrusterTank(ThrusterBE be) {
        super(1000);
        this.be = be;
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return be.inventory.isEmpty() && ThrusterBE.getBurnTime(stack.getFluid()) > 0f;
    }

    @Override
    protected void onContentsChanged() {
        super.onContentsChanged();
        this.be.notifyUpdate();
    }
}
