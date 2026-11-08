package de.browars.network;

import de.browars.area.TeleportArea;
import de.browars.area.TeleportAreaSavedData;
import de.browars.block.entity.TeleportBlockEntity;
import de.browars.util.HologramHelper;
import de.browars.util.InputValidation;
import de.browars.util.PermissionsHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Set;

public final class BroWarsNetworking {
    private BroWarsNetworking() {}
    private static boolean canEditBlock(ServerPlayer player, BlockPos pos) {
        return PermissionsHelper.isOpOrCreative(player) && player.level().hasChunkAt(pos)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
    private static void removeAreaHologram(ServerLevel currentLevel, TeleportArea area) {
        if (area == null || area.getTextDisplayUuid() == null) return;
        ServerLevel owner = currentLevel.getServer().getLevel(area.getDimension());
        if (owner != null) HologramHelper.removeHologram(owner, area.getTextDisplayUuid());
    }
    public static void register() {
        // S2C
        PayloadTypeRegistry.clientboundPlay().register(OpenTeleportBlockPayload.TYPE, OpenTeleportBlockPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenTeleportAreaPayload.TYPE, OpenTeleportAreaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenAreaManagerPayload.TYPE, OpenAreaManagerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.browars.network.OpenTeleportTargetPayload.TYPE, de.browars.network.OpenTeleportTargetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.browars.network.OpenTextBlockPayload.TYPE, de.browars.network.OpenTextBlockPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncAreasPayload.TYPE, SyncAreasPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncClipboardPayload.TYPE, SyncClipboardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenBoostPadPayload.TYPE, OpenBoostPadPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenUpwardBoosterPayload.TYPE, OpenUpwardBoosterPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.browars.network.SyncMinigameHudPayload.TYPE, de.browars.network.SyncMinigameHudPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.browars.network.SyncArenaSelectionPayload.TYPE, de.browars.network.SyncArenaSelectionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(de.browars.network.SyncColorFloorDataPayload.TYPE, de.browars.network.SyncColorFloorDataPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncCrossWarPlayerPayload.TYPE, SyncCrossWarPlayerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPaintWarRoulettePayload.TYPE, SyncPaintWarRoulettePayload.CODEC);

        // C2S
        PayloadTypeRegistry.serverboundPlay().register(SaveTeleportBlockPayload.TYPE, SaveTeleportBlockPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveTeleportAreaPayload.TYPE, SaveTeleportAreaPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(de.browars.network.SaveTeleportTargetPayload.TYPE, de.browars.network.SaveTeleportTargetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(de.browars.network.SaveTextBlockPayload.TYPE, de.browars.network.SaveTextBlockPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeleteTeleportAreaPayload.TYPE, DeleteTeleportAreaPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TeleportToAreaPayload.TYPE, TeleportToAreaPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveBoostPadPayload.TYPE, SaveBoostPadPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveUpwardBoosterPayload.TYPE, SaveUpwardBoosterPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CrossWarShootPayload.TYPE, CrossWarShootPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CrossWarReloadPayload.TYPE, CrossWarReloadPayload.CODEC);

        // C2S Receivers
        ServerPlayNetworking.registerGlobalReceiver(CrossWarShootPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.crosswar.CrossWarGame crossWar) {
                    crossWar.onShootRequest(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CrossWarReloadPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.crosswar.CrossWarGame crossWar) {
                    crossWar.onReloadRequest(player, payload.holding());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveUpwardBoosterPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!canEditBlock(player, payload.pos())) return;
            ServerLevel level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (be instanceof de.browars.block.entity.UpwardBoosterBlockEntity boosterBe) {
                boosterBe.setHeight(payload.height());
                level.sendBlockUpdated(payload.pos(), boosterBe.getBlockState(), boosterBe.getBlockState(), 3);
                player.sendSystemMessage(Component.literal("§a[BroWars] Aufwärts-Booster gespeichert (" + payload.height() + "m)!"), true);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(de.browars.network.SaveTextBlockPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!canEditBlock(player, payload.pos())) return;
            ServerLevel level = player.level();
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (be instanceof de.browars.block.entity.TextBlockEntity textBe) {
                textBe.setText(payload.text());
                level.sendBlockUpdated(payload.pos(), textBe.getBlockState(), textBe.getBlockState(), 3);
                player.sendSystemMessage(Component.literal("§a[BroWars] Text-Block gespeichert!"), true);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(de.browars.network.SaveTeleportTargetPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (canEditBlock(player, payload.pos())) {
                ServerLevel level = player.level();
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be instanceof de.browars.block.entity.TeleportTargetBlockEntity targetBe) {
                    targetBe.setName(payload.name());
                    targetBe.setColorHex(payload.colorHex());
                    level.sendBlockUpdated(payload.pos(), targetBe.getBlockState(), targetBe.getBlockState(), 3);
                    player.sendSystemMessage(Component.literal("§a[BroWars] Teleport Ziel-Indikator gespeichert!"), true);
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(SaveBoostPadPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (canEditBlock(player, payload.pos())) {
                ServerLevel level = player.level();
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be instanceof de.browars.block.entity.BoostPadBlockEntity pad) {
                    pad.setStrength(payload.strength());
                    pad.setColorHex(payload.colorHex());
                    pad.setLookDirectionMode(payload.lookDirectionMode());
                    level.sendBlockUpdated(payload.pos(), pad.getBlockState(), pad.getBlockState(), 3);
                    player.sendSystemMessage(Component.literal("§a[BroWars] Boost-Pad Einstellungen gespeichert!"), true);
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(SaveTeleportBlockPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!InputValidation.teleportTarget(payload.targetX(), payload.targetY(), payload.targetZ(), payload.targetYaw(), payload.targetPitch(), payload.targetDimension())) {
                player.sendSystemMessage(Component.literal("§c[BroWars] Ungültige Zielkoordinaten oder Dimension."), true);
                return;
            }
            if (canEditBlock(player, payload.pos())) {
                ServerLevel level = player.level();
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be instanceof TeleportBlockEntity teleporter) {
                    teleporter.setTargetX(payload.targetX());
                    teleporter.setTargetY(payload.targetY());
                    teleporter.setTargetZ(payload.targetZ());
                    teleporter.setTargetYaw(payload.targetYaw());
                    teleporter.setTargetPitch(payload.targetPitch());
                    teleporter.setTargetDimension(payload.targetDimension());
                    teleporter.setName(payload.name());
                    teleporter.setColorHex(payload.colorHex());
                    level.sendBlockUpdated(payload.pos(), teleporter.getBlockState(), teleporter.getBlockState(), 3);
                    player.sendSystemMessage(Component.literal("§a[BroWars] Teleport-Block gespeichert!"), true);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveTeleportAreaPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!InputValidation.teleportTarget(payload.targetX(), payload.targetY(), payload.targetZ(), payload.targetYaw(), payload.targetPitch(), payload.targetDimension())) {
                player.sendSystemMessage(Component.literal("§c[BroWars] Ungültige Zielkoordinaten oder Dimension."), true);
                return;
            }
            if (de.browars.util.PermissionsHelper.isOp(player)) {
                ServerLevel level = player.level();
                TeleportAreaSavedData savedData = TeleportAreaSavedData.get(level);

                ResourceKey<Level> targetDim = ResourceKey.create(
                        Registries.DIMENSION, Identifier.parse(payload.targetDimension())
                );

                TeleportArea previous = savedData.getArea(payload.areaId());
                TeleportArea area = new TeleportArea(
                        payload.areaId(),
                        payload.name(),
                        payload.pos1(),
                        payload.pos2(),
                        previous == null ? level.dimension() : previous.getDimension(),
                        payload.targetX(),
                        payload.targetY(),
                        payload.targetZ(),
                        payload.targetYaw(),
                        payload.targetPitch(),
                        targetDim,
                        payload.colorHex(),
                        payload.visibleInSurvival(),
                        payload.showNameHologram(),
                        null
                );

                removeAreaHologram(level, previous);
                savedData.putArea(area);
                ServerPlayNetworking.send(player, new SyncAreasPayload(savedData.getAreasList()));
                player.sendSystemMessage(Component.literal("§a[BroWars] Teleport-Fläche '" + area.getName() + "' gespeichert!"), true);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteTeleportAreaPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (de.browars.util.PermissionsHelper.isOp(player)) {
                ServerLevel level = player.level();
                TeleportAreaSavedData savedData = TeleportAreaSavedData.get(level);
                TeleportArea area = savedData.getArea(payload.areaId());
                removeAreaHologram(level, area);
                savedData.removeArea(payload.areaId());
                ServerPlayNetworking.send(player, new SyncAreasPayload(savedData.getAreasList()));
                player.sendSystemMessage(Component.literal("§c[BroWars] Teleport-Fläche gelöscht!"), true);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(TeleportToAreaPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (de.browars.util.PermissionsHelper.isOp(player)
                    && !de.browars.minigame.MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())) {
                TeleportAreaSavedData savedData = TeleportAreaSavedData.get(player.level());
                TeleportArea area = savedData.getArea(payload.areaId());
                if (area != null) {
                    ServerLevel destLevel = player.level().getServer().getLevel(area.getDimension());
                    if (destLevel != null) {
                        double cx = (area.getMinPos().getX() + area.getMaxPos().getX() + 1.0) / 2.0;
                        double cy = area.getMinPos().getY();
                        double cz = (area.getMinPos().getZ() + area.getMaxPos().getZ() + 1.0) / 2.0;
                        player.teleportTo(destLevel, cx, cy, cz, Set.of(), player.getYRot(), player.getXRot(), true);
                        player.sendSystemMessage(Component.literal("§b[BroWars] Zur Fläche teleportiert!"), true);
                    }
                }
            }
        });
    }
}
