package de.browars;

import com.mojang.serialization.JsonOps;
import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.sumo.SumoArenaData;
import de.browars.minigame.ui.MinigameHudState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** Regression checks against actual Minecraft geometry and the persisted arena codec. */
public final class MinigameRegressionChecks {
    private static int checks;

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var arena = new SumoArenaData();
        check(!arena.containsLossPosition(player(0, 0, 0), -64), "unset zone");
        arena.setLossZone(new BlockPos(4, 60, 4), new BlockPos(-4, 60, -4), Level.OVERWORLD);
        check(arena.containsLossPosition(player(0, 61, 0), -64), "standing on selected blocks");
        check(arena.containsLossPosition(player(0, 60.5, 0), -64), "inside zone");
        check(arena.containsLossPosition(player(0, 55, 0), -64), "falling through a thin zone");
        check(arena.containsLossPosition(player(5.2, 61, 0), -64), "edge of physical feet overlaps");
        check(!arena.containsLossPosition(player(5.31, 61, 0), -64), "outside horizontal boundary");
        check(!arena.containsLossPosition(player(0, 63, 0), -64), "safe platform above zone");
        check(!arena.containsLossPosition(player(30, -55, 30), -64), "negative height is not automatically void");
        check(arena.containsLossPosition(player(30, -81, 30), -64), "void below world minimum");
        arena.setSpawn1(0, 63, 0, 90, 0);
        arena.setSpawn2(1, 61, 1, 0, 0);
        check(!arena.isSpawnInLossZone(1, -64), "safe spawn");
        check(arena.isSpawnInLossZone(2, -64), "invalid spawn on zone");
        arena.setLossZone(new BlockPos(100, 20, 100), new BlockPos(105, 20, 105), Level.OVERWORLD);
        check(!arena.containsLossPosition(player(0, 61, 0), -64), "replacement discards old zone");
        check(arena.containsLossPosition(player(102, 21, 102), -64), "replacement zone works");
        var encoded = SumoArenaData.CODEC.encodeStart(JsonOps.INSTANCE, arena).getOrThrow();
        var loaded = SumoArenaData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(loaded.containsLossPosition(player(102, 21, 102), -64), "saved zone reloads");
        check(!loaded.containsLossPosition(player(0, 61, 0), -64), "old zone stays removed after reload");
        loaded.setLossZone(BlockPos.ZERO, BlockPos.ZERO, Level.NETHER);
        check(!loaded.hasSpawn1() && !loaded.hasSpawn2(), "dimension change clears incompatible spawns");

        // Approximate targeting must tolerate a near miss, while keeping distant/off-angle hits out.
        AABB target = player(0, 60, 3);
        check(target.inflate(0.55).clip(new net.minecraft.world.phys.Vec3(0.7, 61.6, 0),
                new net.minecraft.world.phys.Vec3(0.7, 61.6, 3)).isPresent(), "slight sideways miss connects");
        check(target.inflate(0.55).clip(new net.minecraft.world.phys.Vec3(1, 61.6, 0),
                new net.minecraft.world.phys.Vec3(1, 61.6, 3)).isEmpty(), "larger miss stays a miss");
        check(target.inflate(0.55).clip(new net.minecraft.world.phys.Vec3(0, 61.6, -3),
                new net.minecraft.world.phys.Vec3(0, 61.6, 0)).isEmpty(), "out of range stays a miss");

        // This standalone test has no datapack reload; bind the common defaults for the test stick.
        net.minecraft.world.item.Items.STICK.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        var plainStick = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        check(!de.browars.minigame.MinigamePlayerState.isGameItem(plainStick), "ordinary sticks are not game equipment");
        de.browars.minigame.MinigamePlayerState.markGameItem(plainStick);
        check(de.browars.minigame.MinigamePlayerState.isGameItem(plainStick), "game stick marker");
        check(de.browars.minigame.MinigamePlayerState.isGameItem(plainStick.copy()), "marker survives stack copy");
        check(!de.browars.minigame.MinigamePlayerState.isGameItem(net.minecraft.world.item.ItemStack.EMPTY), "empty item has no marker");

