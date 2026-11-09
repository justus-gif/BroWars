package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record OpenParkourLeaderboardPayload(
        BlockPos pos,
        List<Entry> entries
) implements CustomPacketPayload {
    public record Entry(int index, String playerName, String timeFormatted, long timestamp) {
        public static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readVarInt(), buf.readUtf(256), buf.readUtf(64), buf.readVarLong());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeUtf(playerName, 256);
            buf.writeUtf(timeFormatted, 64);
            buf.writeVarLong(timestamp);
        }
    }

    public static final Type<OpenParkourLeaderboardPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_parkour_leaderboard"));

    public static final StreamCodec<FriendlyByteBuf, OpenParkourLeaderboardPayload> CODEC = CustomPacketPayload.codec(
            OpenParkourLeaderboardPayload::write,
            OpenParkourLeaderboardPayload::new
    );

    public OpenParkourLeaderboardPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                readEntries(buf)
        );
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(Entry.read(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.write(buf);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
