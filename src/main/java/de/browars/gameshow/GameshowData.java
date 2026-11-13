package de.browars.gameshow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class GameshowData {

    public record AreaBox(BlockPos min, BlockPos max, ResourceKey<Level> dimension, boolean set) {
        public static final AreaBox EMPTY = new AreaBox(BlockPos.ZERO, BlockPos.ZERO, Level.OVERWORLD, false);

        public static final Codec<AreaBox> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("min").forGetter(AreaBox::min),
                BlockPos.CODEC.fieldOf("max").forGetter(AreaBox::max),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(AreaBox::dimension),
                Codec.BOOL.fieldOf("set").forGetter(AreaBox::set)
        ).apply(inst, AreaBox::new));

        public static AreaBox of(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
            BlockPos minPos = new BlockPos(
                    Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ())
            );
            BlockPos maxPos = new BlockPos(
                    Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ())
            );
            return new AreaBox(minPos, maxPos, dim, true);
        }

        public AABB toAABB() {
            if (!set) return null;
            return new AABB(
                    min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0, Math.max(max.getY() + 2.0, min.getY() + 4.0), max.getZ() + 1.0
            );
        }
    }

    public record SpawnPoint(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dimension, boolean set) {
        public static final SpawnPoint EMPTY = new SpawnPoint(0.0, 64.0, 0.0, 0.0f, 0.0f, Level.OVERWORLD, false);

        public static final Codec<SpawnPoint> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(SpawnPoint::x),
                Codec.DOUBLE.fieldOf("y").forGetter(SpawnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(SpawnPoint::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(SpawnPoint::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(SpawnPoint::pitch),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SpawnPoint::dimension),
                Codec.BOOL.fieldOf("set").forGetter(SpawnPoint::set)
        ).apply(inst, SpawnPoint::new));
    }

    public record PlayedGameEntry(
            int gameNumber,
            String gameId,
            String gameTitle,
            String winnerName,
            String winnerColorId,
            String scoreSummary
    ) {
        public static final Codec<PlayedGameEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("gameNumber").forGetter(PlayedGameEntry::gameNumber),
                Codec.STRING.fieldOf("gameId").forGetter(PlayedGameEntry::gameId),
                Codec.STRING.fieldOf("gameTitle").forGetter(PlayedGameEntry::gameTitle),
                Codec.STRING.fieldOf("winnerName").forGetter(PlayedGameEntry::winnerName),
                Codec.STRING.fieldOf("winnerColorId").forGetter(PlayedGameEntry::winnerColorId),
                Codec.STRING.fieldOf("scoreSummary").forGetter(PlayedGameEntry::scoreSummary)
        ).apply(inst, PlayedGameEntry::new));

        public static final StreamCodec<FriendlyByteBuf, PlayedGameEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlayedGameEntry::gameNumber,
                ByteBufCodecs.STRING_UTF8, PlayedGameEntry::gameId,
                ByteBufCodecs.STRING_UTF8, PlayedGameEntry::gameTitle,
                ByteBufCodecs.STRING_UTF8, PlayedGameEntry::winnerName,
                ByteBufCodecs.STRING_UTF8, PlayedGameEntry::winnerColorId,
                ByteBufCodecs.STRING_UTF8, PlayedGameEntry::scoreSummary,
                PlayedGameEntry::new
        );
    }

    public record ShowRecord(
            String p1Name,
            String p1ColorId,
            int p1Wins,
            String p2Name,
            String p2ColorId,
            int p2Wins,
            int targetGames,
            boolean finished,
            String overallWinner,
            List<PlayedGameEntry> playedGames
    ) {
        public static final ShowRecord EMPTY = new ShowRecord("", "red", 0, "", "blue", 0, 7, false, "", List.of());

        public static final Codec<ShowRecord> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("p1Name").forGetter(ShowRecord::p1Name),
                Codec.STRING.fieldOf("p1ColorId").forGetter(ShowRecord::p1ColorId),
                Codec.INT.fieldOf("p1Wins").forGetter(ShowRecord::p1Wins),
                Codec.STRING.fieldOf("p2Name").forGetter(ShowRecord::p2Name),
                Codec.STRING.fieldOf("p2ColorId").forGetter(ShowRecord::p2ColorId),
                Codec.INT.fieldOf("p2Wins").forGetter(ShowRecord::p2Wins),
                Codec.INT.fieldOf("targetGames").forGetter(ShowRecord::targetGames),
                Codec.BOOL.fieldOf("finished").forGetter(ShowRecord::finished),
                Codec.STRING.fieldOf("overallWinner").forGetter(ShowRecord::overallWinner),
                PlayedGameEntry.CODEC.listOf().fieldOf("playedGames").forGetter(ShowRecord::playedGames)
        ).apply(inst, ShowRecord::new));
    }

    public static final Codec<GameshowData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SpawnPoint.CODEC.optionalFieldOf("mainSpawn", SpawnPoint.EMPTY).forGetter(GameshowData::getMainSpawn),
            AreaBox.CODEC.optionalFieldOf("startArea", AreaBox.EMPTY).forGetter(GameshowData::getStartArea),
            SpawnPoint.CODEC.optionalFieldOf("blackRoomSpawn", SpawnPoint.EMPTY).forGetter(GameshowData::getBlackRoomSpawn),
            SpawnPoint.CODEC.optionalFieldOf("lobbySpawn", SpawnPoint.EMPTY).forGetter(GameshowData::getLobbySpawn),
            AreaBox.CODEC.optionalFieldOf("lobbyArea", AreaBox.EMPTY).forGetter(GameshowData::getLobbyArea),
            SpawnPoint.CODEC.optionalFieldOf("podium1Pos", SpawnPoint.EMPTY).forGetter(GameshowData::getPodium1Pos),
            SpawnPoint.CODEC.optionalFieldOf("podium2Pos", SpawnPoint.EMPTY).forGetter(GameshowData::getPodium2Pos),
            SpawnPoint.CODEC.optionalFieldOf("podiumStatuePos", SpawnPoint.EMPTY).forGetter(d -> SpawnPoint.EMPTY),
            SpawnPoint.CODEC.optionalFieldOf("podiumViewerSpawn", SpawnPoint.EMPTY).forGetter(GameshowData::getPodiumViewerSpawn),
            BlockPos.CODEC.listOf().optionalFieldOf("boardBlocks", List.of()).forGetter(GameshowData::getBoardBlocks),
            ShowRecord.CODEC.optionalFieldOf("lastShow", ShowRecord.EMPTY).forGetter(GameshowData::getLastShow)
    ).apply(inst, (mainSpawn, startArea, blackRoom, lobbySpawn, lobbyArea, podium1, podium2, legacyStatue, podiumViewer, boards, lastShow) -> {
        SpawnPoint resolvedPodium1 = (podium1 != null && podium1.set()) ? podium1 : (legacyStatue != null ? legacyStatue : SpawnPoint.EMPTY);
        return new GameshowData(mainSpawn, startArea, blackRoom, lobbySpawn, lobbyArea, resolvedPodium1, podium2, podiumViewer, boards, lastShow);
    }));

    private SpawnPoint mainSpawn = SpawnPoint.EMPTY;
    private AreaBox startArea = AreaBox.EMPTY;
    private SpawnPoint blackRoomSpawn = SpawnPoint.EMPTY;
    private SpawnPoint lobbySpawn = SpawnPoint.EMPTY;
    private AreaBox lobbyArea = AreaBox.EMPTY;
    private SpawnPoint podium1Pos = SpawnPoint.EMPTY;
    private SpawnPoint podium2Pos = SpawnPoint.EMPTY;
    private SpawnPoint podiumViewerSpawn = SpawnPoint.EMPTY;
    private final List<BlockPos> boardBlocks = new ArrayList<>();
    private ShowRecord lastShow = ShowRecord.EMPTY;

    public GameshowData() {}

    public GameshowData(
            SpawnPoint mainSpawn,
            AreaBox startArea,
            SpawnPoint blackRoomSpawn,
            SpawnPoint lobbySpawn,
            AreaBox lobbyArea,
            SpawnPoint podium1Pos,
            SpawnPoint podium2Pos,
            SpawnPoint podiumViewerSpawn,
            List<BlockPos> boardBlocks,
            ShowRecord lastShow
    ) {
        this.mainSpawn = mainSpawn != null ? mainSpawn : SpawnPoint.EMPTY;
        this.startArea = startArea != null ? startArea : AreaBox.EMPTY;
        this.blackRoomSpawn = blackRoomSpawn != null ? blackRoomSpawn : SpawnPoint.EMPTY;
        this.lobbySpawn = lobbySpawn != null ? lobbySpawn : SpawnPoint.EMPTY;
        this.lobbyArea = lobbyArea != null ? lobbyArea : AreaBox.EMPTY;
        this.podium1Pos = podium1Pos != null ? podium1Pos : SpawnPoint.EMPTY;
        this.podium2Pos = podium2Pos != null ? podium2Pos : SpawnPoint.EMPTY;
        this.podiumViewerSpawn = podiumViewerSpawn != null ? podiumViewerSpawn : SpawnPoint.EMPTY;
        if (boardBlocks != null) this.boardBlocks.addAll(boardBlocks);
        this.lastShow = lastShow != null ? lastShow : ShowRecord.EMPTY;
    }

    public SpawnPoint getMainSpawn() { return mainSpawn; }
    public void setMainSpawn(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.mainSpawn = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public AreaBox getStartArea() { return startArea; }
    public void setStartArea(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        this.startArea = AreaBox.of(p1, p2, dim);
    }

    public SpawnPoint getBlackRoomSpawn() { return blackRoomSpawn; }
    public void setBlackRoomSpawn(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.blackRoomSpawn = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public SpawnPoint getLobbySpawn() { return lobbySpawn; }
    public void setLobbySpawn(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.lobbySpawn = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public AreaBox getLobbyArea() { return lobbyArea; }
    public void setLobbyArea(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        this.lobbyArea = AreaBox.of(p1, p2, dim);
    }

    public SpawnPoint getPodium1Pos() { return podium1Pos; }
    public void setPodium1Pos(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.podium1Pos = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public SpawnPoint getPodium2Pos() { return podium2Pos; }
    public void setPodium2Pos(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.podium2Pos = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public SpawnPoint getPodiumViewerSpawn() { return podiumViewerSpawn; }
    public void setPodiumViewerSpawn(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.podiumViewerSpawn = new SpawnPoint(x, y, z, yaw, pitch, dim, true);
    }

    public List<BlockPos> getBoardBlocks() { return boardBlocks; }
    public void addBoardBlock(BlockPos pos) {
        BlockPos imm = pos.immutable();
        if (!boardBlocks.contains(imm)) {
            boardBlocks.add(imm);
        }
    }
    public void removeBoardBlock(BlockPos pos) {
        boardBlocks.remove(pos);
    }

    public ShowRecord getLastShow() { return lastShow; }
    public void setLastShow(ShowRecord lastShow) {
        this.lastShow = lastShow != null ? lastShow : ShowRecord.EMPTY;
    }
}
