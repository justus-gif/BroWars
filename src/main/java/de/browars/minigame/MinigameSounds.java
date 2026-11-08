package de.browars.minigame;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/** Sends each announcement exactly once to each participant, even when they stand together. */
public final class MinigameSounds {
    private MinigameSounds() {}

    public static void playWin(MinecraftServer server, boolean matchWon, UUID... participants) {
        play(server, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP), 1.5f, participants);
        play(server, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP), 1.0f, participants);
        if (matchWon) play(server, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP), 1.2f, participants);
    }

    public static void play(MinecraftServer server, Holder<SoundEvent> sound, float pitch, UUID... participants) {
        for (UUID id : participants) {
            if (id == null) continue;
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
                        player.getX(), player.getY(), player.getZ(), 1.0f, pitch, player.getRandom().nextLong()));
            }
        }
    }
}
