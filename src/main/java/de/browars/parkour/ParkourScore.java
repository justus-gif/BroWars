package de.browars.parkour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Locale;
import java.util.UUID;

public record ParkourScore(UUID playerUuid, String playerName, long timeMillis, long timestamp) {
    public static final Codec<ParkourScore> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("playerUuid").forGetter(ParkourScore::playerUuid),
            Codec.STRING.fieldOf("playerName").forGetter(ParkourScore::playerName),
            Codec.LONG.fieldOf("timeMillis").forGetter(ParkourScore::timeMillis),
            Codec.LONG.fieldOf("timestamp").forGetter(ParkourScore::timestamp)
    ).apply(inst, ParkourScore::new));

    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long hundredths = (millis % 1000) / 10;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", minutes, seconds, hundredths);
    }
}
