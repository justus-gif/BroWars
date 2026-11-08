package de.browars.minigame.tnttag;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.TeamColor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;
import java.util.UUID;

public class TntTagGame extends AbstractDuelGame<TntTagArenaData> {

    private static final int INITIAL_EXPLOSION_TICKS = 600; // 30 seconds
    private static final int BONUS_TICKS_PER_PASS = 20;     // +1 second per pass
    private static final int[] ROULETTE_TICKS = {60, 57, 54, 51, 48, 45, 42, 39, 36, 32, 28, 24, 20, 15, 10};

    private final Random random = new Random();
    private int explosionTicks = INITIAL_EXPLOSION_TICKS;
    private int explosionDelayTicks = -1;
    private int currentTagger = 1; // 1 for P1, 2 for P2
    private int rouletteStep = 0;

    public TntTagGame(MinecraftServer server, ServerLevel level, TntTagArenaData arena,
                      ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override
    public String getId() {
        return "tnttag";
    }

    @Override
    public String getDisplayName() {
        return "TNT Tag";
    }

    @Override
    protected String startInstruction() {
        return "Entkomme dem TNT oder schlage deinen Gegner!";
    }

    @Override
    protected boolean hasSpawnCampEjector() {
        return true;
    }

    @Override
    protected boolean isPlayerInSpawnChamber(ServerPlayer player, int spawnNumber) {
        return arena.isInSpawnChamber(player, spawnNumber);
    }

    @Override
    protected void prepareArena() {
        currentTagger = (isSoloMode || random.nextBoolean()) ? 1 : 2;
        rouletteStep = 0;
        explosionDelayTicks = -1;
    }

    private static boolean isRouletteTick(int ticks) {
        for (int t : ROULETTE_TICKS) {
            if (t == ticks) return true;
        }
        return false;
    }

    @Override
    protected boolean onCountdownTick(int phaseTicks) {
        if (isSoloMode) return false;
        if (phaseTicks <= 60 && phaseTicks > 10) {
            if (isRouletteTick(phaseTicks)) {
                rouletteStep++;
                String candidate = (rouletteStep % 2 == 0) ? p1Name : p2Name;
                int color = (rouletteStep % 2 == 0) ? p1Team.hexColor : p2Team.hexColor;
                setCenterTitle(candidate, "Auslosung...", color, 8);
                playSound(SoundEvents.UI_BUTTON_CLICK, 1.1f + (rouletteStep % 4) * 0.15f);
            }
            return true;
        } else if (phaseTicks == 10) {
            String lockedTagger = currentTagger == 1 ? p1Name : p2Name;
            int color = currentTagger == 1 ? p1Team.hexColor : p2Team.hexColor;
            setCenterTitle(lockedTagger, "START-TAGGER", color, 25);
            playSound(SoundEvents.NOTE_BLOCK_PLING, 1.8f);
            playSound(SoundEvents.TNT_PRIMED, 1.0f);
            return true;
        } else if (phaseTicks < 10) {
            return true;
        }
        return false;
    }

    @Override
    protected void onRoundStarted() {
        explosionTicks = INITIAL_EXPLOSION_TICKS;
        explosionDelayTicks = -1;

        ServerPlayer tagger = getTaggerPlayer();
        ServerPlayer runner = getRunnerPlayer();

        if (tagger != null) {
            equipTntHat(tagger);
            // Starting tagger gets 3 seconds of Speed II
            tagger.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false, true));
        }
        if (runner != null) {
            runner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }

        String taggerName = tagger != null ? tagger.getPlainTextName() : (currentTagger == 1 ? p1Name : p2Name);
        message("§c§l" + taggerName + " §ehat das TNT erhalten! (3s Speed-Boost)");
        playSound(SoundEvents.TNT_PRIMED, 1.0f);
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        boolean firstOut = (arena.hasArena() && !arena.containsPlayer(first)) || first.getY() < level.getMinY() + 1;
        boolean secondOut = second != null && ((arena.hasArena() && !arena.containsPlayer(second)) || second.getY() < level.getMinY() + 1);
        if (firstOut || secondOut) {
            finishRound(firstOut, secondOut, "Aus der Arena gefallen!");
            return;
        }

        if (explosionDelayTicks >= 0) {
            explosionDelayTicks--;
            ServerPlayer tagger = getTaggerPlayer();
            if (tagger != null) {
                tagger.setDeltaMovement(0, 0, 0);
                level.sendParticles(ParticleTypes.SMOKE, tagger.getX(), tagger.getY() + 1.0, tagger.getZ(), 4, 0.2, 0.2, 0.2, 0.04);
            }
            if (explosionDelayTicks <= 0) {
                boolean p1Lost = currentTagger == 1;
                boolean p2Lost = currentTagger == 2;
                finishRound(p1Lost, p2Lost, "TNT explodiert");
            }
            return;
        }

        explosionTicks--;

        ServerPlayer tagger = currentTagger == 1 ? first : second;
        ServerPlayer runner = currentTagger == 1 ? second : first;

