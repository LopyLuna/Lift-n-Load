package dev.lopyluna.create_lnl.content.blocks.thruster;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.eriksonn.aeronautics.content.particle.HotAirEmberParticleData;
import dev.eriksonn.aeronautics.data.AeroLang;
import dev.lopyluna.create_lnl.content.utils.LiftSoundDistUtil;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.service.SimItemService;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.joml.Vector3d;

import java.util.List;

@SuppressWarnings("deprecation")
public class ThrusterBE extends SmartBlockEntity implements IHaveGoggleInformation, IHaveHoveringInformation, BlockEntitySubLevelActor, Clearable {
    Vector3d THRUST_VECTOR = new Vector3d();
    Vector3d THRUST_POSITION = new Vector3d();

    public boolean superHeated = false;
    public ThrusterTank tank;
    public ThrusterInventory inventory;
    public float burnTime = 0;
    private float maxBurningTick = 25;

    protected double lastRenderTime;
    protected double renderTime;

    protected LerpedFloat intensitySwitch = LerpedFloat.linear().chase(0, 0.4, LerpedFloat.Chaser.EXP);
    protected LerpedFloat intensity = LerpedFloat.linear().chase(0, 0.65, LerpedFloat.Chaser.EXP);
    protected float targetIntensity = 0;
    protected int strength = 0;
    private final LerpedFloat thrust = LerpedFloat.linear().chase(0, 0.65, LerpedFloat.Chaser.EXP);
    private Vec3 lastFlagWorldCenter;
    private boolean blocked;

    private Direction direction;

