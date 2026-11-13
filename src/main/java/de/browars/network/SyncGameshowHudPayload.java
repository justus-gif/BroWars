package de.browars.network;

import de.browars.BroWarsMod;
import de.browars.gameshow.GameshowData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncGameshowHudPayload(
        boolean active,
        String phase,
        int phaseTicksRemaining,
        int phaseTotalTicks,
        int currentGameNumber,
        int totalGames,
        String p1Name,
        String p1ColorId,
        int p1Wins,
        String p2Name,
        String p2ColorId,
        int p2Wins,
        List<String> roulettePoolIds,
        int rouletteTargetIndex,
        String activeGameId,
        String lastWinnerName,
        String lastWinnerColorId,
        String lastScoreSummary,
        List<GameshowData.PlayedGameEntry> playedGames
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncGameshowHudPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BroWarsMod.MOD_ID, "sync_gameshow_hud"));

    public static final SyncGameshowHudPayload INACTIVE = new SyncGameshowHudPayload(
            false, "INACTIVE", 0, 0, 0, 7,
            "", "red", 0, "", "blue", 0,
            List.of(), 0, "", "", "", "", List.of()
    );

    public static final StreamCodec<FriendlyByteBuf, SyncGameshowHudPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.active);
                buf.writeUtf(p.phase);
                buf.writeVarInt(p.phaseTicksRemaining);
                buf.writeVarInt(p.phaseTotalTicks);
                buf.writeVarInt(p.currentGameNumber);
                buf.writeVarInt(p.totalGames);
                buf.writeUtf(p.p1Name);
                buf.writeUtf(p.p1ColorId);
                buf.writeVarInt(p.p1Wins);
                buf.writeUtf(p.p2Name);
                buf.writeUtf(p.p2ColorId);
                buf.writeVarInt(p.p2Wins);
                buf.writeVarInt(p.roulettePoolIds.size());
                for (String id : p.roulettePoolIds) {
                    buf.writeUtf(id);
                }
                buf.writeVarInt(p.rouletteTargetIndex);
                buf.writeUtf(p.activeGameId);
                buf.writeUtf(p.lastWinnerName);
                buf.writeUtf(p.lastWinnerColorId);
                buf.writeUtf(p.lastScoreSummary);
                buf.writeVarInt(p.playedGames.size());
                for (GameshowData.PlayedGameEntry entry : p.playedGames) {
                    GameshowData.PlayedGameEntry.STREAM_CODEC.encode(buf, entry);
                }
            },
            buf -> {
                boolean active = buf.readBoolean();
                String phase = buf.readUtf();
                int rem = buf.readVarInt();
                int tot = buf.readVarInt();
                int curGame = buf.readVarInt();
                int totGames = buf.readVarInt();
                String p1Name = buf.readUtf();
                String p1Col = buf.readUtf();
                int p1Wins = buf.readVarInt();
                String p2Name = buf.readUtf();
                String p2Col = buf.readUtf();
                int p2Wins = buf.readVarInt();
                int poolSize = buf.readVarInt();
                List<String> pool = new ArrayList<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    pool.add(buf.readUtf());
                }
                int targetIdx = buf.readVarInt();
                String activeId = buf.readUtf();
                String lastWinner = buf.readUtf();
                String lastWinCol = buf.readUtf();
                String lastSummary = buf.readUtf();
                int playedSize = buf.readVarInt();
                List<GameshowData.PlayedGameEntry> played = new ArrayList<>(playedSize);
                for (int i = 0; i < playedSize; i++) {
                    played.add(GameshowData.PlayedGameEntry.STREAM_CODEC.decode(buf));
                }
                return new SyncGameshowHudPayload(
                        active, phase, rem, tot, curGame, totGames,
                        p1Name, p1Col, p1Wins, p2Name, p2Col, p2Wins,
                        pool, targetIdx, activeId, lastWinner, lastWinCol, lastSummary, played
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
