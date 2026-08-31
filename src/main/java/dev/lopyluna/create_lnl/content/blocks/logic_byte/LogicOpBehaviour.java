package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

public class LogicOpBehaviour extends ScrollOptionBehaviour<LogicOp> {
    @SuppressWarnings("unchecked")
    public static final BehaviourType<LogicOpBehaviour>[] TYPES = new BehaviourType[LogicSlot.ALL.length];

    static {
        for (var i = 0; i < TYPES.length; i++) TYPES[i] = new BehaviourType<>();
    }

    public final LogicSlot slot;

    public LogicOpBehaviour(LogicByteBE be, LogicSlot slot) {
        super(LogicOp.class, Component.translatable("create_lnl.logic_byte.mode"), be, new LogicSlotTransform(slot));
        this.slot = slot;
        onlyActiveWhen(() -> be.has(slot));
        withCallback(v -> be.notifyUpdate());
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPES[slot.ordinal()];
    }

    @Override
    public int netId() {
        return slot.ordinal();
    }

    @Override
    public boolean bypassesInput(ItemStack mainhandItem) {
        return mainhandItem.is(LiftsBlocks.LOGIC_BYTE.get().asItem()) || mainhandItem.is(Tags.Items.TOOLS_WRENCH);
    }

    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
        setValue(get().next(false).ordinal());
        playFeedbackSound(this);
    }

    @Override
    public String getClipboardKey() {
        return "LogicOp" + slot.id;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt("Op" + slot.id, value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        value = nbt.getInt("Op" + slot.id);
    }
}
