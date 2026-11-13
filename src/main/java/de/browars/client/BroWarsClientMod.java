package de.browars.client;

import de.browars.client.gui.AreaManagerScreen;
import de.browars.client.gui.TeleportAreaScreen;
import de.browars.client.gui.TeleportBlockScreen;
import de.browars.client.render.AreaSelectionRenderer;
import de.browars.network.OpenTeleportAreaPayload;
import de.browars.network.OpenTeleportBlockPayload;
import de.browars.network.SyncAreasPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroWarsClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("browars-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialisiere BroWars Client...");

        // 1. Register Clientbound Network Receivers
        ClientPlayNetworking.registerGlobalReceiver(OpenTeleportBlockPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new TeleportBlockScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenTeleportAreaPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new TeleportAreaScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenAreaManagerPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new AreaManagerScreen(null, payload.pos1(), payload.pos2()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncAreasPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                AreaManagerScreen.updateAreas(payload.areas());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncClipboardPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientClipboard.set(payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(), payload.dimension());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenBoostPadPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new de.browars.client.gui.BoostPadScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenUpwardBoosterPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new de.browars.client.gui.UpwardBoosterScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenTeleportTargetPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new de.browars.client.gui.TeleportTargetScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenTextBlockPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new de.browars.client.gui.TextBlockScreen(payload));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.OpenParkourLeaderboardPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().gui.setScreen(new de.browars.client.gui.ParkourLeaderboardScreen(payload));
            });
        });

        // Minigame HUD Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncMinigameHudPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.minigame.MinigameHudClientHolder.setState(payload.state());
            });
        });

        // Arena Selection Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncArenaSelectionPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientArenaSelection.set(payload.pos1().orElse(null), payload.pos2().orElse(null));
            });
        });

        // Color Floor Persistent Data Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncColorFloorDataPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.ClientColorFloorData.update(payload);
            });
        });

        // Cross War Player Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncCrossWarPlayerPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.minigame.CrossWarClientHolder.update(payload.ammo(), payload.reloadTicks(), payload.isReloading());
            });
        });

        // Paint War Lucky Box Roulette Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncPaintWarRoulettePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.minigame.PaintWarRouletteClientHolder.startOrUpdate(payload.rewardOrdinal(), payload.totalSpinTicks());
            });
        });

        // Game Show HUD & Cutscene Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncGameshowHudPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.gameshow.GameshowClientHolder.setHudState(payload);
            });
        });

        // Game Show Team Color Selection Screen Sync Receiver
        ClientPlayNetworking.registerGlobalReceiver(de.browars.network.SyncGameshowColorSelectPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                de.browars.client.gameshow.GameshowClientHolder.setColorSelectState(payload);
                if (payload.openOrKeepOpen()) {
                    de.browars.client.gui.GameshowColorSelectScreen.openOrUpdate();
                } else {
                    de.browars.client.gui.GameshowColorSelectScreen.closeIfOpen();
                }
            });
        });

        // Register Reload Keybind
        RELOAD_KEY = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping(
                "key.browars.reload",
                com.mojang.blaze3d.platform.InputConstants.KEY_R,
                net.minecraft.client.KeyMapping.Category.GAMEPLAY
        ));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            de.browars.client.minigame.MinigameHudClientHolder.setState(de.browars.minigame.ui.MinigameHudState.inactive());
            ClientArenaSelection.set(null, null);
            de.browars.client.ClientColorFloorData.reset();
            de.browars.client.minigame.CrossWarClientHolder.reset();
            de.browars.client.minigame.PaintWarRouletteClientHolder.reset();
            de.browars.client.gameshow.GameshowClientHolder.reset();
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String msg = de.browars.updater.ModUpdater.getUpdateNotification();
            if (msg != null && client.player != null) {
                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
                de.browars.updater.ModUpdater.clearNotification();
            }
        });

        // Register Unified Minigame HUD Element
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.TITLE_AND_SUBTITLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("browars", "minigame_hud"),
                new de.browars.client.render.MinigameHudElement()
        );

        // Register Game Show HUD Element (Intro, Roulette, Evaluation, Finale, Lobby Scorebar)
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.TITLE_AND_SUBTITLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("browars", "gameshow_hud"),
                new de.browars.client.render.GameshowHudElement()
        );

        // Register Cross War Crosshair HUD Element
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("browars", "crosswar_hud"),
                new de.browars.client.render.CrossWarHudElement()
        );

        // Register Paint War Top-Right Lucky Box Roulette HUD Element
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.TITLE_AND_SUBTITLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("browars", "paintwar_roulette_hud"),
                new de.browars.client.render.PaintWarRouletteHudElement()
        );

        // Register Block Color Provider for Boost Pad
        net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register((state, view, pos, tintValues) -> {
            int color = 0xFFFFFF;
            if (view != null && pos != null) {
                net.minecraft.world.level.block.entity.BlockEntity blockEntity = view.getBlockEntity(pos);
                if (blockEntity instanceof de.browars.block.entity.BoostPadBlockEntity boostPadEntity) {
                    color = boostPadEntity.getColorHex();
                }
            }
            // Add the color for tint index 0
            tintValues.add(color);
        }, de.browars.BroWarsMod.BOOST_PAD);

        // Register Sky Block Renderer & Client Batch Manager hooks
        de.browars.block.entity.SkyBlockEntity.clientOnAdd = de.browars.client.render.SkyBlockClientManager::register;
        de.browars.block.entity.SkyBlockEntity.clientOnRemove = de.browars.client.render.SkyBlockClientManager::unregister;
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                de.browars.BroWarsMod.SKY_BLOCK_ENTITY,
                de.browars.client.render.SkyBlockRenderer::new
        );

        // 2. Client Selection Box Outline Tick & Cross War Reload Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AreaSelectionRenderer.onClientTick(client);
            de.browars.client.minigame.PaintWarRouletteClientHolder.clientTick();
            de.browars.client.gameshow.GameshowClientHolder.clientTick();

            if (client.player != null && client.player.getMainHandItem().is(de.browars.BroWarsMod.CROSSWAR_CROSSBOW)) {
                boolean rDown = RELOAD_KEY != null && RELOAD_KEY.isDown();
                boolean rightDown = client.options.keyUse.isDown() && de.browars.client.minigame.CrossWarClientHolder.getAmmo() <= 0;
                boolean wantsReload = rDown || rightDown;

                if (wantsReload != lastSentReload) {
                    lastSentReload = wantsReload;
                    ClientPlayNetworking.send(new de.browars.network.CrossWarReloadPayload(wantsReload));
                }

                if (de.browars.client.minigame.CrossWarClientHolder.isReloading()) {
                    de.browars.client.minigame.CrossWarClientHolder.clientTick();
                    if (!client.player.isUsingItem()) {
                        client.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                } else if (!client.options.keyUse.isDown() && client.player.isUsingItem()) {
                    client.player.stopUsingItem();
                }
            } else if (lastSentReload) {
                lastSentReload = false;
                ClientPlayNetworking.send(new de.browars.network.CrossWarReloadPayload(false));
            }
        });

        LOGGER.info("BroWars Client erfolgreich initialisiert!");
    }

    public static net.minecraft.client.KeyMapping RELOAD_KEY;
    private static boolean lastSentReload = false;
}
