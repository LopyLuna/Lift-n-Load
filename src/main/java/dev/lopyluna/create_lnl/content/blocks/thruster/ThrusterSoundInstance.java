package dev.lopyluna.create_lnl.content.blocks.thruster;

import dev.eriksonn.aeronautics.index.AeroSoundEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ThrusterSoundInstance extends AbstractTickableSoundInstance {
    public static final ThrusterSoundInstance GLOBAL_HOT_AIR_BURNER_SOUND = new ThrusterSoundInstance(AeroSoundEvents.HOT_AIR_BURNER_HEAT.event());
    public static final ThrusterSoundInstance GLOBAL_STEAM_VENT_AIR_BURNER_SOUND = new ThrusterSoundInstance(AeroSoundEvents.STEAM_VENT_HEAT.event());

    private static final int MAX_DISTANCE = 10;
    private static final float VOLUME_SCALE = 0.325f;

    private final Set<BlockPos> NEARBY_BLOCKS = new HashSet<>();
    private final Vector3d meanPos = new Vector3d();

    private float meanPitch = 0;
    private float meanVolume = 0;

    public ThrusterSoundInstance(SoundEvent sound) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = 0.001f;
    }

    public void addPos(final BlockPos pos) {
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (distSquared(camera, pos) < MAX_DISTANCE * MAX_DISTANCE && NEARBY_BLOCKS.add(pos)) updateMeanPos();
    }

    public void removePos(final BlockPos pos) {
        NEARBY_BLOCKS.remove(pos);
        updateMeanPos();
    }

    private void updateMeanPos() {
        meanPos.zero();
        final Vector3d v = new Vector3d();
        if (!NEARBY_BLOCKS.isEmpty()) {
            for (final BlockPos nearby : NEARBY_BLOCKS) {
                v.set(nearby.getX() + 0.5, nearby.getY() + 0.5, nearby.getZ() + 0.5);

                final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(v);
                if (subLevel != null) subLevel.logicalPose().transformPosition(v);

                meanPos.add(v);
            }
            meanPos.div(NEARBY_BLOCKS.size());
        }
    }

    private void updateInformation() {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        meanPitch = 1f;
        meanVolume = 0.001f;
        int volumeChangers = 0;
        final Iterator<BlockPos> iter = NEARBY_BLOCKS.iterator();
        boolean dirty = false;
        while (iter.hasNext()) {
            final BlockPos next = iter.next();
            if (next != null) {
                if (distSquared(camera, next) > MAX_DISTANCE * MAX_DISTANCE) {
                    iter.remove();
                    dirty = true;
                    continue;
                }

                float intensityScaling;
                if (level.getBlockEntity(next) instanceof ThrusterBE hbe) intensityScaling = Mth.clamp(hbe.intensity.getValue(), 0f, 1f);
                else {
                    iter.remove();
                    dirty = true;
                    continue;
                }

                meanPitch += intensityScaling;
                meanVolume += intensityScaling * 4f;
                volumeChangers++;
            }
        }
        if (dirty) updateMeanPos();

        if (!NEARBY_BLOCKS.isEmpty()) {
            meanVolume = volumeChangers > 0 ? meanVolume / volumeChangers : meanVolume;
            meanVolume *= (float) (1 - (Math.sqrt(distSquared(camera, JOMLConversion.toMojang(meanPos))) / MAX_DISTANCE));
        }
    }

    private static double distSquared(final Camera camera, final Vec3 pos) {
        final ClientLevel level = Minecraft.getInstance().level;
        return Sable.HELPER.distanceSquaredWithSubLevels(level, camera.getPosition(), pos);
    }

    private static double distSquared(final Camera camera, final BlockPos pos) {
        return distSquared(camera, pos.getCenter());
    }

    @Override
    public void tick() {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        updateInformation();

        if (NEARBY_BLOCKS.isEmpty()) {
            this.volume = 0.001f;
            this.pitch = 0.001f;

            meanPos.zero();
            return;
        }

        this.x = meanPos.x;
        this.y = meanPos.y;
        this.z = meanPos.z;

        this.volume = meanVolume * VOLUME_SCALE;
        this.pitch = meanPitch;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        final ClientLevel level = Minecraft.getInstance().level;
        return level != null;
    }

    @Override
    public boolean isStopped() {
        final ClientLevel level = Minecraft.getInstance().level;
        return level == null;
    }
}
