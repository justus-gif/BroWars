package de.browars.area;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class TeleportArea {
    public static final Codec<TeleportArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TeleportArea::getId),
            Codec.STRING.fieldOf("name").forGetter(TeleportArea::getName),
            BlockPos.CODEC.fieldOf("minPos").forGetter(TeleportArea::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(TeleportArea::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TeleportArea::getDimension),
            Codec.DOUBLE.fieldOf("targetX").forGetter(TeleportArea::getTargetX),
            Codec.DOUBLE.fieldOf("targetY").forGetter(TeleportArea::getTargetY),
            Codec.DOUBLE.fieldOf("targetZ").forGetter(TeleportArea::getTargetZ),
            Codec.FLOAT.fieldOf("targetYaw").forGetter(TeleportArea::getTargetYaw),
            Codec.FLOAT.fieldOf("targetPitch").forGetter(TeleportArea::getTargetPitch),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("targetDimension").forGetter(TeleportArea::getTargetDimension),
            Codec.INT.fieldOf("colorHex").forGetter(TeleportArea::getColorHex),
            Codec.BOOL.fieldOf("visibleInSurvival").forGetter(TeleportArea::isVisibleInSurvival),
            Codec.BOOL.fieldOf("showNameHologram").forGetter(TeleportArea::isShowNameHologram)
    ).apply(instance, (id, name, min, max, dim, tx, ty, tz, tyaw, tpitch, tdim, col, vis, holo) ->
            new TeleportArea(id, name, min, max, dim, tx, ty, tz, tyaw, tpitch, tdim, col, vis, holo, null)
    ));

    public static final StreamCodec<FriendlyByteBuf, TeleportArea> STREAM_CODEC = StreamCodec.ofMember(
            TeleportArea::writeToBuf,
            TeleportArea::readFromBuf
    );

    private final String id;
    private String name;
    private BlockPos minPos;
    private BlockPos maxPos;
    private ResourceKey<Level> dimension;
    private double targetX;
    private double targetY;
    private double targetZ;
    private float targetYaw;
    private float targetPitch;
    private ResourceKey<Level> targetDimension;
    private int colorHex;
    private boolean visibleInSurvival;
    private boolean showNameHologram;
    private UUID textDisplayUuid;

    public TeleportArea(String id, String name, BlockPos pos1, BlockPos pos2, ResourceKey<Level> dimension,
                        double targetX, double targetY, double targetZ, float targetYaw, float targetPitch,
                        ResourceKey<Level> targetDimension, int colorHex, boolean visibleInSurvival,
                        boolean showNameHologram, UUID textDisplayUuid) {
        this.id = id != null && !id.isEmpty() ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "";
        this.dimension = dimension != null ? dimension : Level.OVERWORLD;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.targetDimension = targetDimension != null ? targetDimension : this.dimension;
        this.colorHex = colorHex;
        this.visibleInSurvival = visibleInSurvival;
        this.showNameHologram = showNameHologram;
        this.textDisplayUuid = textDisplayUuid;
        setBounds(pos1, pos2);
    }

    public void setBounds(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null) pos1 = BlockPos.ZERO;
        if (pos2 == null) pos2 = BlockPos.ZERO;
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        this.minPos = new BlockPos(minX, minY, minZ);
        this.maxPos = new BlockPos(maxX, maxY, maxZ);
    }

    public boolean contains(double x, double y, double z) {
        return x >= minPos.getX() && x <= (maxPos.getX() + 1.0)
                && y >= minPos.getY() && y <= (maxPos.getY() + 1.0)
                && z >= minPos.getZ() && z <= (maxPos.getZ() + 1.0);
    }

    public boolean intersects(AABB box) {
        return getBoundingBox().intersects(box);
    }

    public AABB getBoundingBox() {
        return new AABB(
                minPos.getX(), minPos.getY(), minPos.getZ(),
                maxPos.getX() + 1.0, maxPos.getY() + 1.0, maxPos.getZ() + 1.0
        );
    }

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name != null ? name : "");
        buf.writeBlockPos(minPos);
        buf.writeBlockPos(maxPos);
        buf.writeUtf(dimension.identifier().toString());
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeUtf(targetDimension.identifier().toString());
        buf.writeInt(colorHex);
        buf.writeBoolean(visibleInSurvival);
        buf.writeBoolean(showNameHologram);
    }

    public static TeleportArea readFromBuf(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(buf.readUtf()));
        double tx = buf.readDouble();
        double ty = buf.readDouble();
        double tz = buf.readDouble();
        float tyaw = buf.readFloat();
        float tpitch = buf.readFloat();
        ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(buf.readUtf()));
        int color = buf.readInt();
        boolean visible = buf.readBoolean();
        boolean showHolo = buf.readBoolean();
        return new TeleportArea(id, name, min, max, dim, tx, ty, tz, tyaw, tpitch, targetDim, color, visible, showHolo, null);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BlockPos getMinPos() { return minPos; }
    public BlockPos getMaxPos() { return maxPos; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public void setDimension(ResourceKey<Level> dimension) { this.dimension = dimension; }
    public double getTargetX() { return targetX; }
    public void setTargetX(double targetX) { this.targetX = targetX; }
    public double getTargetY() { return targetY; }
    public void setTargetY(double targetY) { this.targetY = targetY; }
    public double getTargetZ() { return targetZ; }
    public void setTargetZ(double targetZ) { this.targetZ = targetZ; }
    public float getTargetYaw() { return targetYaw; }
    public void setTargetYaw(float targetYaw) { this.targetYaw = targetYaw; }
    public float getTargetPitch() { return targetPitch; }
    public void setTargetPitch(float targetPitch) { this.targetPitch = targetPitch; }
    public ResourceKey<Level> getTargetDimension() { return targetDimension; }
    public void setTargetDimension(ResourceKey<Level> targetDimension) { this.targetDimension = targetDimension; }
    public int getColorHex() { return colorHex; }
    public void setColorHex(int colorHex) { this.colorHex = colorHex; }
    public boolean isVisibleInSurvival() { return visibleInSurvival; }
    public void setVisibleInSurvival(boolean visibleInSurvival) { this.visibleInSurvival = visibleInSurvival; }
    public boolean isShowNameHologram() { return showNameHologram; }
    public void setShowNameHologram(boolean showNameHologram) { this.showNameHologram = showNameHologram; }
    public UUID getTextDisplayUuid() { return textDisplayUuid; }
    public void setTextDisplayUuid(UUID textDisplayUuid) { this.textDisplayUuid = textDisplayUuid; }
}
