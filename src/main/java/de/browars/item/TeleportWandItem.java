package de.browars.item;

import de.browars.area.TeleportAreaSavedData;
import de.browars.network.OpenTeleportAreaPayload;
import de.browars.network.SyncAreasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportWandItem extends Item {
    public static final Map<UUID, BlockPos> POS1_MAP = new ConcurrentHashMap<>();
    public static final Map<UUID, BlockPos> POS2_MAP = new ConcurrentHashMap<>();

    public TeleportWandItem(Properties properties) {
        super(properties);
    }

    public static void setPos1(ServerPlayer player, BlockPos pos) {
        POS1_MAP.put(player.getUUID(), pos);
        player.sendSystemMessage(
                Component.literal("§a[BroWars] Punkt 1 gesetzt bei: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                true
        );
    }

    public static void setPos2(ServerPlayer player, BlockPos pos) {
        POS2_MAP.put(player.getUUID(), pos);
        player.sendSystemMessage(
                Component.literal("§b[BroWars] Punkt 2 gesetzt bei: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                true
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        if (player instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOp(serverPlayer)) {
                if (serverPlayer.isShiftKeyDown()) {
                    setPos2(serverPlayer, clickedPos);
                    return InteractionResult.SUCCESS;
                }
                openAreaConfig(serverPlayer);
                return InteractionResult.SUCCESS;
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOp(serverPlayer)) {
                openAreaConfig(serverPlayer);
                return InteractionResult.SUCCESS;
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public static void openAreaConfig(ServerPlayer player) {
        BlockPos p1 = POS1_MAP.getOrDefault(player.getUUID(), player.blockPosition());
        BlockPos p2 = POS2_MAP.getOrDefault(player.getUUID(), player.blockPosition());

        TeleportAreaSavedData savedData = TeleportAreaSavedData.get(player.level());
        ServerPlayNetworking.send(player, new SyncAreasPayload(savedData.getAreasList()));
        ServerPlayNetworking.send(player, new de.browars.network.OpenAreaManagerPayload(p1, p2));
    }
}
