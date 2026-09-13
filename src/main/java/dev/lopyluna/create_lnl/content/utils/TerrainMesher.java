package dev.lopyluna.create_lnl.content.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class TerrainMesher {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 4), TerrainMesher::spawn);
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final int span;
    private final int detail;
    private final int rounds;
    private final double slack;
    @Nullable private TerrainSurface ready;
    @Nullable private TerrainSurface spare;
    @Nullable private CompletableFuture<Void> job;
    @Nullable private BlockPos anchor;
    private boolean primed;

    public TerrainMesher(int span, int detail, int rounds, double slack) {
        this.span = span;
        this.detail = detail;
        this.rounds = rounds;
        this.slack = slack;
    }

    private static Thread spawn(Runnable task) {
        var thread = new Thread(task, "lnl-terrain-" + SEQ.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }

    @Nullable
    public synchronized TerrainSurface mesh() {
        return primed ? ready : null;
    }

    public synchronized void follow(BlockGetter level, Vec3 origin) {
        if (spare == null) {
            ready = new TerrainSurface(span, detail, rounds);
            spare = new TerrainSurface(span, detail, rounds);
        }
        if (job != null) {
            if (!job.isDone()) return;
            job = null;
            var done = spare;
            spare = ready;
            ready = done;
            primed = true;
        }
        if (spare == null) return;
        var at = BlockPos.containing(origin);
        if (anchor != null && anchor.distSqr(at) <= slack * slack) return;
        anchor = at;
        spare.scan(level, origin);
        job = CompletableFuture.runAsync(spare::shape, POOL);
    }

    public synchronized void drop() {
        anchor = null;
        primed = false;
    }
}
