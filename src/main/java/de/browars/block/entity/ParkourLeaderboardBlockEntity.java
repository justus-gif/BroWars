package de.browars.block.entity;

import de.browars.BroWarsMod;
import de.browars.mixin.DisplayAccess;
import de.browars.mixin.TextDisplayAccess;
import de.browars.parkour.ParkourSavedData;
import de.browars.parkour.ParkourScore;
import de.browars.util.HologramHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.UUID;

public class ParkourLeaderboardBlockEntity extends BlockEntity {
    private UUID textDisplayUuid;
    private int tickCount = 0;

    public ParkourLeaderboardBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.PARKOUR_LEADERBOARD_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ParkourLeaderboardBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        be.tickCount++;
        if (be.tickCount % 20 == 0) {
            be.updateHologram(serverLevel);
        }
    }

    public void updateHologram(ServerLevel level) {
        if (!level.hasChunkAt(worldPosition)) return;

        ParkourSavedData savedData = ParkourSavedData.get(level);
        List<ParkourScore> scores = savedData.getData().getScores();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lPARKOUR BESTZEITEN\n§8----------------------");
        if (scores.isEmpty()) {
            sb.append("\n§7(Noch keine Zeiten)");
        } else {
            int max = Math.min(scores.size(), 10);
            for (int i = 0; i < max; i++) {
                ParkourScore score = scores.get(i);
                String prefix = switch (i) {
                    case 0 -> "§e#1";
                    case 1 -> "§f#2";
                    case 2 -> "§6#3";
                    default -> "§7#" + (i + 1);
                };
                sb.append("\n").append(prefix).append(" §f").append(score.playerName())
                        .append(" §7- §a").append(ParkourScore.formatTime(score.timeMillis()));
            }
        }

        Component text = Component.literal(sb.toString());
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.6;
        double z = worldPosition.getZ() + 0.5;

        if (textDisplayUuid != null) {
            Entity entity = level.getEntity(textDisplayUuid);
            if (entity instanceof Display.TextDisplay display && display.isAlive()) {
                ((TextDisplayAccess) display).browars$setText(text);
                ((DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
                display.setPos(x, y, z);
                return;
            }
        }

        Display.TextDisplay display = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display != null) {
            display.setPos(x, y, z);
            ((TextDisplayAccess) display).browars$setText(text);
            ((TextDisplayAccess) display).browars$setBackgroundColor(0x70000000);
            ((DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
            ((DisplayAccess) display).browars$setViewRange(32.0f);
            level.addFreshEntity(display);
            this.textDisplayUuid = display.getUUID();
            setChanged();
        }
    }

    public void onRemoved(ServerLevel level) {
        if (this.textDisplayUuid != null) {
            HologramHelper.removeHologram(level, this.textDisplayUuid);
            this.textDisplayUuid = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (textDisplayUuid != null) {
            output.putString("textDisplayUuid", textDisplayUuid.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String uuidStr = input.getStringOr("textDisplayUuid", "");
        this.textDisplayUuid = !uuidStr.isEmpty() ? UUID.fromString(uuidStr) : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (textDisplayUuid != null) {
            tag.putString("textDisplayUuid", textDisplayUuid.toString());
        }
        return tag;
    }
}
