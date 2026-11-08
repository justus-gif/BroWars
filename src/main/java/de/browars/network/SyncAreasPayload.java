package de.browars.network;

import de.browars.area.TeleportArea;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncAreasPayload(List<TeleportArea> areas) implements CustomPacketPayload {
    public static final Type<SyncAreasPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_areas"));

    public static final StreamCodec<FriendlyByteBuf, SyncAreasPayload> CODEC = CustomPacketPayload.codec(
            SyncAreasPayload::write,
            SyncAreasPayload::new
    );

    public SyncAreasPayload(FriendlyByteBuf buf) {
        this(readList(buf));
    }

    private static List<TeleportArea> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TeleportArea> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(TeleportArea.readFromBuf(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(areas.size());
        for (TeleportArea area : areas) {
            area.writeToBuf(buf);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
