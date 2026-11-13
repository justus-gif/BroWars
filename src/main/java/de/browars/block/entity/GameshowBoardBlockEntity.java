package de.browars.block.entity;

import de.browars.BroWarsMod;
import de.browars.gameshow.GameshowData;
import de.browars.gameshow.GameshowSavedData;
import de.browars.minigame.TeamColor;
import de.browars.mixin.DisplayAccess;
import de.browars.mixin.TextDisplayAccess;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.UUID;

public class GameshowBoardBlockEntity extends BlockEntity {
    private UUID textDisplayUuid;
    private int tickCount = 0;

    public GameshowBoardBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.GAMESHOW_BOARD_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GameshowBoardBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        be.tickCount++;
        if (be.tickCount % 20 == 0) {
            be.updateHologram(serverLevel);
        }
    }

    public void updateHologram(ServerLevel level) {
        if (!level.hasChunkAt(worldPosition)) return;

        GameshowSavedData savedData = GameshowSavedData.get(level);
        GameshowData.ShowRecord show = savedData.getData().getLastShow();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lGAME SHOW");

        if (show == null || show.p1Name().isEmpty() || show.p2Name().isEmpty()) {
            sb.append("\n§70 : 0");
        } else {
            TeamColor t1 = TeamColor.fromString(show.p1ColorId(), TeamColor.RED);
            TeamColor t2 = TeamColor.fromString(show.p2ColorId(), TeamColor.BLUE);

            sb.append("\n")
              .append(t1.chatPrefix).append(show.p1Name())
              .append("  §f§l").append(show.p1Wins())
              .append(" §8: ")
              .append("§f§l").append(show.p2Wins()).append("  ")
              .append(t2.chatPrefix).append(show.p2Name());

            int totalDots = Math.max(show.targetGames(), Math.max(show.p1Wins() + show.p2Wins(), 7));
            int p1MaxDots = Math.min(7, Math.max(4, (totalDots + 1) / 2));
            StringBuilder p1Dots = new StringBuilder();
            for (int i = 0; i < p1MaxDots; i++) {
                p1Dots.append(i < show.p1Wins() ? "§6●" : "§8○");
            }
            StringBuilder p2Dots = new StringBuilder();
            for (int i = p1MaxDots - 1; i >= 0; i--) {
                p2Dots.append(i < show.p2Wins() ? "§6●" : "§8○");
            }
            sb.append("\n").append(p1Dots).append("  §8•  ").append(p2Dots);

            if (show.finished() && !show.overallWinner().isEmpty()) {
                sb.append("\n§6§l👑 ").append(show.overallWinner());
            }

            List<GameshowData.PlayedGameEntry> played = show.playedGames();
            if (!played.isEmpty()) {
                sb.append("\n");
                for (GameshowData.PlayedGameEntry entry : played) {
                    TeamColor winColor = TeamColor.fromString(entry.winnerColorId(), TeamColor.YELLOW);
                    String scorePart = (entry.scoreSummary() != null && !entry.scoreSummary().isEmpty())
                            ? (" §8(" + entry.scoreSummary() + ")")
                            : "";
                    sb.append("\n§7#").append(entry.gameNumber())
                      .append(" §f").append(entry.gameTitle())
                      .append(" §8• ")
                      .append(winColor.chatPrefix).append(entry.winnerName())
                      .append(scorePart);
                }
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
                ((TextDisplayAccess) display).browars$setLineWidth(280);
                ((TextDisplayAccess) display).browars$setBackgroundColor(0xD8141416);
                ((DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
                display.setPos(x, y, z);
                return;
            }
        }

        Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display != null) {
            display.setPos(x, y, z);
            ((TextDisplayAccess) display).browars$setText(text);
            ((TextDisplayAccess) display).browars$setLineWidth(280);
            ((TextDisplayAccess) display).browars$setBackgroundColor(0xD8141416);
            ((DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
            ((DisplayAccess) display).browars$setViewRange(40.0f);
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
