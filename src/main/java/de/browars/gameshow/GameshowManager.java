package de.browars.gameshow;

import de.browars.block.entity.GameshowBoardBlockEntity;
import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.mixin.MannequinAccess;
import de.browars.network.SyncGameshowColorSelectPayload;
import de.browars.network.SyncGameshowHudPayload;
import de.browars.system.WorldSpawnRespawnHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GameshowManager {
    private static final GameshowManager INSTANCE = new GameshowManager();

    public static GameshowManager getInstance() {
        return INSTANCE;
    }

    public enum Phase {
        INACTIVE,
        COLOR_SELECT,
        INTRO,
        ROULETTE,
        IN_GAME,
        EVALUATION,
        LOBBY,
        FINALE_EVALUATION,
        PODIUM
    }

    public static final int DEFAULT_TOTAL_GAMES = 7;
    private static final int AREA_COUNTDOWN_TICKS = 100; // 5 seconds
    private static final int COLOR_SELECT_TICKS = 500;   // 25 seconds max
    private static final int INTRO_TICKS = 120;          // 6 seconds
    private static final int ROULETTE_TICKS = 130;       // 6.5 seconds (90 spin + 40 reveal)
    private static final int EVALUATION_TICKS = 120;     // 6 seconds
    private static final int FINALE_TICKS = 160;         // 8 seconds
    private static final int PODIUM_TICKS = 1200;        // 60 seconds (1 minute)

    // Particle colors requested by user: #FFBB00 (0 players), #F7770F (1 player), #D61811 (2 players)
    private static final int COLOR_IDLE = 0xFFBB00;
    private static final int COLOR_ONE_PLAYER = 0xF7770F;
    private static final int COLOR_TWO_PLAYERS = 0xD61811;
    private static final int COLOR_READY_GO = 0x22C55E;

    private final Random random = new Random();

    private Phase phase = Phase.INACTIVE;
    private int phaseTicks = 0;
    private int phaseTotalTicks = 0;
    private int serverTickCounter = 0;

    // Ready area countdown state
    private int startAreaCountdown = AREA_COUNTDOWN_TICKS;
    private int lobbyAreaCountdown = AREA_COUNTDOWN_TICKS;

    // Show participants & state
    private UUID p1Uuid;
    private UUID p2Uuid;
    private String p1Name = "";
    private String p2Name = "";
    private TeamColor p1Color = TeamColor.RED;
    private TeamColor p2Color = TeamColor.BLUE;
    private boolean p1ColorReady = false;
    private boolean p2ColorReady = false;
    private int p1Wins = 0;
    private int p2Wins = 0;
    private int totalGames = DEFAULT_TOTAL_GAMES;
    private int currentGameNumber = 0;

    // Roulette & current game tracking
    private final List<String> roulettePoolIds = new ArrayList<>();
    private int rouletteTargetIndex = 0;
    private int lastRouletteSoundSlot = -1;
    private GameshowGameType selectedGameType = null;
    private boolean startingMinigameInternal = false;

    // History & last game result
    private final List<GameshowData.PlayedGameEntry> playedGames = new ArrayList<>();
    private String lastWinnerName = "";
    private String lastWinnerColorId = "yellow";
    private String lastScoreSummary = "";

    // Podium 1 (Winner) & Podium 2 (Runner-up) Mannequin entity tracking
    public static final String PODIUM_MANNEQUIN_TAG = "browars_podium_mannequin";
    private UUID podium1MannequinUuid = null;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> podium1MannequinDim = null;
    private UUID podium2MannequinUuid = null;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> podium2MannequinDim = null;

    private GameshowManager() {}

    public boolean isShowActive() {
        return phase != Phase.INACTIVE;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isPlayerInShow(UUID uuid) {
        return isShowActive() && uuid != null && (uuid.equals(p1Uuid) || uuid.equals(p2Uuid));
    }

    public boolean isPlayerInBlackRoom(UUID uuid) {
        if (!isPlayerInShow(uuid)) return false;
        return switch (phase) {
            case COLOR_SELECT, INTRO, ROULETTE, EVALUATION, FINALE_EVALUATION -> true;
            default -> false;
        };
    }

    public void onServerTick(MinecraftServer server) {
        serverTickCounter++;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        GameshowSavedData savedData = GameshowSavedData.get(overworld);
        GameshowData data = savedData.getData();

        // 1. When no show is active, tick the Main Lobby Start Area
        if (phase == Phase.INACTIVE) {
            if (!MinigameManager.getInstance().isGameRunning()) {
                tickStartArea(server, data);
            }
            return;
        }

        // 2. Verify both participants are still online
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
        if (p1 == null || p2 == null) {
            stopShow(server, "Spieler hat verlassen");
            return;
        }
        p1Name = p1.getPlainTextName();
        p2Name = p2.getPlainTextName();

        // 3. Tick active phase
        switch (phase) {
            case COLOR_SELECT -> tickColorSelect(server, data, p1, p2);
            case INTRO -> tickIntro(server, data, p1, p2);
            case ROULETTE -> tickRoulette(server, data, p1, p2);
            case IN_GAME -> tickInGame(server, p1, p2);
            case EVALUATION -> tickEvaluation(server, data, p1, p2);
            case LOBBY -> tickLobby(server, data, p1, p2);
            case FINALE_EVALUATION -> tickFinaleEvaluation(server, data, p1, p2);
            case PODIUM -> tickPodium(server, data, p1, p2);
            default -> {}
        }
    }

    private void tickStartArea(MinecraftServer server, GameshowData data) {
        GameshowData.AreaBox startArea = data.getStartArea();
        if (!startArea.set()) return;

        ServerLevel level = server.getLevel(startArea.dimension());
        if (level == null) return;

        AABB box = startArea.toAABB();
        if (box == null) return;

        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && box.intersects(player.getBoundingBox())) {
                inside.add(player);
            }
        }

        int count = inside.size();
        int particleColor = count == 0 ? COLOR_IDLE : (count == 1 ? COLOR_ONE_PLAYER : (startAreaCountdown <= 20 ? COLOR_READY_GO : COLOR_TWO_PLAYERS));
        if (serverTickCounter % 2 == 0) {
            spawnBorderParticles(level, startArea, particleColor);
        }

        if (count == 1) {
            if (startAreaCountdown < AREA_COUNTDOWN_TICKS) {
                startAreaCountdown = AREA_COUNTDOWN_TICKS;
            }
            if (serverTickCounter % 20 == 0) {
                inside.get(0).sendSystemMessage(Component.literal("§6§l1 / 2"), true);
            }
        } else if (count >= 2) {
            ServerPlayer first = inside.get(0);
            ServerPlayer second = inside.get(1);

            if (startAreaCountdown % 20 == 0) {
                int sec = startAreaCountdown / 20;
                float pitch = switch (sec) {
                    case 5 -> 1.0f;
                    case 4 -> 1.15f;
                    case 3 -> 1.3f;
                    case 2 -> 1.5f;
                    default -> 1.8f;
                };
                playSoundTo(first, second, SoundEvents.NOTE_BLOCK_PLING.value(), pitch);
                Component msg = Component.literal("§6§l" + sec);
                first.sendSystemMessage(msg, true);
                second.sendSystemMessage(msg, true);
            }

            startAreaCountdown--;
            if (startAreaCountdown <= 0) {
                startAreaCountdown = AREA_COUNTDOWN_TICKS;
                startShow(server, first, second);
            }
        } else {
            startAreaCountdown = AREA_COUNTDOWN_TICKS;
        }
    }

    public boolean startShow(MinecraftServer server, ServerPlayer first, ServerPlayer second) {
        if (first == null || second == null || first.getUUID().equals(second.getUUID())) {
            return false;
        }

        List<GameshowGameType> readyGames = getReadyGames(server);
        if (readyGames.isEmpty()) {
            Component err = Component.literal("§c[Game Show] Keine Minispiele eingerichtet (/browars gameshow status).");
            first.sendSystemMessage(err, false);
            second.sendSystemMessage(err, false);
            playSoundTo(first, second, SoundEvents.VILLAGER_NO, 1.0f);
            return false;
        }

        if (MinigameManager.getInstance().isGameRunning()) {
            MinigameManager.getInstance().stopCurrentMinigame("Game Show gestartet");
        }
        removePodiumMannequins(server);

        this.p1Uuid = first.getUUID();
        this.p2Uuid = second.getUUID();
        this.p1Name = first.getPlainTextName();
        this.p2Name = second.getPlainTextName();
        this.p1Color = TeamColor.RED;
        this.p2Color = TeamColor.BLUE;
        this.p1ColorReady = false;
        this.p2ColorReady = false;
        this.p1Wins = 0;
        this.p2Wins = 0;
        this.totalGames = DEFAULT_TOTAL_GAMES;
        this.currentGameNumber = 0;
        this.playedGames.clear();
        this.roulettePoolIds.clear();
        this.selectedGameType = null;
        this.lastWinnerName = "";
        this.lastWinnerColorId = "yellow";
        this.lastScoreSummary = "";

        saveShowProgress(server, false, "");
        updateAllBoards(server);

        // Transition into Black Room for Color Selection
        this.phase = Phase.COLOR_SELECT;
        this.phaseTotalTicks = COLOR_SELECT_TICKS;
        this.phaseTicks = COLOR_SELECT_TICKS;

        GameshowData data = GameshowSavedData.get(server.overworld()).getData();
        teleportToBlackRoom(server, data, first, second);
        playSoundTo(first, second, SoundEvents.ENDERMAN_TELEPORT, 1.1f);
        syncHudToAll(server);
        syncColorSelectToPlayers(first, second, true);
        return true;
    }

    public void onPlayerSelectColor(ServerPlayer player, String colorId, boolean toggleReady) {
        if (phase != Phase.COLOR_SELECT || !isPlayerInShow(player.getUUID())) return;

        boolean isP1 = player.getUUID().equals(p1Uuid);
        TeamColor chosen = TeamColor.fromString(colorId, null);

        if (toggleReady) {
            if (isP1) p1ColorReady = !p1ColorReady;
            else p2ColorReady = !p2ColorReady;
            if (!(p1ColorReady && p2ColorReady)) {
                playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), (isP1 ? p1ColorReady : p2ColorReady) ? 1.5f : 0.9f);
            }
        } else if (chosen != null) {
            TeamColor myColor = isP1 ? p1Color : p2Color;
            TeamColor opponentColor = isP1 ? p2Color : p1Color;
            if (chosen != opponentColor && chosen != myColor) {
                if (isP1 && !p1ColorReady) {
                    p1Color = chosen;
                    playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
                } else if (!isP1 && !p2ColorReady) {
                    p2Color = chosen;
                    playSoundToPlayer(player, SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
                }
            }
        }

        MinecraftServer server = player.level().getServer();
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);

        saveShowProgress(server, false, "");
        updateAllBoards(server);

        if (p1ColorReady && p2ColorReady) {
            if (p1 != null && p2 != null) {
                syncColorSelectToPlayers(p1, p2, false);
                beginIntro(server, p1, p2);
            }
        } else if (p1 != null && p2 != null) {
            syncColorSelectToPlayers(p1, p2, true);
        }
    }

    private void tickColorSelect(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainBlackRoomPlayers(server, data, p1, p2);
        phaseTicks--;

        if (phaseTicks % 20 == 0) {
            syncColorSelectToPlayers(p1, p2, true);
        }

        if (phaseTicks <= 0) {
            syncColorSelectToPlayers(p1, p2, false);
            beginIntro(server, p1, p2);
        }
    }

    private void beginIntro(MinecraftServer server, ServerPlayer p1, ServerPlayer p2) {
        if (p1Color == p2Color) {
            p2Color = (p1Color == TeamColor.RED) ? TeamColor.BLUE : TeamColor.RED;
        }
        this.phase = Phase.INTRO;
        this.phaseTotalTicks = INTRO_TICKS;
        this.phaseTicks = INTRO_TICKS;
        playSoundTo(p1, p2, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
        syncHudToAll(server);
    }

    private void tickIntro(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainBlackRoomPlayers(server, data, p1, p2);
        int elapsed = phaseTotalTicks - phaseTicks;
        if (elapsed == 35) {
            playSoundTo(p1, p2, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f);
        }

        phaseTicks--;
        if (phaseTicks <= 0) {
            beginNextRoulette(server, data, p1, p2);
        }
    }

    private void beginNextRoulette(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        currentGameNumber++;
        teleportToBlackRoom(server, data, p1, p2);

        List<GameshowGameType> readyGames = getReadyGames(server);
        if (readyGames.isEmpty()) {
            stopShow(server, "Keine spielbaren Minispiele gefunden.");
            return;
        }

        // Filter out already played games in this show
        List<GameshowGameType> unplayed = new ArrayList<>();
        for (GameshowGameType type : readyGames) {
            boolean alreadyPlayed = false;
            for (GameshowData.PlayedGameEntry entry : playedGames) {
                if (entry.gameId().equalsIgnoreCase(type.id)) {
                    alreadyPlayed = true;
                    break;
                }
            }
            if (!alreadyPlayed) {
                unplayed.add(type);
            }
        }

        // If fewer arenas are configured than totalGames, refill from readyGames (avoiding immediate repeat)
        if (unplayed.isEmpty()) {
            String lastPlayedId = playedGames.isEmpty() ? "" : playedGames.get(playedGames.size() - 1).gameId();
            for (GameshowGameType type : readyGames) {
                if (readyGames.size() == 1 || !type.id.equalsIgnoreCase(lastPlayedId)) {
                    unplayed.add(type);
                }
            }
        }

        this.selectedGameType = unplayed.get(random.nextInt(unplayed.size()));

        // Build visual roulette pool
        this.roulettePoolIds.clear();
        List<GameshowGameType> displayCandidates = new ArrayList<>(unplayed);
        if (displayCandidates.size() < 5) {
            for (GameshowGameType type : readyGames) {
                if (!displayCandidates.contains(type)) displayCandidates.add(type);
            }
        }
        if (displayCandidates.size() < 5) {
            for (GameshowGameType type : GameshowGameType.values()) {
                if (!displayCandidates.contains(type)) displayCandidates.add(type);
            }
        }
        Collections.shuffle(displayCandidates, random);
        for (GameshowGameType type : displayCandidates) {
            roulettePoolIds.add(type.id);
        }
        this.rouletteTargetIndex = roulettePoolIds.indexOf(selectedGameType.id);
        if (this.rouletteTargetIndex < 0) {
            roulettePoolIds.add(selectedGameType.id);
            this.rouletteTargetIndex = roulettePoolIds.size() - 1;
        }

        this.phase = Phase.ROULETTE;
        this.phaseTotalTicks = ROULETTE_TICKS;
        this.phaseTicks = ROULETTE_TICKS;
        this.lastRouletteSoundSlot = -999;

        playSoundTo(p1, p2, SoundEvents.BEACON_ACTIVATE, 1.3f);
        syncHudToAll(server);
    }

    private void tickRoulette(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainBlackRoomPlayers(server, data, p1, p2);

        int revealHoldTicks = 40;
        int spinDuration = Math.max(1, phaseTotalTicks - revealHoldTicks);
        int remSpinTicks = Math.max(0, phaseTicks - revealHoldTicks);

        if (remSpinTicks > 0) {
            float t = Math.clamp(1.0f - (remSpinTicks / (float) spinDuration), 0.0f, 1.0f);
            float easeOut = 1.0f - (float) Math.pow(1.0f - t, 3.8);
            int currentSlot = Math.round((rouletteTargetIndex - 22) + easeOut * 22);
            if (currentSlot != lastRouletteSoundSlot) {
                lastRouletteSoundSlot = currentSlot;
                float pitch = 0.85f + 0.65f * t;
                playSoundTo(p1, p2, SoundEvents.UI_BUTTON_CLICK.value(), pitch);
            }
        } else if (phaseTicks == revealHoldTicks) {
            playSoundTo(p1, p2, SoundEvents.PLAYER_LEVELUP, 1.35f);
            playSoundTo(p1, p2, SoundEvents.NOTE_BLOCK_PLING.value(), 1.8f);
        }

        phaseTicks--;
        if (phaseTicks <= 0) {
            launchSelectedMinigame(server, p1, p2);
        }
    }

    private void launchSelectedMinigame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2) {
        if (selectedGameType == null) {
            stopShow(server, "Kein Spiel ausgewählt.");
            return;
        }
        p1.removeEffect(MobEffects.INVISIBILITY);
        p2.removeEffect(MobEffects.INVISIBILITY);

        this.phase = Phase.IN_GAME;
        syncHudToAll(server);

        GameshowGameType gameToStart = this.selectedGameType;
        this.startingMinigameInternal = true;
        try {
            MinigameManager.getInstance().startMinigame(() -> gameToStart.createGame(server, p1, p2, p1Color, p2Color));
        } catch (Exception e) {
            this.startingMinigameInternal = false;
            stopShow(server, "Fehler beim Starten von " + gameToStart.displayName + ": " + e.getMessage());
            return;
        }
        this.startingMinigameInternal = false;
    }

    private void tickInGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2) {
        if (!MinigameManager.getInstance().isGameRunning() && !startingMinigameInternal && phase == Phase.IN_GAME) {
            GameshowData data = GameshowSavedData.get(server.overworld()).getData();
            enterLobby(server, data, p1, p2);
        }
    }

    public void onMinigameCompleted(AbstractDuelGame<?> game, UUID winnerUuid, String scoreSummary) {
        if (phase != Phase.IN_GAME || !isShowActive()) return;

        MinecraftServer server = game.getServer();
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
        if (p1 == null || p2 == null) {
            stopShow(server, "Spieler hat verlassen");
            return;
        }

        boolean p1Won = p1Uuid.equals(winnerUuid);
        if (p1Won) {
            p1Wins++;
            lastWinnerName = p1Name;
            lastWinnerColorId = p1Color.id;
        } else {
            p2Wins++;
            lastWinnerName = p2Name;
            lastWinnerColorId = p2Color.id;
        }
        lastScoreSummary = (scoreSummary != null && !scoreSummary.isBlank()) ? scoreSummary : "";

        String gId = selectedGameType != null ? selectedGameType.id : game.getId();
        String gTitle = selectedGameType != null ? selectedGameType.displayName : game.getDisplayName();

        playedGames.add(new GameshowData.PlayedGameEntry(
                currentGameNumber,
                gId,
                gTitle,
                lastWinnerName,
                lastWinnerColorId,
                lastScoreSummary
        ));

        saveShowProgress(server, false, "");
        updateAllBoards(server);

        GameshowData data = GameshowSavedData.get(server.overworld()).getData();
        this.phase = Phase.EVALUATION;
        this.phaseTotalTicks = EVALUATION_TICKS;
        this.phaseTicks = EVALUATION_TICKS;

        teleportToBlackRoom(server, data, p1, p2);
        playSoundTo(p1, p2, SoundEvents.ENDERMAN_TELEPORT, 1.1f);
        syncHudToAll(server);
    }

    private void tickEvaluation(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainBlackRoomPlayers(server, data, p1, p2);

        int elapsed = phaseTotalTicks - phaseTicks;
        if (elapsed == 40) {
            playSoundTo(p1, p2, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f);
            playSoundTo(p1, p2, SoundEvents.PLAYER_LEVELUP, 1.5f);
        }

        phaseTicks--;
        if (phaseTicks <= 0) {
            boolean allRegularPlayed = playedGames.size() >= totalGames;
            boolean isTied = p1Wins == p2Wins;

            if (!allRegularPlayed || isTied) {
                enterLobby(server, data, p1, p2);
            } else {
                beginFinaleEvaluation(server, data, p1, p2);
            }
        }
    }

    private void enterLobby(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        this.phase = Phase.LOBBY;
        this.phaseTicks = 0;
        this.phaseTotalTicks = 0;
        this.lobbyAreaCountdown = AREA_COUNTDOWN_TICKS;

        p1.removeEffect(MobEffects.INVISIBILITY);
        p2.removeEffect(MobEffects.INVISIBILITY);

        teleportToSpawnPoint(server, data.getLobbySpawn(), p1, p2);
        playSoundTo(p1, p2, SoundEvents.ENDERMAN_TELEPORT, 1.2f);

        updateAllBoards(server);
        syncHudToAll(server);
    }

    private void tickLobby(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainLobbyPlayer(p1);
        maintainLobbyPlayer(p2);

        GameshowData.AreaBox lobbyArea = data.getLobbyArea();
        if (!lobbyArea.set()) {
            if (serverTickCounter % 40 == 0) {
                Component msg = Component.literal("§c/browars gameshow lobbyarea fehlt");
                p1.sendSystemMessage(msg, true);
                p2.sendSystemMessage(msg, true);
            }
            return;
        }

        ServerLevel level = server.getLevel(lobbyArea.dimension());
        if (level == null) return;

        AABB box = lobbyArea.toAABB();
        if (box == null) return;

        boolean p1In = p1.level() == level && p1.isAlive() && box.intersects(p1.getBoundingBox());
        boolean p2In = p2.level() == level && p2.isAlive() && box.intersects(p2.getBoundingBox());
        int count = (p1In ? 1 : 0) + (p2In ? 1 : 0);

        int particleColor = count == 0 ? COLOR_IDLE : (count == 1 ? COLOR_ONE_PLAYER : (lobbyAreaCountdown <= 20 ? COLOR_READY_GO : COLOR_TWO_PLAYERS));
        if (serverTickCounter % 2 == 0) {
            spawnBorderParticles(level, lobbyArea, particleColor);
        }

        if (count == 1) {
            lobbyAreaCountdown = AREA_COUNTDOWN_TICKS;
            if (serverTickCounter % 20 == 0) {
                ServerPlayer waiting = p1In ? p1 : p2;
                waiting.sendSystemMessage(Component.literal("§6§l1 / 2"), true);
            }
        } else if (count == 2) {
            if (lobbyAreaCountdown % 20 == 0) {
                int sec = lobbyAreaCountdown / 20;
                float pitch = switch (sec) {
                    case 5 -> 1.0f;
                    case 4 -> 1.15f;
                    case 3 -> 1.3f;
                    case 2 -> 1.5f;
                    default -> 1.8f;
                };
                playSoundTo(p1, p2, SoundEvents.NOTE_BLOCK_PLING.value(), pitch);
                Component msg = Component.literal("§6§l" + sec);
                p1.sendSystemMessage(msg, true);
                p2.sendSystemMessage(msg, true);
            }

            lobbyAreaCountdown--;
            if (lobbyAreaCountdown <= 0) {
                lobbyAreaCountdown = AREA_COUNTDOWN_TICKS;
                beginNextRoulette(server, data, p1, p2);
            }
        } else {
            lobbyAreaCountdown = AREA_COUNTDOWN_TICKS;
        }
    }

    private void beginFinaleEvaluation(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        this.phase = Phase.FINALE_EVALUATION;
        this.phaseTotalTicks = FINALE_TICKS;
        this.phaseTicks = FINALE_TICKS;

        String champName = p1Wins >= p2Wins ? p1Name : p2Name;
        saveShowProgress(server, true, champName);
        updateAllBoards(server);

        teleportToBlackRoom(server, data, p1, p2);
        syncHudToAll(server);
    }

    private void tickFinaleEvaluation(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainBlackRoomPlayers(server, data, p1, p2);

        int elapsed = phaseTotalTicks - phaseTicks;
        if (elapsed < 40 && elapsed % 4 == 0) {
            float pitch = 0.7f + (elapsed / 40.0f) * 0.8f;
            playSoundTo(p1, p2, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), pitch);
        } else if (elapsed == 40) {
            playSoundTo(p1, p2, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            playSoundTo(p1, p2, SoundEvents.PLAYER_LEVELUP, 1.2f);
        }

        phaseTicks--;
        if (phaseTicks <= 0) {
            beginPodium(server, data, p1, p2);
        }
    }

    private void beginPodium(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        this.phase = Phase.PODIUM;
        this.phaseTotalTicks = PODIUM_TICKS;
        this.phaseTicks = PODIUM_TICKS;

        p1.removeEffect(MobEffects.INVISIBILITY);
        p2.removeEffect(MobEffects.INVISIBILITY);

        if (data.getPodiumViewerSpawn().set()) {
            teleportToSpawnPoint(server, data.getPodiumViewerSpawn(), p1, p2);
        } else if (data.getLobbySpawn().set()) {
            teleportToSpawnPoint(server, data.getLobbySpawn(), p1, p2);
        } else {
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p1);
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p2);
        }

        boolean p1Won = p1Wins >= p2Wins;
        ServerPlayer winner = p1Won ? p1 : p2;
        TeamColor winColor = p1Won ? p1Color : p2Color;
        ServerPlayer runnerUp = p1Won ? p2 : p1;
        TeamColor runnerUpColor = p1Won ? p2Color : p1Color;

        spawnPodiumMannequins(server, data, winner, winColor, runnerUp, runnerUpColor);

        playSoundTo(p1, p2, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f);
        syncHudToAll(server);
    }

    private void tickPodium(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        maintainLobbyPlayer(p1);
        maintainLobbyPlayer(p2);

        if (phaseTicks % 25 == 0 && podium1MannequinUuid != null && podium1MannequinDim != null) {
            ServerLevel level = server.getLevel(podium1MannequinDim);
            if (level != null) {
                Entity statue = level.getEntity(podium1MannequinUuid);
                if (statue != null) {
                    level.sendParticles(ParticleTypes.FIREWORK, statue.getX(), statue.getY() + 1.8, statue.getZ(), 20, 0.7, 0.7, 0.7, 0.12);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, statue.getX(), statue.getY() + 1.2, statue.getZ(), 12, 0.5, 0.5, 0.5, 0.05);
                }
            }
        }

        phaseTicks--;
        if (phaseTicks <= 0) {
            finishShowAndReturnToWorldSpawn(server);
        }
    }

    private void spawnPodiumMannequins(
            MinecraftServer server,
            GameshowData data,
            ServerPlayer winner,
            TeamColor winColor,
            ServerPlayer runnerUp,
            TeamColor runnerUpColor
    ) {
        removePodiumMannequins(server);

        // 1st Place Mannequin
        Mannequin m1 = createPodiumMannequin(
                server,
                data.getPodium1Pos(),
                winner,
                winColor,
                "§6§l👑 1. PLATZ §7(" + Math.max(p1Wins, p2Wins) + ":" + Math.min(p1Wins, p2Wins) + ")",
                0.0
        );
        if (m1 != null) {
            this.podium1MannequinUuid = m1.getUUID();
            this.podium1MannequinDim = m1.level().dimension();
        }

        // 2nd Place Mannequin
        Mannequin m2 = createPodiumMannequin(
                server,
                data.getPodium2Pos(),
                runnerUp,
                runnerUpColor,
                "§7§l2. PLATZ §8(" + Math.min(p1Wins, p2Wins) + ":" + Math.max(p1Wins, p2Wins) + ")",
                2.5
        );
        if (m2 != null) {
            this.podium2MannequinUuid = m2.getUUID();
            this.podium2MannequinDim = m2.level().dimension();
        }
    }

    private Mannequin createPodiumMannequin(
            MinecraftServer server,
            GameshowData.SpawnPoint sp,
            ServerPlayer player,
            TeamColor color,
            String subtitle,
            double fallbackSideOffset
    ) {
        if (player == null) return null;
        ServerLevel level;
        double x, y, z;
        float yaw, pitch;

        if (sp != null && sp.set()) {
            level = server.getLevel(sp.dimension());
            x = sp.x();
            y = sp.y();
            z = sp.z();
            yaw = sp.yaw();
            pitch = sp.pitch();
        } else {
            level = (ServerLevel) player.level();
            double rad = Math.toRadians(player.getYRot());
            x = player.getX() - Math.sin(rad) * 4.0 + Math.cos(rad) * fallbackSideOffset;
            y = player.getY();
            z = player.getZ() + Math.cos(rad) * 4.0 + Math.sin(rad) * fallbackSideOffset;
            yaw = player.getYRot() + 180.0f;
            pitch = 0.0f;
        }

        if (level == null) return null;

        Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.TRIGGERED);
        if (mannequin == null) return null;

        mannequin.setPos(x, y, z);
        mannequin.setYRot(yaw);
        mannequin.setXRot(pitch);
        mannequin.setYHeadRot(yaw);
        mannequin.setYBodyRot(yaw);
        mannequin.setNoGravity(true);
        mannequin.setPermanentlyInvulnerable(true);
        mannequin.addTag(PODIUM_MANNEQUIN_TAG);

        MannequinAccess access = (MannequinAccess) mannequin;
        access.browars$setProfile(ResolvableProfile.createResolved(player.getGameProfile()));
        access.browars$setImmovable(true);
        access.browars$setHideDescription(false);
        access.browars$setDescription(Component.literal(subtitle));

        mannequin.setCustomName(Component.literal(color.chatPrefix + "§l" + player.getPlainTextName()));
        mannequin.setCustomNameVisible(true);

        level.addFreshEntity(mannequin);
        return mannequin;
    }

    public void removePodiumMannequins(MinecraftServer server) {
        if (server != null) {
            if (podium1MannequinUuid != null && podium1MannequinDim != null) {
                ServerLevel level = server.getLevel(podium1MannequinDim);
                if (level != null) {
                    Entity entity = level.getEntity(podium1MannequinUuid);
                    if (entity != null) entity.discard();
                }
            }
            if (podium2MannequinUuid != null && podium2MannequinDim != null) {
                ServerLevel level = server.getLevel(podium2MannequinDim);
                if (level != null) {
                    Entity entity = level.getEntity(podium2MannequinUuid);
                    if (entity != null) entity.discard();
                }
            }
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                GameshowData data = GameshowSavedData.get(overworld).getData();
                discardTaggedMannequinsNear(server, data.getPodium1Pos());
                discardTaggedMannequinsNear(server, data.getPodium2Pos());
            }
        }
        podium1MannequinUuid = null;
        podium1MannequinDim = null;
        podium2MannequinUuid = null;
        podium2MannequinDim = null;
    }

    private void discardTaggedMannequinsNear(MinecraftServer server, GameshowData.SpawnPoint sp) {
        if (sp == null || !sp.set()) return;
        ServerLevel level = server.getLevel(sp.dimension());
        if (level == null) return;
        BlockPos blockPos = BlockPos.containing(sp.x(), sp.y(), sp.z());
        if (!level.hasChunkAt(blockPos)) return;
        AABB searchBox = new AABB(sp.x() - 6.0, sp.y() - 4.0, sp.z() - 6.0, sp.x() + 6.0, sp.y() + 6.0, sp.z() + 6.0);
        for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, searchBox, e -> e.entityTags().contains(PODIUM_MANNEQUIN_TAG))) {
            m.discard();
        }
    }

    public void finishShowAndReturnToWorldSpawn(MinecraftServer server) {
        ServerPlayer p1 = p1Uuid != null ? server.getPlayerList().getPlayer(p1Uuid) : null;
        ServerPlayer p2 = p2Uuid != null ? server.getPlayerList().getPlayer(p2Uuid) : null;

        removePodiumMannequins(server);
        resetShowState();
        syncHudToAll(server);

        if (p1 != null) {
            p1.removeEffect(MobEffects.INVISIBILITY);
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p1);
        }
        if (p2 != null) {
            p2.removeEffect(MobEffects.INVISIBILITY);
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p2);
        }
    }

    public void stopShow(MinecraftServer server, String reason) {
        if (!isShowActive()) return;

        ServerPlayer p1 = p1Uuid != null ? server.getPlayerList().getPlayer(p1Uuid) : null;
        ServerPlayer p2 = p2Uuid != null ? server.getPlayerList().getPlayer(p2Uuid) : null;

        Phase oldPhase = this.phase;
        resetShowState();

        if (oldPhase == Phase.IN_GAME && MinigameManager.getInstance().isGameRunning()) {
            MinigameManager.getInstance().stopCurrentMinigame(reason);
        }

        removePodiumMannequins(server);
        syncHudToAll(server);

        if (p1 != null) {
            p1.removeEffect(MobEffects.INVISIBILITY);
            ServerPlayNetworking.send(p1, new SyncGameshowColorSelectPayload(false, 1, "", "red", false, "", "blue", false, 0));
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p1);
        }
        if (p2 != null) {
            p2.removeEffect(MobEffects.INVISIBILITY);
            ServerPlayNetworking.send(p2, new SyncGameshowColorSelectPayload(false, 2, "", "red", false, "", "blue", false, 0));
            WorldSpawnRespawnHandler.teleportToWorldSpawn(p2);
        }
        updateAllBoards(server);
    }

    public boolean skipCurrentStep(MinecraftServer server) {
        if (!isShowActive()) return false;
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
        if (p1 == null || p2 == null) return false;
        GameshowData data = GameshowSavedData.get(server.overworld()).getData();

        switch (phase) {
            case COLOR_SELECT -> {
                syncColorSelectToPlayers(p1, p2, false);
                beginIntro(server, p1, p2);
            }
            case INTRO, EVALUATION, FINALE_EVALUATION, PODIUM -> this.phaseTicks = 1;
            case ROULETTE -> launchSelectedMinigame(server, p1, p2);
            case LOBBY -> beginNextRoulette(server, data, p1, p2);
            case IN_GAME -> {
                if (MinigameManager.getInstance().getActiveGame() instanceof AbstractDuelGame<?> duel) {
                    MinigameManager.getInstance().stopCurrentMinigame("Phase übersprungen");
                    onMinigameCompleted(duel, p1Uuid, "Übersprungen");
                }
            }
            default -> {}
        }
        return true;
    }

    public void onPlayerRespawn(ServerPlayer player) {
        if (!isPlayerInShow(player.getUUID())) return;
        MinecraftServer server = player.level().getServer();
        GameshowData data = GameshowSavedData.get(server.overworld()).getData();
        switch (phase) {
            case COLOR_SELECT, INTRO, ROULETTE, EVALUATION, FINALE_EVALUATION ->
                    teleportToSpawnPoint(server, data.getBlackRoomSpawn(), player, null);
            case LOBBY ->
                    teleportToSpawnPoint(server, data.getLobbySpawn(), player, null);
            case PODIUM ->
                    teleportToSpawnPoint(server, data.getPodiumViewerSpawn(), player, null);
            default ->
                    WorldSpawnRespawnHandler.teleportToWorldSpawn(player);
        }
    }

    private void resetShowState() {
        this.phase = Phase.INACTIVE;
        this.phaseTicks = 0;
        this.phaseTotalTicks = 0;
        this.p1Uuid = null;
        this.p2Uuid = null;
        this.p1ColorReady = false;
        this.p2ColorReady = false;
        this.roulettePoolIds.clear();
        this.selectedGameType = null;
        this.startingMinigameInternal = false;
    }

    private void saveShowProgress(MinecraftServer server, boolean finished, String overallWinner) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        GameshowSavedData saved = GameshowSavedData.get(overworld);
        GameshowData.ShowRecord record = new GameshowData.ShowRecord(
                p1Name, p1Color.id, p1Wins,
                p2Name, p2Color.id, p2Wins,
                totalGames,
                finished,
                overallWinner,
                new ArrayList<>(playedGames)
        );
        saved.getData().setLastShow(record);
        GameshowSavedData.save(overworld);
    }

    public void updateAllBoards(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        GameshowSavedData saved = GameshowSavedData.get(overworld);
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : saved.getData().getBoardBlocks()) {
                if (level.hasChunkAt(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof GameshowBoardBlockEntity boardBe) {
                        boardBe.updateHologram(level);
                    }
                }
            }
        }
    }

    public List<GameshowGameType> getReadyGames(MinecraftServer server) {
        List<GameshowGameType> ready = new ArrayList<>();
        for (GameshowGameType type : GameshowGameType.values()) {
            if (type.isReady(server)) {
                ready.add(type);
            }
        }
        return ready;
    }

    private void teleportToBlackRoom(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        teleportToSpawnPoint(server, data.getBlackRoomSpawn(), p1, p2);
        maintainBlackRoomPlayers(server, data, p1, p2);
    }

    private void teleportToSpawnPoint(MinecraftServer server, GameshowData.SpawnPoint sp, ServerPlayer p1, ServerPlayer p2) {
        if (!sp.set()) {
            if (p1 != null) WorldSpawnRespawnHandler.teleportToWorldSpawn(p1);
            if (p2 != null) WorldSpawnRespawnHandler.teleportToWorldSpawn(p2);
            return;
        }
        ServerLevel targetLevel = server.getLevel(sp.dimension());
        if (targetLevel == null) targetLevel = server.overworld();
        if (p1 != null) {
            p1.stopRiding();
            p1.teleportTo(targetLevel, sp.x(), sp.y(), sp.z(), Set.of(), sp.yaw(), sp.pitch(), true);
            p1.setDeltaMovement(0, 0, 0);
            p1.fallDistance = 0;
        }
        if (p2 != null) {
            p2.stopRiding();
            p2.teleportTo(targetLevel, sp.x(), sp.y(), sp.z(), Set.of(), sp.yaw(), sp.pitch(), true);
            p2.setDeltaMovement(0, 0, 0);
            p2.fallDistance = 0;
        }
    }

    private void maintainBlackRoomPlayers(MinecraftServer server, GameshowData data, ServerPlayer p1, ServerPlayer p2) {
        GameshowData.SpawnPoint sp = data.getBlackRoomSpawn();
        freezeInBlackRoom(server, sp, p1);
        freezeInBlackRoom(server, sp, p2);
    }

    private void freezeInBlackRoom(MinecraftServer server, GameshowData.SpawnPoint sp, ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return;
        maintainLobbyPlayer(player);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));

        if (sp.set()) {
            ServerLevel targetLevel = server.getLevel(sp.dimension());
            if (targetLevel != null) {
                if (player.level() != targetLevel
                        || player.distanceToSqr(sp.x(), sp.y(), sp.z()) > 0.01
                        || Math.abs(player.getYRot() - sp.yaw()) > 1.0f
                        || Math.abs(player.getXRot() - sp.pitch()) > 1.0f) {
                    player.teleportTo(targetLevel, sp.x(), sp.y(), sp.z(), Set.of(), sp.yaw(), sp.pitch(), false);
                }
            }
        }
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }

    private void maintainLobbyPlayer(ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return;
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
        player.clearFire();
        if (!de.browars.util.PermissionsHelper.isOp(player) && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    private static boolean isWallAt(ServerLevel level, int x, int airY, int z) {
        BlockPos pos = new BlockPos(x, airY, z);
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (!shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 0.5) {
            return true;
        }
        BlockPos above = new BlockPos(x, airY + 1, z);
        var shapeAbove = level.getBlockState(above).getCollisionShape(level, above);
        return !shapeAbove.isEmpty();
    }

    private void spawnBorderParticles(ServerLevel level, GameshowData.AreaBox area, int colorHex) {
        BlockPos min = area.min();
        BlockPos max = area.max();
        double minX = min.getX();
        double maxX = max.getX() + 1.0;
        double minZ = min.getZ();
        double maxZ = max.getZ() + 1.0;

        // Detect exact floor surface Y (handles full blocks, bottom slabs, and carpets)
        BlockPos centerFloorCheck = new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
        var floorShape = level.getBlockState(centerFloorCheck).getCollisionShape(level, centerFloorCheck);
        double floorOffset = (!floorShape.isEmpty() && floorShape.max(net.minecraft.core.Direction.Axis.Y) > 0.2)
                ? floorShape.max(net.minecraft.core.Direction.Axis.Y)
                : 0.0;
        double floorSurfaceY = min.getY() + floorOffset;
        int airY = (int) Math.ceil(floorSurfaceY);

        double y1 = floorSurfaceY + 0.15;
        double y2 = floorSurfaceY + 0.55;

        DustParticleOptions dust = new DustParticleOptions(colorHex, 1.0f);
        double step = 0.2;

        // North (minZ) and South (maxZ) borders
        for (double x = minX; x <= maxX + 0.001; x += step) {
            int bx = Math.clamp((int) Math.floor(x), min.getX(), max.getX());

            // North edge: check inside block at min.getZ() and outside neighbor at min.getZ() - 1
            if (!isWallAt(level, bx, airY, min.getZ())
                    && !isWallAt(level, bx, airY, min.getZ() - 1)) {
                level.sendParticles(dust, x, y1, minZ, 1, 0, 0, 0, 0);
                level.sendParticles(dust, x, y2, minZ, 1, 0, 0, 0, 0);
            }

            // South edge: check inside block at max.getZ() and outside neighbor at max.getZ() + 1
            if (!isWallAt(level, bx, airY, max.getZ())
                    && !isWallAt(level, bx, airY, max.getZ() + 1)) {
                level.sendParticles(dust, x, y1, maxZ, 1, 0, 0, 0, 0);
                level.sendParticles(dust, x, y2, maxZ, 1, 0, 0, 0, 0);
            }
        }

        // West (minX) and East (maxX) borders
        for (double z = minZ + step; z < maxZ - 0.001; z += step) {
            int bz = Math.clamp((int) Math.floor(z), min.getZ(), max.getZ());

            // West edge: check inside block at min.getX() and outside neighbor at min.getX() - 1
            if (!isWallAt(level, min.getX(), airY, bz)
                    && !isWallAt(level, min.getX() - 1, airY, bz)) {
                level.sendParticles(dust, minX, y1, z, 1, 0, 0, 0, 0);
                level.sendParticles(dust, minX, y2, z, 1, 0, 0, 0, 0);
            }

            // East edge: check inside block at max.getX() and outside neighbor at max.getX() + 1
            if (!isWallAt(level, max.getX(), airY, bz)
                    && !isWallAt(level, max.getX() + 1, airY, bz)) {
                level.sendParticles(dust, maxX, y1, z, 1, 0, 0, 0, 0);
                level.sendParticles(dust, maxX, y2, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void syncColorSelectToPlayers(ServerPlayer p1, ServerPlayer p2, boolean open) {
        int remSec = Math.max(0, (phaseTicks + 19) / 20);
        if (p1 != null) {
            ServerPlayNetworking.send(p1, new SyncGameshowColorSelectPayload(
                    open, 1, p1Name, p1Color.id, p1ColorReady, p2Name, p2Color.id, p2ColorReady, remSec
            ));
        }
        if (p2 != null) {
            ServerPlayNetworking.send(p2, new SyncGameshowColorSelectPayload(
                    open, 2, p1Name, p1Color.id, p1ColorReady, p2Name, p2Color.id, p2ColorReady, remSec
            ));
        }
    }

    public SyncGameshowHudPayload buildHudPayload() {
        if (!isShowActive()) return SyncGameshowHudPayload.INACTIVE;
        return new SyncGameshowHudPayload(
                true,
                phase.name(),
                phaseTicks,
                phaseTotalTicks,
                currentGameNumber,
                totalGames,
                p1Name,
                p1Color.id,
                p1Wins,
                p2Name,
                p2Color.id,
                p2Wins,
                new ArrayList<>(roulettePoolIds),
                rouletteTargetIndex,
                selectedGameType != null ? selectedGameType.id : "",
                lastWinnerName,
                lastWinnerColorId,
                lastScoreSummary,
                new ArrayList<>(playedGames)
        );
    }

    public void syncHudToAll(MinecraftServer server) {
        SyncGameshowHudPayload payload = buildHudPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public void syncPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, buildHudPayload());
    }

    private void playSoundTo(ServerPlayer p1, ServerPlayer p2, SoundEvent sound, float pitch) {
        playSoundToPlayer(p1, sound, pitch);
        playSoundToPlayer(p2, sound, pitch);
    }

    private void playSoundToPlayer(ServerPlayer player, SoundEvent sound, float pitch) {
        if (player == null) return;
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.MASTER,
                player.getX(),
                player.getY(),
                player.getZ(),
                1.0f,
                pitch,
                player.level().getRandom().nextLong()
        ));
    }
}