        var manager = MinigameManager.getInstance();
        var old = new FakeGame();
        manager.startMinigame(() -> old);
        manager.startMinigame(() -> {
            check(!old.running, "old game must stop before replacement is constructed");
            return new FakeGame();
        });
        check(manager.isGameRunning(), "replacement registered");
        manager.stopCurrentMinigame("test");
        check(!manager.isGameRunning(), "stop clears manager");
        var score = new de.browars.minigame.DuelScore();
        check(score.resolve(true, true, false) == de.browars.minigame.DuelScore.Result.DRAW
                && score.first() == 0 && score.second() == 0, "simultaneous loss is a draw");
        for (int i = 0; i < 5; i++) score.resolve(true, false, false);
        check(score.isFinished() && score.second() == 5, "first to five ends match");
        score.resolve(true, false, false);
        check(score.second() == 5, "finished match cannot score again");
        var soloScore = new de.browars.minigame.DuelScore();
        check(soloScore.resolve(true, false, true) == de.browars.minigame.DuelScore.Result.SOLO_OUT
                && soloScore.second() == 0, "solo loss awards no opponent point");
        var crossWarScore = new de.browars.minigame.DuelScore(3);
        check(crossWarScore.target() == 3, "cross war score target is 3");
        for (int i = 0; i < 3; i++) crossWarScore.resolve(true, false, false);
        check(crossWarScore.isFinished() && crossWarScore.second() == 3, "cross war finishes at 3 points");

        check(de.browars.minigame.crosswar.CrossWarGame.MAX_AMMO == 5, "cross war ammo is 5");
        check(de.browars.minigame.crosswar.CrossWarGame.SHOOT_COOLDOWN_TICKS == 10, "cross war cooldown is 10 ticks");
        check(de.browars.minigame.crosswar.CrossWarGame.RELOAD_TOTAL_TICKS == 50, "cross war reload is 50 ticks");
        check(de.browars.minigame.crosswar.CrossWarGame.HIT_DAMAGE == 2.0f, "cross war hit damage is 2.0");

        // Accurate arrow hit testing: 0.04 margin connects on direct hit, but avoids near miss
        AABB playerBox = player(0, 60, 0);
        check(playerBox.inflate(0.04).clip(new net.minecraft.world.phys.Vec3(0, 61, -5), new net.minecraft.world.phys.Vec3(0, 61, 5)).isPresent(), "arrow directly hitting torso connects");
        check(playerBox.inflate(0.04).clip(new net.minecraft.world.phys.Vec3(0.38, 61, -5), new net.minecraft.world.phys.Vec3(0.38, 61, 5)).isEmpty(), "arrow passing 8cm next to shoulder misses");

        // Team concrete blocks check
        check(de.browars.minigame.TeamColor.RED.getConcreteBlock() != null, "team 1 red concrete available");
        check(de.browars.minigame.TeamColor.BLUE.getConcreteBlock() != null, "team 2 blue concrete available");

