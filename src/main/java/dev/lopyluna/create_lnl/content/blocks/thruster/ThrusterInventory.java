package dev.lopyluna.create_lnl.content.blocks.thruster;

import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import dev.simulated_team.simulated.multiloader.inventory.SingleSlotContainer;
import dev.simulated_team.simulated.service.SimItemService;

public class ThrusterInventory extends SingleSlotContainer {
    private final ThrusterBE be;
    public ThrusterInventory(final ThrusterBE be) {
        super(64);
        this.be = be;
    }

    @Override
    public boolean canInsertItem(final ItemInfoWrapper info) {
        return be.tank.isEmpty() && SimItemService.INSTANCE.getBurnTime(info.type().getDefaultInstance()) > 0;
    }

    @Override
    public void setChanged() {
        this.be.notifyUpdate();
    }
}
