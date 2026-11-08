package de.browars.minigame.anvilrain;

import com.mojang.math.Transformation;
import de.browars.mixin.BlockDisplayAccess;
import de.browars.mixin.DisplayAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The visual entity never place a block, drop an item, deal vanilla damage or explode. */
final class FallingAnvil {
    public static final String TEMPORARY_TAG = "browars_minigame_temporary";
    private final ServerLevel level;
    private final Display.BlockDisplay anvil;
    private final double x, z, groundY;
    private final int duration;
    private int age;
    private double y;
    private AABB swept;

    FallingAnvil(ServerLevel level, double x, double z, double groundY, int duration) {
        this.level = level; this.x = x; this.z = z; this.groundY = groundY; this.duration = duration;
        y = groundY + AnvilRainMotion.DROP_HEIGHT;
        anvil = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (anvil == null) throw new IllegalStateException("Amboss-Anzeige konnte nicht erstellt werden");
        anvil.setPermanentlyInvulnerable(true);
        anvil.addTag(TEMPORARY_TAG);
        ((BlockDisplayAccess) anvil).browars$setBlockState(Blocks.ANVIL.defaultBlockState());
        ((DisplayAccess) anvil).browars$setTeleportDuration(1);
        ((DisplayAccess) anvil).browars$setViewRange(2);
        ((DisplayAccess) anvil).browars$setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f),
                new Quaternionf(), new Vector3f(1), new Quaternionf()));
        anvil.setPos(x, y, z);
        try {
            if (!level.addFreshEntity(anvil)) throw new IllegalStateException("Amboss-Anzeige konnte nicht gespawnt werden");
        } catch (RuntimeException error) {
            discard();
            throw error;
        }
    }

    boolean advance() {
        double previous = y;
        age++;
        y = groundY + AnvilRainMotion.remainingHeight((double) age / duration);
        swept = AnvilRainMotion.sweptHitbox(x, z, previous, y);
        anvil.setPos(x, y, z);
        if (y - groundY < 5.0 && age % 2 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, x, groundY + 0.05, z, 1, 0.08, 0.01, 0.08, 0.0);
        }
        return age >= duration;
    }
    boolean intersects(AABB player) { return swept != null && swept.intersects(player); }
    boolean removed() { return anvil.isRemoved(); }
    double distanceToSqr(double px, double pz) { return (x - px) * (x - px) + (z - pz) * (z - pz); }
    void impact() {
        level.sendParticles(ParticleTypes.POOF, x, groundY + 0.12, z, 5, 0.15, 0.06, 0.15, 0.02);
        level.sendParticles(ParticleTypes.CRIT, x, groundY + 0.1, z, 5, 0.2, 0.1, 0.2, 0.1);
        level.playSound(null, x, groundY, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35f, 1.4f + level.getRandom().nextFloat() * 0.4f);
        discard();
    }
    void discard() { anvil.discard(); }
}