        if (tagger != null) {
            // Keep TNT hat equipped
            ItemStack headItem = tagger.getItemBySlot(EquipmentSlot.HEAD);
            if (!headItem.is(Blocks.TNT.asItem())) {
                equipTntHat(tagger);
            }

            // Passive slight speed advantage (Speed I) for the tagger to catch up
            if (!tagger.hasEffect(MobEffects.SPEED)) {
                tagger.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false, false));
            }

            // Spark and smoke particles above tagger's head
            level.sendParticles(ParticleTypes.SMOKE, tagger.getX(), tagger.getY() + 2.1, tagger.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
            level.sendParticles(ParticleTypes.FLAME, tagger.getX(), tagger.getY() + 2.15, tagger.getZ(), 1, 0.05, 0.05, 0.05, 0.01);

            // Audio cues: ticking accelerating near explosion
            if (explosionTicks <= 100) {
                int interval = explosionTicks <= 40 ? 5 : 10;
                if (explosionTicks % interval == 0) {
                    level.playSound(null, tagger.getX(), tagger.getY(), tagger.getZ(),
                            SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.2f, 1.8f);
                }
            } else if (explosionTicks % 20 == 0) {
                level.playSound(null, tagger.getX(), tagger.getY(), tagger.getZ(),
                        SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }

        // Check if explosion timer expired
        if (explosionTicks <= 0) {
            triggerExplosion(tagger);
        }
    }

    private void triggerExplosion(ServerPlayer tagger) {
        explosionDelayTicks = 30; // 1.5s delay before respawn
        if (tagger != null) {
            level.explode(null, tagger.getX(), tagger.getY() + 0.5, tagger.getZ(), 0.0f, false, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, tagger.getX(), tagger.getY() + 1.0, tagger.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, tagger.getX(), tagger.getY(), tagger.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 1.0f);
        }

        String loserName = currentTagger == 1 ? p1Name : p2Name;
        setCenterTitle("§c§lBOOM!", loserName + " ist explodiert!", 0xFFEF4444, 40);
        message("§c§lBOOM! Das TNT von " + loserName + " ist explodiert!");
    }

    public void onPlayerAttack(ServerPlayer attacker, ServerPlayer victim) {
        if (!isRoundActive() || explosionDelayTicks >= 0) return;
        if (!isPlayerParticipating(attacker.getUUID()) || !isPlayerParticipating(victim.getUUID())) return;

        int attackerIndex = attacker.getUUID().equals(p1Uuid) ? 1 : 2;
        int victimIndex = victim.getUUID().equals(p1Uuid) ? 1 : 2;

        // Only the current tagger can pass the TNT
        if (attackerIndex != currentTagger) return;

        // Pass TNT to the victim
        currentTagger = victimIndex;
        explosionTicks = Math.min(INITIAL_EXPLOSION_TICKS, explosionTicks + BONUS_TICKS_PER_PASS); // Add +1s, capped at max 20s

        // Update equipment
        attacker.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        attacker.removeEffect(MobEffects.SPEED);
        equipTntHat(victim);

        // Sound and visual feedback
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.5f, 2.0f);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.2f, 1.2f);
        level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY() + 1.5, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.05);

        // Feedback in chat (no actionbar message above hotbar, HUD & vignette handle on-screen indicators)
        message("§c" + attacker.getPlainTextName() + " §ehat das TNT an §c" + victim.getPlainTextName() + " §eweitergegeben! (+1s)");
    }

    private void equipTntHat(ServerPlayer player) {
        ItemStack tntHat = new ItemStack(Blocks.TNT);
        tntHat.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        level.registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .flatMap(r -> r.get(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE))
                .ifPresent(enchantment -> tntHat.enchant(enchantment, 1));
        de.browars.minigame.MinigamePlayerState.markGameItem(tntHat);
        player.setItemSlot(EquipmentSlot.HEAD, tntHat);
    }

    @Override
    protected void clearRound() {
        explosionDelayTicks = -1;
        ServerPlayer first = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer second = p2Uuid == null ? null : server.getPlayerList().getPlayer(p2Uuid);
        if (first != null) {
            first.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            first.removeAllEffects();
        }
        if (second != null) {
            second.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            second.removeAllEffects();
        }
    }

    @Override
    protected int timerRemaining() {
        return Math.max(0, explosionTicks);
    }

    @Override
    protected int timerTotal() {
        return INITIAL_EXPLOSION_TICKS;
    }

    @Override
    protected int taggerIndex() {
        return isRoundActive() ? currentTagger : 0;
    }

    @Override
    protected int phaseNumber() {
        if (explosionTicks > 360) return 1;
        if (explosionTicks > 140) return 2;
        return 3;
    }

    @Override
    protected String phaseTitle() {
        return switch (phaseNumber()) {
            case 2 -> "Eskalation";
            case 3 -> "Chaos";
            default -> "Aufwärmen";
        };
    }

    private ServerPlayer getTaggerPlayer() {
        return currentTagger == 1 ? server.getPlayerList().getPlayer(p1Uuid)
                : (p2Uuid != null ? server.getPlayerList().getPlayer(p2Uuid) : null);
    }

    private ServerPlayer getRunnerPlayer() {
        return currentTagger == 1 ? (p2Uuid != null ? server.getPlayerList().getPlayer(p2Uuid) : null)
                : server.getPlayerList().getPlayer(p1Uuid);
    }
}