        var cwArena = new de.browars.minigame.crosswar.CrossWarArenaData();
        check(!cwArena.hasArena(), "fresh cross war arena has no bounds");
        cwArena.setArenaBounds(new net.minecraft.core.BlockPos(0, 64, 0), new net.minecraft.core.BlockPos(20, 80, 20), net.minecraft.world.level.Level.OVERWORLD);
        cwArena.setSpawn1(5, 65, 5, 0, 0);
        cwArena.setSpawn2(15, 65, 15, 180, 0);
        check(cwArena.hasArena() && cwArena.hasSpawn1() && cwArena.hasSpawn2(), "cross war arena valid setup");
        check(!cwArena.isInSpawnChamber(null, 1), "null player not in spawn chamber");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(0) == 20, "anvil starts twenty blocks high");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(1) == 0, "anvil ends at ground");
        double early = de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(0)
                - de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(0.1);
        double late = de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(0.9)
                - de.browars.minigame.anvilrain.AnvilRainMotion.remainingHeight(1);
        check(late > early * 10, "fall accelerates strongly near ground");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.fallDuration(2400)
                < de.browars.minigame.anvilrain.AnvilRainMotion.fallDuration(0), "difficulty accelerates falling");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.spawnInterval(2400)
                < de.browars.minigame.anvilrain.AnvilRainMotion.spawnInterval(0), "difficulty increases rain frequency");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.fallDuration(Integer.MAX_VALUE) == 60, "difficulty remains bounded");
        check(de.browars.minigame.anvilrain.AnvilRainMotion.sweptHitbox(0, 0, 65, 60)
                .intersects(player(0, 61, 0)), "fast anvil cannot tunnel through player");
        check(!de.browars.minigame.anvilrain.AnvilRainMotion.sweptHitbox(0, 0, 65, 60)
                .intersects(player(2, 61, 0)), "nearby player avoids anvil");
        var anvilArena = new de.browars.minigame.anvilrain.AnvilRainArenaData();
        anvilArena.setArenaBounds(new BlockPos(0, 60, 0), new BlockPos(6, 60, 6), Level.OVERWORLD);
        anvilArena.setSpawn1(1.5, 61, 1.5, 0, 0);
        check(anvilArena.containsSpawn(1) && !anvilArena.containsSpawn(2), "anvil spawn checks");
        var anvilLoaded = de.browars.minigame.anvilrain.AnvilRainArenaData.CODEC.parse(JsonOps.INSTANCE,
                de.browars.minigame.anvilrain.AnvilRainArenaData.CODEC.encodeStart(JsonOps.INSTANCE, anvilArena).getOrThrow()).getOrThrow();
        check(anvilLoaded.containsSpawn(1) && anvilLoaded.getFloorY() == 60, "anvil arena persists");
        check(!anvilLoaded.containsPlayer(Double.NaN, 61, 1), "nonfinite position rejected");
        check(!anvilLoaded.containsFloorSpawn(1, 64, 1), "airborne floor spawn rejected");
        check(de.browars.util.InputValidation.boostStrength(Double.NaN) == 2, "corrupt boost defaults safely");
        check(de.browars.util.InputValidation.boostStrength(1000) == 10, "excessive boost bounded");
        check(de.browars.util.InputValidation.upwardHeight(Double.NaN) == 5.0, "corrupt upward height defaults safely");
        check(de.browars.util.InputValidation.upwardHeight(0.2) == 1.0, "underflow upward height clamped");
        check(de.browars.util.InputValidation.upwardHeight(100.0) == 30.0, "overflow upward height clamped");
        var tntArena = new de.browars.minigame.tnttag.TntTagArenaData();
        tntArena.setArenaBounds(new BlockPos(0, 50, 0), new BlockPos(10, 60, 10), Level.OVERWORLD);
        tntArena.setSpawn1(2, 51, 2, 0, 0);
        tntArena.setSpawn2(8, 51, 8, 180, 0);
        tntArena.setSpawnChamber1(new BlockPos(1, 50, 1), new BlockPos(3, 53, 3));
        check(tntArena.isFullyReady(), "tnt arena fully configured");
        check(tntArena.hasSpawnChamber1() && !tntArena.hasSpawnChamber2(), "tnt arena chamber 1 set, chamber 2 unset");
        var tntEncoded = de.browars.minigame.tnttag.TntTagArenaData.CODEC.encodeStart(JsonOps.INSTANCE, tntArena).getOrThrow();
        var tntLoaded = de.browars.minigame.tnttag.TntTagArenaData.CODEC.parse(JsonOps.INSTANCE, tntEncoded).getOrThrow();
        check(tntLoaded.isFullyReady(), "tnt arena codec reloads correctly");
        check(tntLoaded.hasSpawnChamber1() && tntLoaded.getSpawnChamber1Min().equals(new BlockPos(1, 50, 1)), "chamber 1 reloaded");
        check(!tntLoaded.hasSpawnChamber2(), "chamber 2 remains unset after reload");
        var tntSpawnsOnly = new de.browars.minigame.tnttag.TntTagArenaData();
        tntSpawnsOnly.setSpawn1(0, 64, 0, 0, 0);
        tntSpawnsOnly.setSpawn2(10, 64, 10, 0, 0);
        check(tntSpawnsOnly.isFullyReady(), "tnt tag ready with only spawns");
        check(tntSpawnsOnly.getArenaMin() == null && tntSpawnsOnly.getArenaMax() == null, "no arena bounds required");
        check(!de.browars.util.InputValidation.teleportTarget(0, 64, 0, Float.NaN, 0, "minecraft:overworld"), "invalid teleport angle rejected");
        check(!de.browars.util.InputValidation.teleportTarget(0, 64, 0, 0, 0, "INVALID DIMENSION"), "invalid dimension rejected");
        check(de.browars.util.InputValidation.teleportTarget(0, 64, 0, 0, 0, "minecraft:overworld"), "valid teleport accepted");
        check(de.browars.minigame.sumo.SumoGame.HIT_KNOCKBACK == 0.28,
                "sumo knockback remains deliberately low");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(0) == 0.28, "sumo phase 1 knockback");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(399) == 0.28, "sumo phase 1 knockback end");
        check(de.browars.minigame.sumo.SumoGame.calculatePhaseNumber(0) == 1, "sumo phase 1 number");
        check("Aufwärmen".equals(de.browars.minigame.sumo.SumoGame.calculatePhaseTitle(0)), "sumo phase 1 title");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(400) == 0.45, "sumo phase 2 step 1");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(500) == 0.55, "sumo phase 2 step 2");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(600) == 0.65, "sumo phase 2 step 3");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(700) == 0.75, "sumo phase 2 step 4");
        check(de.browars.minigame.sumo.SumoGame.calculatePhaseNumber(400) == 2, "sumo phase 2 number");
        check("Eskalation".equals(de.browars.minigame.sumo.SumoGame.calculatePhaseTitle(400)), "sumo phase 2 title");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(800) == 0.85, "sumo phase 3 step 1");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(880) == 1.00, "sumo phase 3 step 2");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(960) == 1.15, "sumo phase 3 step 3");
        check(de.browars.minigame.sumo.SumoGame.calculateKnockback(6000) == 1.80, "sumo phase 3 capped at 1.80");
        check(de.browars.minigame.sumo.SumoGame.calculatePhaseNumber(800) == 3, "sumo phase 3 number");
        check("Chaos".equals(de.browars.minigame.sumo.SumoGame.calculatePhaseTitle(800)), "sumo phase 3 title");
        check(MinigameHudState.inactive().phaseNumber() == 1, "default inactive HUD phase");

        // Target Rush checks
        var trArena = new de.browars.minigame.targetrush.TargetRushArenaData();
        check(trArena.validationError(null, false) != null, "fresh target rush arena needs setup");
        trArena.setTargetBounds(new BlockPos(0, 60, 10), new BlockPos(10, 70, 40), Level.OVERWORLD);
        trArena.setSpawn(1, 5, 60, 0, 0, 0);
        trArena.setSpawn(2, 5, 60, 0, 0, 0);
        check(trArena.hasTargetArea(), "target rush target area set");
        check(trArena.calculateTier(new BlockPos(5, 65, 12)) == de.browars.minigame.targetrush.ZoneTier.BRONZE, "near target is bronze");
        check(trArena.calculateTier(new BlockPos(5, 65, 38)) == de.browars.minigame.targetrush.ZoneTier.GOLD, "far target is gold");

        // Spleef checks: 2 spawns, up to 7 snow layers, kill area, Best of 5 (First to 3), Efficiency 5 shovel
        check(de.browars.minigame.spleef.SpleefGame.TARGET_SCORE == 7, "spleef target score is 7 (best of 13)");
        check(de.browars.minigame.spleef.SpleefGame.SHOVEL_EFFICIENCY == 5, "spleef shovel efficiency is 5");
        var spleefArena = new de.browars.minigame.spleef.SpleefArenaData();
        check(!spleefArena.isFullyReady(), "fresh spleef arena not ready");
        spleefArena.setSpawn1(0, 81, 0, 0, 0, Level.OVERWORLD);
        spleefArena.setSpawn2(5, 81, 5, 180, 0, Level.OVERWORLD);
        spleefArena.setSnowArea(1, new BlockPos(-5, 80, -5), new BlockPos(10, 80, 10), Level.OVERWORLD);
        spleefArena.setSnowArea(2, new BlockPos(-5, 75, -5), new BlockPos(10, 75, 10), Level.OVERWORLD);
        check(!spleefArena.isFullyReady(), "spleef arena missing kill area");
        spleefArena.setSnowArea(3, new BlockPos(-5, 70, -5), new BlockPos(10, 70, 10), Level.OVERWORLD);
        spleefArena.setSnowArea(4, new BlockPos(-5, 65, -5), new BlockPos(10, 65, 10), Level.OVERWORLD);
        spleefArena.setSnowArea(5, new BlockPos(-5, 60, -5), new BlockPos(10, 60, 10), Level.OVERWORLD);
        spleefArena.setSnowArea(6, new BlockPos(-5, 55, -5), new BlockPos(10, 55, 10), Level.OVERWORLD);
        spleefArena.setSnowArea(7, new BlockPos(-5, 50, -5), new BlockPos(10, 50, 10), Level.OVERWORLD);
        spleefArena.setKillArea(new BlockPos(-8, 40, -8), new BlockPos(13, 40, 13), Level.OVERWORLD);
        check(spleefArena.isFullyReady(), "spleef arena fully ready with 2 spawns, 7 snow areas, and kill area");
        check(spleefArena.getSnowAreas().size() == 7, "spleef arena holds 7 snow layers");
        check(spleefArena.isInAnySnowArea(new BlockPos(0, 80, 0)), "layer 1 block in snow area");
        check(spleefArena.isInAnySnowArea(new BlockPos(0, 75, 0)), "layer 2 block in snow area");
        check(spleefArena.isInAnySnowArea(new BlockPos(0, 70, 0)), "layer 3 block in snow area");
        check(spleefArena.isInAnySnowArea(new BlockPos(0, 50, 0)), "layer 7 block in snow area");
        check(!spleefArena.isInAnySnowArea(new BlockPos(0, 72, 0)), "air between layers not in snow area");
        check(!spleefArena.containsKillPosition(player(0, 81, 0), -64), "spawn on top layer is safe");
        check(spleefArena.containsKillPosition(player(0, 41, 0), -64), "touching kill area eliminates player");
        var spleefEncoded = de.browars.minigame.spleef.SpleefArenaData.CODEC.encodeStart(JsonOps.INSTANCE, spleefArena).getOrThrow();
        var spleefLoaded = de.browars.minigame.spleef.SpleefArenaData.CODEC.parse(JsonOps.INSTANCE, spleefEncoded).getOrThrow();
        check(spleefLoaded.isFullyReady(), "spleef arena codec roundtrip preserves readiness");
        check(spleefLoaded.getSnowAreas().size() == 7, "spleef codec preserves 7 snow areas");
        check(spleefLoaded.isInAnySnowArea(new BlockPos(2, 50, 2)), "spleef codec preserves layer 7");
        check(spleefLoaded.containsKillPosition(player(2, 41, 2), -64), "spleef codec preserves kill area");

        net.minecraft.world.item.Items.DIAMOND_SHOVEL.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        var shovelCountdown = de.browars.minigame.spleef.SpleefGame.createSpleefShovel(null, false);
        check(de.browars.minigame.MinigamePlayerState.isGameItem(shovelCountdown), "spleef shovel marked as game item");
        check(!shovelCountdown.has(net.minecraft.core.component.DataComponents.CAN_BREAK), "countdown shovel cannot break snow yet");
        var shovelActive = de.browars.minigame.spleef.SpleefGame.createSpleefShovel(null, true);
        check(shovelActive.has(net.minecraft.core.component.DataComponents.CAN_BREAK), "active shovel can break snow");

        var vaultArena = new de.browars.minigame.vaultwar.VaultWarArenaData();
        check(!vaultArena.hasArena(), "vault war initially has no arena");
        check(!vaultArena.hasFence(), "vault war initially has no fence");
        vaultArena.setArenaBounds(new BlockPos(10, 60, 10), new BlockPos(30, 80, 30), Level.OVERWORLD);
        vaultArena.setSpawn1(12, 61, 15, 90, 0, Level.OVERWORLD);
        vaultArena.setSpawn2(28, 61, 25, -90, 0, Level.OVERWORLD);
        vaultArena.setFenceBounds(new BlockPos(20, 61, 10), new BlockPos(20, 65, 30));
        check(vaultArena.hasArena(), "vault war arena bounds set");
        check(vaultArena.hasSpawn1() && vaultArena.hasSpawn2(), "vault war spawns set");
        check(vaultArena.hasFence(), "vault war fence set");
        check(vaultArena.isInsideArena(new BlockPos(15, 62, 15)), "inside vault war arena");
        check(!vaultArena.isInsideArena(new BlockPos(5, 62, 15)), "outside vault war arena");
        var vaultEncoded = de.browars.minigame.vaultwar.VaultWarArenaData.CODEC.encodeStart(JsonOps.INSTANCE, vaultArena).getOrThrow();
        var vaultLoaded = de.browars.minigame.vaultwar.VaultWarArenaData.CODEC.parse(JsonOps.INSTANCE, vaultEncoded).getOrThrow();
        check(vaultLoaded.hasArena(), "vault war codec preserves arena");
        check(vaultLoaded.hasSpawn1() && vaultLoaded.hasSpawn2(), "vault war codec preserves spawns");
        check(vaultLoaded.hasFence(), "vault war codec preserves fence");
        check(vaultLoaded.getFenceMin().equals(new BlockPos(20, 61, 10)), "vault war codec preserves fence min");
        check(vaultLoaded.getFenceMax().equals(new BlockPos(20, 65, 30)), "vault war codec preserves fence max");

        System.out.println("All " + checks + " minigame regression checks passed.");
    }

    private static AABB player(double x, double y, double z) {
        return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGame implements Minigame {
        boolean running = true;
        public String getId() { return "test"; }
        public String getDisplayName() { return "test"; }
        public boolean isRunning() { return running; }
        public void tick() {}
        public void stop(String reason) { running = false; }
        public MinigameHudState getHudState() { return MinigameHudState.inactive(); }
        public boolean isPlayerParticipating(UUID id) { return false; }
    }
}
