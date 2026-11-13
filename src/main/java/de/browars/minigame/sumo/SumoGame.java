package de.browars.minigame.sumo;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.DuelScore;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.MinigamePlayerState;
import de.browars.minigame.TeamColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public final class SumoGame extends AbstractDuelGame<SumoArenaData> {
    /** Small, fixed push so consecutive stick hits are useful without launching players. */
    public static final double HIT_KNOCKBACK = 0.28;

    private double lastKnockback = HIT_KNOCKBACK;

    public SumoGame(MinecraftServer server, ServerLevel level, SumoArenaData arena,
                    ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }
    @Override public String getId() { return "sumo"; }
    @Override public String getDisplayName() { return "Sumo Ringen"; }
    @Override protected String startInstruction() { return "Stoße deinen Gegner aus dem Ring!"; }
    public boolean isFightActive() { return isRoundActive(); }

    public static double calculateKnockback(int playTicks) {
        if (playTicks < 400) {
            return HIT_KNOCKBACK;
        }
        if (playTicks < 800) {
            int step = (playTicks - 400) / 100;
            double kb = 0.45 + step * 0.10;
            return Math.round(kb * 100.0) / 100.0;
        }
        int step = (playTicks - 800) / 80;
        double kb = 0.85 + step * 0.15;
        return Math.min(1.80, Math.round(kb * 100.0) / 100.0);
    }

    public static int calculatePhaseNumber(int playTicks) {
        if (playTicks < 400) return 1;
        if (playTicks < 800) return 2;
        return 3;
    }

    public static String calculatePhaseTitle(int playTicks) {
        return switch (calculatePhaseNumber(playTicks)) {
            case 2 -> "Eskalation";
            case 3 -> "Chaos";
            default -> "Aufwärmen";
        };
    }

    public double getCurrentKnockback() {
        return calculateKnockback(playTicks);
    }

    @Override
    protected int phaseNumber() {
        return calculatePhaseNumber(playTicks);
    }

    @Override
    protected String phaseTitle() {
        return calculatePhaseTitle(playTicks);
    }

    @Override
    protected String matchStatus() {
        double kb = getCurrentKnockback();
        if (kb > HIT_KNOCKBACK) {
            int pct = (int) Math.round((kb / HIT_KNOCKBACK - 1.0) * 100.0);
            return "§6KB +" + pct + "%";
        }
        return "";
    }

    public boolean registerHit(UUID attacker) {
        ServerPlayer player = server.getPlayerList().getPlayer(attacker);
        return isFightActive() && player != null && getOpponent(player) != null;
    }

    @Override
    protected void onRoundStarted() {
        lastKnockback = HIT_KNOCKBACK;
    }

    @Override protected void tickRound(ServerPlayer first, ServerPlayer second) {
        if (finishRound(arena.isPlayerInLossZone(first), second != null && arena.isPlayerInLossZone(second), "")) return;
        double currentKb = getCurrentKnockback();
        if (currentKb > lastKnockback) {
            lastKnockback = currentKb;
            playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL, 1.4f);
            MinigameManager.getInstance().broadcastHud(server, getHudState());
        }
        if (playTicks % 20 == 0) removeDroppedEquipment();
    }
    @Override protected void clearRound() {
        removeDroppedEquipment();
        lastKnockback = HIT_KNOCKBACK;
    }

    @Override protected void equipExtra(ServerPlayer player) {
        var speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null && speed.getBaseValue() != 100) speed.setBaseValue(100);
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.STICK) && MinigamePlayerState.isGameItem(held)) return;
        ItemStack stick = new ItemStack(Items.STICK);
        stick.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lSumo-Schläger"));
        level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(r -> r.get(Enchantments.KNOCKBACK))
                .ifPresent(enchantment -> stick.enchant(enchantment, 1));
        MinigamePlayerState.markGameItem(stick);
        player.setItemSlot(EquipmentSlot.MAINHAND, stick);
    }

    private void removeDroppedEquipment() {
        AABB bounds = new AABB(arena.getLossZoneMin()).minmax(new AABB(arena.getLossZoneMax())).inflate(20);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (MinigamePlayerState.isGameItem(item.getItem())) item.discard();
        }
    }
}
