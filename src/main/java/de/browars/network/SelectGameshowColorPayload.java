package de.browars.network;

import de.browars.BroWarsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SelectGameshowColorPayload(
        String colorId,
        boolean toggleReady
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectGameshowColorPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BroWarsMod.MOD_ID, "select_gameshow_color"));

    public static final StreamCodec<FriendlyByteBuf, SelectGameshowColorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SelectGameshowColorPayload::colorId,
            ByteBufCodecs.BOOL, SelectGameshowColorPayload::toggleReady,
            SelectGameshowColorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
