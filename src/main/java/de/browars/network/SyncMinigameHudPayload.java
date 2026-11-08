package de.browars.network;

import de.browars.minigame.ui.MinigameHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncMinigameHudPayload(MinigameHudState state) implements CustomPacketPayload {
    public static final Type<SyncMinigameHudPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_minigame_hud"));

    public static final StreamCodec<FriendlyByteBuf, SyncMinigameHudPayload> CODEC = CustomPacketPayload.codec(
            SyncMinigameHudPayload::write,
            SyncMinigameHudPayload::new
    );

    public SyncMinigameHudPayload(FriendlyByteBuf buf) {
        this(new MinigameHudState(
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt()
        ));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(state.active());
        buf.writeUtf(state.gameId());
        buf.writeUtf(state.gameTitle());
        buf.writeUtf(state.p1Name());
        buf.writeInt(state.p1Score());
        buf.writeInt(state.p1ColorHex());
        buf.writeUtf(state.p2Name());
        buf.writeInt(state.p2Score());
        buf.writeInt(state.p2ColorHex());
        buf.writeInt(state.maxScore());
        buf.writeInt(state.currentRound());
        buf.writeUtf(state.statusText());
        buf.writeInt(state.timerRemainingTicks());
        buf.writeInt(state.timerTotalTicks());
        buf.writeInt(state.targetColorHex());
        buf.writeUtf(state.targetColorName());
        buf.writeUtf(state.centerTitle());
        buf.writeUtf(state.centerSubtitle());
        buf.writeInt(state.centerTitleColorHex());
        buf.writeInt(state.centerTitleTicks());
        buf.writeInt(state.centerTitleTotalTicks());
        buf.writeInt(state.phaseNumber());
        buf.writeUtf(state.phaseTitle());
        buf.writeInt(state.taggerIndex());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