    public ThrusterBE(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.inventory = new ThrusterInventory(this);
        this.tank = new ThrusterTank(this);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void initialize() {
        super.initialize();
        updateSignal();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        var state = getBlockState();
        var dir = state.getValue(ThrusterBlock.FACING);
        direction = dir;

        var stack = getStack();
        if (burnTime > 0 && !isCurrentFuelInfinite()) burnTime -= getBurningSpeed();
        if (burnTime <= 0 && (!inventory.isEmpty() || !tank.isEmpty())) {
            burnTime = getBurnTime(stack);
            superHeated = getNextSuperHeated();
            if (burnTime > 0 && !level.isClientSide) {
                if (stack instanceof ItemStack item) {
                    var slot = inventory.slot;
                    if (item.getCount() == 1 && item.getItem().hasCraftingRemainingItem()) {
                        var remaining = slot.getType().getCraftingRemainingItem();
                        if (remaining != null) slot.setStack(remaining.getDefaultInstance());
                    } else slot.shrink(1);
                }
                if (stack instanceof FluidStack) tank.drain(1, IFluidHandler.FluidAction.EXECUTE);
            }
        }
        if (burnTime <= 0) superHeated = false;
        maxBurningTick = superHeated ? 30 : 15;

        thrust.updateChaseTarget(getBurningSpeed()*30);
        thrust.tickChaser();

        blocked = flag(level);
        var thrustVal = thrust.getValue();
        targetIntensity = blocked ? 0f : Mth.clamp(thrustVal/(((burnTime>0?1:0)+30)*30), 0f, 1f);
        intensity.updateChaseTarget(targetIntensity);
        intensity.tickChaser();
        var intensityVal = intensity.getValue();
        intensitySwitch.updateChaseTarget(Math.round(targetIntensity));
        intensitySwitch.tickChaser();

        lastRenderTime = renderTime;
        renderTime += (1.0 / 15.0) * (1.0 + (intensityVal * intensityVal * 1.8));

        if (level.isClientSide) {
            if (intensity.getValue() > 0.01) {
                spawnParticles(level, dir);
                LiftSoundDistUtil.addPosHotAirBurnerSound(worldPosition);
            } else LiftSoundDistUtil.removePosHotAirBurnerSound(worldPosition);
        }
    }
    
    public void updateSignal() {
        if (level == null || burnTime <= 0) return;
        final int newSignalStrength = level.getBestNeighborSignal(worldPosition);
        if (newSignalStrength != strength) {
            if (strength == 0) level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS,
                    .1f + level.random.nextFloat() * .05f, 0.9f - level.random.nextFloat() * .2f);
            else if (newSignalStrength == 0) level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    .05f + level.random.nextFloat() * .025f, 1.1f - level.random.nextFloat() * .2f);
            strength = newSignalStrength;
            invalidateRenderBoundingBox();
            this.sendData();
        }
    }

    public float getBurningSpeed() {
        return (burnTime>0 ? 1 : 0) * maxBurningTick * (strength/15f);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!isActive() || blocked) return;
        var state = getBlockState();
        var dir = state.getValue(ThrusterBlock.FACING);
        this.applyForces(subLevel, worldPosition, dir, timeStep);
    }

    public void applyForces(final ServerSubLevel subLevel, final  BlockPos pos, final Direction dir, final double timeStep) {
        final var thrust = Vec3.atLowerCornerOf(dir.getNormal()).scale(-this.thrust.getValue() * timeStep);

        THRUST_POSITION.set(JOMLConversion.atCenterOf(pos));
        THRUST_VECTOR.set(thrust.x, thrust.y, thrust.z);

        final var forceGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        forceGroup.applyAndRecordPointForce(new Vector3d(THRUST_POSITION), new Vector3d(THRUST_VECTOR));
    }

    public boolean isActive() {
        return burnTime > 0 && strength > 0;
    }

    private boolean flag(Level level) {
        if (level == null) return false;
        final Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition).relative(direction == null ? Direction.NORTH : direction, 1.75f));
        final Vec3 previous = lastFlagWorldCenter;
        lastFlagWorldCenter = worldCenter;

        if (isBlockedAt(level, worldCenter)) return true;
        if (previous == null) return false;

        final int steps = Math.max(1, Mth.ceil(previous.distanceTo(worldCenter) * 4.0));
        for (int i = 1; i <= steps; i++) {
            final Vec3 sample = previous.lerp(worldCenter, i / (double) steps);
            if (isBlockedAt(level, sample)) return true;
        }

        return false;
    }

    private boolean isBlockedAt(final Level level, final Vec3 worldPos) {
        final BlockPos pos = BlockPos.containing(worldPos);
        if (!level.isLoaded(pos)) return false;
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    public int state(Object stack) {
        if (stack instanceof FluidStack fluid) return fluid.isEmpty() ? -2 : getBurnTime(fluid.getFluid()) <= 0 ? -1 : 1;
        if (stack instanceof ItemStack item) return item.isEmpty() ? -2 : getBurnTime(item.getItem()) <= 0 ? -1 : 1;
        return -3;
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        if (level == null) return true;
        var stack = getStack();
        var flag = state(stack);
        if (flag == -3) return true;

        var state = getBlockState();
        AeroLang.blockName(state).text(":").forGoggles(tooltip);

        var name = stack instanceof FluidStack fluid ? fluid.getHoverName() : stack instanceof ItemStack item ? item.getHoverName() : Component.empty();
        var count = stack instanceof FluidStack fluid ? " " + fluid.getAmount() + "mb" : stack instanceof ItemStack item ? " x" + item.getCount() : "";
        var empty = stack instanceof FluidStack fluid ? fluid.isEmpty() : !(stack instanceof ItemStack item) || item.isEmpty();
        final boolean hasByProduct = flag == -1;

        final LangBuilder noFuel = SimLang.translate("portable_engine.none").style(ChatFormatting.RED);
        final LangBuilder stackName = SimLang.builder().add(name)
                .text(count)
                .style(ChatFormatting.GREEN);

        if (!this.isCurrentFuelInfinite()) {
            final String langKey = hasByProduct ? "byproduct" : "fuel";
            SimLang.translate("portable_engine." + langKey, empty ? noFuel : stackName)
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
        }

        if(burnTime > 0) {
            final float seconds = burnTime / 20f;
            final float secondsTotal = getTotalBurnTime() / 20f;
            final float timeMultiplier = (1f - Mth.clamp((getBurningSpeed()*20)/(((burnTime>0?1:0)+maxBurningTick)*20), 0f, 1f));

            final var infiniteLang = SimLang.translate("portable_engine.infinite")
                    .style(ChatFormatting.LIGHT_PURPLE);
            final var timeLang = SimLang.text(getTime(secondsTotal * timeMultiplier)).style(superHeated ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            SimLang.translate("portable_engine.time", this.isTotalFuelInfinite() ? infiniteLang : timeLang)
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            if (superHeated) {
                if (isCurrentFuelInfinite()) SimLang.translate("portable_engine.superheated")
                        .style(ChatFormatting.GOLD)
                        .forGoggles(tooltip);
                else SimLang.translate("portable_engine.superheated_time", getTime((getNextSuperHeated() ? secondsTotal : seconds) * timeMultiplier))
                            .style(ChatFormatting.GOLD)
                            .forGoggles(tooltip);
            }
        }

        AeroLang.text("Strength " + strength)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        final var thrustComponent = AeroLang.pixelNewton(thrust.getValue() * 0.36)
                .style(ChatFormatting.AQUA).component();
        AeroLang.translate("propeller.thrust", thrustComponent)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        return true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && level.isClientSide) LiftSoundDistUtil.removePosHotAirBurnerSound(worldPosition);
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        nbt.put("Tank", this.tank.writeToNBT(provider, new CompoundTag()));
        nbt.put("Inventory", this.inventory.write(provider));
        nbt.putFloat("IntensityValue", intensity.getValue());
        nbt.putFloat("IntensityChase", intensity.getChaseTarget());
        nbt.putFloat("IntensitySwitchValue", intensitySwitch.getValue());
        nbt.putFloat("IntensitySwitchChase", intensitySwitch.getChaseTarget());
        nbt.putFloat("ThrustValue", thrust.getValue());
        nbt.putFloat("ThrustChase", thrust.getChaseTarget());
        nbt.putFloat("BurnTime", burnTime);
        nbt.putInt("Strength", strength);
        nbt.putBoolean("SuperHeated", this.superHeated);
    }

    @Override
    protected void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        if (nbt.contains("Tank")) this.tank.readFromNBT(provider, nbt.getCompound("Tank"));
        this.inventory.read(provider, nbt.getCompound("Inventory"));
        intensity.setValue(nbt.getFloat("IntensityValue"));
        intensity.updateChaseTarget(nbt.getFloat("IntensityChase"));
        targetIntensity = intensity.getChaseTarget();
        intensitySwitch.setValue(nbt.getFloat("IntensitySwitchValue"));
        intensitySwitch.updateChaseTarget(nbt.getFloat("IntensitySwitchChase"));
        thrust.setValue(nbt.getFloat("ThrustValue"));
        thrust.updateChaseTarget(nbt.getFloat("ThrustChase"));
        burnTime = nbt.getFloat("BurnTime");
        strength = nbt.getInt("Strength");
        invalidateRenderBoundingBox();
        superHeated = nbt.getBoolean("SuperHeated");
    }

    public Object getStack() {
        var fluid = tank.getFluid();
        if (fluid.isEmpty()) return this.inventory.slot.getStack();
        return fluid;
    }

    public float getBurnTime(Object stack) {
        if (stack instanceof FluidStack fluid) return getBurnTime(fluid.getFluid());
        if (stack instanceof ItemStack item) return getBurnTime(item.getItem());
        return 0;
    }

    public float getTotalBurnTime() {
        var fluid = tank.getFluid();
        var nextBurn = getNextBurnTime();
        return burnTime + (!fluid.isEmpty() ? fluid.getAmount() * nextBurn : inventory.slot.getStack().getCount() * nextBurn);
    }

    private float getNextBurnTime() {
        var invBurn = SimItemService.INSTANCE.getBurnTime(inventory.slot.getStack());
        var tankBurn = SimItemService.INSTANCE.getBurnTime(tank.getFluid().getFluid().getBucket().getDefaultInstance()) * (1/1000f);
        return tankBurn == 0 ? invBurn : tankBurn;
    }

    private boolean getNextSuperHeated() {
        var invBurn = SimItemService.INSTANCE.getSuperheatedBurnTime(inventory.slot.getStack());
        var tankBurn = SimItemService.INSTANCE.getSuperheatedBurnTime(tank.getFluid().getFluid().getBucket().getDefaultInstance()) * (1/1000f);
        return (tankBurn == 0 ? invBurn : tankBurn) > 0;
    }

    public static float getBurnTime(Fluid fluid) {
        var stack = fluid.getBucket().getDefaultInstance();
        var i = SimItemService.INSTANCE.getSuperheatedBurnTime(stack);
        if (i > 0) return i * (1/1000f);
        i = SimItemService.INSTANCE.getBurnTime(stack);
        return i * (1/1000f);
    }

    public static float getBurnTime(Item item) {
        var stack = item.getDefaultInstance();
        var i = SimItemService.INSTANCE.getSuperheatedBurnTime(stack);
        if (i > 0) return i;
        i = SimItemService.INSTANCE.getBurnTime(stack);
        return i;
    }

    public boolean isCurrentFuelInfinite() {
        return this.burnTime >= PortableEngineBlockEntity.INFINITE_THRESHOLD;
    }
    public boolean isTotalFuelInfinite() {
        return getNextBurnTime() >= PortableEngineBlockEntity.INFINITE_THRESHOLD || isCurrentFuelInfinite();
    }

    public static String getTime(float secF) { //temp till public static version
        var sec = Math.round(secF);
        String s = "";
        int min = sec / 60;
        final int hour = min / 60;
        sec = Math.floorMod(sec, 60);
        min = Math.floorMod(min, 60);
        if (hour > 0) s += hour + "h ";
        if (min < 10 && hour > 0) s += 0;
        if (min > 0 || hour > 0) s += min + "m ";
        if (sec < 10 && min > 0) s += 0;
        s += sec + "s";
        return s;
    }

    @Override
    public void clearContent() {
        this.tank.drain(tank.getCapacity(), IFluidHandler.FluidAction.EXECUTE);
        this.inventory.clearContent();
    }

    public void spawnParticles(Level level, Direction dir) {
        var blue = this.intensitySwitch.getValue() >= 0.5f;
        var intensity = this.intensity.getValue();
        var particleProbability = intensity;
        final var random = level.random;
        final var speed = strength/15f;
        final var facing = Vec3.atLowerCornerOf(dir.getNormal()).scale(2 + (8/16f));
        final var nozzleCenter = worldPosition.getCenter().add(facing.scale(0.56));

        if (!blue && particleProbability > random.nextFloat()) {
            final var smokePos = nozzleCenter
                    .add(facing.scale(1.5 * intensity))
                    .add(randomFaceOffset(dir, random, 2))
                    .add(facing.scale(random.nextDouble() * 0.35));
            final var smokeMotion = facing.scale(speed * speed * 0.3).scale(intensity);
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true,
                    smokePos.x, smokePos.y, smokePos.z,
                    smokeMotion.x, smokeMotion.y, smokeMotion.z);
        }

        if (random.nextInt(20) == 0 && strength > 0) level.playLocalSound(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                0.25F + random.nextFloat() * .25f, random.nextFloat() * 0.7F + 0.6F, false);

        particleProbability /= 5;
        if (particleProbability > random.nextFloat()) {
            for (int i = 0; i < random.nextInt(2) + 1; ++i) {
                final var flamePos = nozzleCenter
                        .add(facing.scale(0.08))
                        .add(randomFaceOffset(dir, random, 0.12));
                final var flameMotion = facing.scale(0.08 + speed * 0.2)
                        .add(randomFaceOffset(dir, random, 0.06));
                level.addParticle(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        flamePos.x, flamePos.y, flamePos.z,
                        flameMotion.x, flameMotion.y, flameMotion.z);
            }
        }

        if (random.nextFloat() < 0.9f * intensity) {
            final var emberPos = nozzleCenter
                    .add(facing.scale(0.12))
                    .add(randomFaceOffset(dir, random, 0.25));
            final var emberMotion = facing.scale(0.5 + intensity)
                    .add(randomFaceOffset(dir, random, 0.04)).scale(8);
            level.addParticle(new HotAirEmberParticleData(blue),
                    emberPos.x, emberPos.y, emberPos.z,
                    emberMotion.x, emberMotion.y, emberMotion.z);
        }
    }

    private static Vec3 randomFaceOffset(Direction dir, RandomSource random, double spread) {
        final var facing = Vec3.atLowerCornerOf(dir.getNormal());
        final var helperAxis = dir.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        final var tangent = facing.cross(helperAxis).normalize();
        final var bitangent = facing.cross(tangent).normalize();
        return tangent.scale((random.nextDouble() - 0.5) * spread)
                .add(bitangent.scale((random.nextDouble() - 0.5) * spread));
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().expandTowards(Vec3.atLowerCornerOf(getBlockState().getValue(ThrusterBlock.FACING).getNormal()).scale((strength/2f)+1f));
    }
}
