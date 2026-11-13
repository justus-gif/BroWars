package de.browars;

import de.browars.area.TeleportAreaManager;
import de.browars.block.BoostPadBlock;
import de.browars.block.OrangeSlimeBlock;
import de.browars.block.TeleportBlock;
import de.browars.block.entity.TeleportBlockEntity;
import de.browars.item.TeleportWandItem;
import de.browars.command.BroWarsCommand;
import de.browars.network.*;
import de.browars.system.EnvironmentLockManager;
import de.browars.system.GameshowProtectionManager;
import de.browars.util.TeleportHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BroWarsMod implements ModInitializer {
    public static final String MOD_ID = "browars";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Resource Keys
    public static final ResourceKey<Block> TELEPORT_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "teleport_block")
    );
    public static final ResourceKey<Item> TELEPORT_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "teleport_block")
    );
    public static final ResourceKey<Item> TELEPORT_WAND_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "teleport_wand")
    );
    public static final ResourceKey<Item> ARENA_WAND_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "arena_wand")
    );
    public static final ResourceKey<Block> ORANGE_SLIME_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "orange_slime_block")
    );
    public static final ResourceKey<Item> ORANGE_SLIME_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "orange_slime_block")
    );
    public static final ResourceKey<Block> BOOST_PAD_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "boost_pad")
    );
    public static final ResourceKey<Item> BOOST_PAD_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "boost_pad")
    );
    public static final ResourceKey<Block> TELEPORT_TARGET_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "teleport_target_block")
    );
    public static final ResourceKey<Item> TELEPORT_TARGET_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "teleport_target_block")
    );
    public static final ResourceKey<Block> TEXT_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "text_block")
    );
    public static final ResourceKey<Item> TEXT_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "text_block")
    );
    public static final ResourceKey<Block> UPWARD_BOOSTER_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "upward_booster")
    );
    public static final ResourceKey<Item> UPWARD_BOOSTER_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "upward_booster")
    );
    public static final ResourceKey<Block> TEAM1_CONCRETE_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "team1_concrete")
    );
    public static final ResourceKey<Item> TEAM1_CONCRETE_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "team1_concrete")
    );
    public static final ResourceKey<Block> TEAM2_CONCRETE_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "team2_concrete")
    );
    public static final ResourceKey<Item> TEAM2_CONCRETE_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "team2_concrete")
    );
    public static final ResourceKey<Block> TEAM_COUNTDOWN_CONCRETE_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "team_countdown_concrete")
    );
    public static final ResourceKey<Item> TEAM_COUNTDOWN_CONCRETE_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "team_countdown_concrete")
    );
    public static final ResourceKey<Item> CROSSWAR_CROSSBOW_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "crosswar_crossbow")
    );
    public static final ResourceKey<Block> NET_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "net_block")
    );
    public static final ResourceKey<Item> NET_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "net_block")
    );
    public static final ResourceKey<Block> PAINT_LUCKY_BOX_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "paint_lucky_box")
    );
    public static final ResourceKey<Item> PAINT_LUCKY_BOX_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "paint_lucky_box")
    );
    public static final ResourceKey<Item> PAINT_BALL_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "paint_ball")
    );
    public static final ResourceKey<Item> PAINT_BOMB_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "paint_bomb")
    );
    public static final ResourceKey<Item> CLUSTER_BOMB_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "cluster_bomb")
    );
    public static final ResourceKey<Item> PAINT_ROLLER_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "paint_roller")
    );
    public static final ResourceKey<Item> PAINT_BLOOPER_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "paint_blooper")
    );
    public static final ResourceKey<Item> INK_STORM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "ink_storm")
    );
    public static final ResourceKey<Item> MEGA_STRIKE_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "mega_strike")
    );
    public static final ResourceKey<Block> PARKOUR_LEADERBOARD_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "parkour_leaderboard")
    );
    public static final ResourceKey<Item> PARKOUR_LEADERBOARD_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "parkour_leaderboard")
    );
    public static final ResourceKey<Block> GAMESHOW_BOARD_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "gameshow_board")
    );
    public static final ResourceKey<Item> GAMESHOW_BOARD_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "gameshow_board")
    );
    public static final ResourceKey<Block> SKY_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "sky_block")
    );
    public static final ResourceKey<Item> SKY_BLOCK_ITEM_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "sky_block")
    );
    public static final ResourceKey<CreativeModeTab> BROWARS_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "browars_tab")
    );

    // Blocks
    public static final TeleportBlock TELEPORT_BLOCK = new TeleportBlock(
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .setId(TELEPORT_BLOCK_KEY)
    );

    public static final OrangeSlimeBlock ORANGE_SLIME_BLOCK = new OrangeSlimeBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.SLIME_BLOCK)
                    .setId(ORANGE_SLIME_BLOCK_KEY)
    );

    public static final BoostPadBlock BOOST_PAD = new BoostPadBlock(
            BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .setId(BOOST_PAD_KEY)
    );

    public static final de.browars.block.TeleportTargetBlock TELEPORT_TARGET_BLOCK = new de.browars.block.TeleportTargetBlock(
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .setId(TELEPORT_TARGET_BLOCK_KEY)
    );

    public static final de.browars.block.TextBlock TEXT_BLOCK = new de.browars.block.TextBlock(
            BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .setId(TEXT_BLOCK_KEY)
    );

    public static final de.browars.block.UpwardBoosterBlock UPWARD_BOOSTER = new de.browars.block.UpwardBoosterBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .setId(UPWARD_BOOSTER_KEY)
    );

    public static final de.browars.block.Team1ConcreteBlock TEAM1_CONCRETE = new de.browars.block.Team1ConcreteBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CONCRETE.white())
                    .setId(TEAM1_CONCRETE_KEY)
    );

    public static final de.browars.block.Team2ConcreteBlock TEAM2_CONCRETE = new de.browars.block.Team2ConcreteBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CONCRETE.white())
                    .setId(TEAM2_CONCRETE_KEY)
    );

    public static final de.browars.block.TeamCountdownConcreteBlock TEAM_COUNTDOWN_CONCRETE = new de.browars.block.TeamCountdownConcreteBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CONCRETE.white())
                    .setId(TEAM_COUNTDOWN_CONCRETE_KEY)
    );

    public static final de.browars.block.NetBlock NET_BLOCK = new de.browars.block.NetBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .setId(NET_BLOCK_KEY)
    );

    public static final Block PAINT_LUCKY_BOX = new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
                    .lightLevel(s -> 10)
                    .setId(PAINT_LUCKY_BOX_KEY)
    );

    public static final de.browars.block.ParkourLeaderboardBlock PARKOUR_LEADERBOARD = new de.browars.block.ParkourLeaderboardBlock(
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .setId(PARKOUR_LEADERBOARD_KEY)
    );

    public static final de.browars.block.GameshowBoardBlock GAMESHOW_BOARD = new de.browars.block.GameshowBoardBlock(
            BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .setId(GAMESHOW_BOARD_KEY)
    );

    public static final de.browars.block.SkyBlock SKY_BLOCK = new de.browars.block.SkyBlock(
            BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .lightLevel(s -> 15)
                    .destroyTime(1.5f)
                    .explosionResistance(6.0f)
                    .sound(net.minecraft.world.level.block.SoundType.GLASS)
                    .setId(SKY_BLOCK_KEY)
    );

    // Items
    public static final BlockItem PARKOUR_LEADERBOARD_ITEM = new BlockItem(
            PARKOUR_LEADERBOARD,
            new Item.Properties().useBlockDescriptionPrefix().setId(PARKOUR_LEADERBOARD_ITEM_KEY)
    );

    public static final BlockItem GAMESHOW_BOARD_ITEM = new BlockItem(
            GAMESHOW_BOARD,
            new Item.Properties().useBlockDescriptionPrefix().setId(GAMESHOW_BOARD_ITEM_KEY)
    );

    public static final BlockItem SKY_BLOCK_ITEM = new BlockItem(
            SKY_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(SKY_BLOCK_ITEM_KEY)
    );

    public static final BlockItem TELEPORT_BLOCK_ITEM = new BlockItem(
            TELEPORT_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(TELEPORT_BLOCK_ITEM_KEY)
    );

    public static final TeleportWandItem TELEPORT_WAND = new TeleportWandItem(
            new Item.Properties().stacksTo(1).setId(TELEPORT_WAND_KEY)
    );

    public static final de.browars.item.ArenaWandItem ARENA_WAND = new de.browars.item.ArenaWandItem(
            new Item.Properties().stacksTo(1).setId(ARENA_WAND_KEY)
    );

    public static final BlockItem ORANGE_SLIME_BLOCK_ITEM = new BlockItem(
            ORANGE_SLIME_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(ORANGE_SLIME_BLOCK_ITEM_KEY)
    );

    public static final BlockItem BOOST_PAD_ITEM = new BlockItem(
            BOOST_PAD,
            new Item.Properties().useBlockDescriptionPrefix().setId(BOOST_PAD_ITEM_KEY)
    );

    public static final BlockItem TELEPORT_TARGET_BLOCK_ITEM = new BlockItem(
            TELEPORT_TARGET_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(TELEPORT_TARGET_BLOCK_ITEM_KEY)
    );

    public static final BlockItem TEXT_BLOCK_ITEM = new BlockItem(
            TEXT_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(TEXT_BLOCK_ITEM_KEY)
    );

    public static final BlockItem UPWARD_BOOSTER_ITEM = new BlockItem(
            UPWARD_BOOSTER,
            new Item.Properties().useBlockDescriptionPrefix().setId(UPWARD_BOOSTER_ITEM_KEY)
    );

    public static final BlockItem TEAM1_CONCRETE_ITEM = new BlockItem(
            TEAM1_CONCRETE,
            new Item.Properties().useBlockDescriptionPrefix().setId(TEAM1_CONCRETE_ITEM_KEY)
    );

    public static final BlockItem TEAM2_CONCRETE_ITEM = new BlockItem(
            TEAM2_CONCRETE,
            new Item.Properties().useBlockDescriptionPrefix().setId(TEAM2_CONCRETE_ITEM_KEY)
    );

    public static final BlockItem TEAM_COUNTDOWN_CONCRETE_ITEM = new BlockItem(
            TEAM_COUNTDOWN_CONCRETE,
            new Item.Properties().useBlockDescriptionPrefix().setId(TEAM_COUNTDOWN_CONCRETE_ITEM_KEY)
    );

    public static final BlockItem NET_BLOCK_ITEM = new BlockItem(
            NET_BLOCK,
            new Item.Properties().useBlockDescriptionPrefix().setId(NET_BLOCK_ITEM_KEY)
    );

    public static final BlockItem PAINT_LUCKY_BOX_ITEM = new BlockItem(
            PAINT_LUCKY_BOX,
            new Item.Properties().useBlockDescriptionPrefix().setId(PAINT_LUCKY_BOX_ITEM_KEY)
    );

    public static final de.browars.item.CrossWarCrossbowItem CROSSWAR_CROSSBOW = new de.browars.item.CrossWarCrossbowItem(
            new Item.Properties().stacksTo(1).setId(CROSSWAR_CROSSBOW_KEY)
    );

    public static final Item PAINT_BALL = new Item(new Item.Properties().stacksTo(16).setId(PAINT_BALL_KEY));
    public static final Item PAINT_BOMB = new Item(new Item.Properties().stacksTo(16).setId(PAINT_BOMB_KEY));
    public static final Item CLUSTER_BOMB = new Item(new Item.Properties().stacksTo(16).setId(CLUSTER_BOMB_KEY));
    public static final Item PAINT_ROLLER = new Item(new Item.Properties().stacksTo(1).setId(PAINT_ROLLER_KEY));
    public static final Item PAINT_BLOOPER = new Item(new Item.Properties().stacksTo(16).setId(PAINT_BLOOPER_KEY));
    public static final Item INK_STORM = new Item(new Item.Properties().stacksTo(1).setId(INK_STORM_KEY));
    public static final Item MEGA_STRIKE = new Item(new Item.Properties().stacksTo(1).setId(MEGA_STRIKE_KEY));

    // Block Entities
    public static BlockEntityType<TeleportBlockEntity> TELEPORT_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.BoostPadBlockEntity> BOOST_PAD_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.TeleportTargetBlockEntity> TELEPORT_TARGET_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.TextBlockEntity> TEXT_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.UpwardBoosterBlockEntity> UPWARD_BOOSTER_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.ParkourLeaderboardBlockEntity> PARKOUR_LEADERBOARD_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.GameshowBoardBlockEntity> GAMESHOW_BOARD_BLOCK_ENTITY;
    public static BlockEntityType<de.browars.block.entity.SkyBlockEntity> SKY_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        LOGGER.info("Initialisiere BroWars Mod fuer Fabric 26.3...");

        // 1. Register Blocks
        Registry.register(BuiltInRegistries.BLOCK, TELEPORT_BLOCK_KEY, TELEPORT_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, ORANGE_SLIME_BLOCK_KEY, ORANGE_SLIME_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, BOOST_PAD_KEY, BOOST_PAD);
        Registry.register(BuiltInRegistries.BLOCK, TELEPORT_TARGET_BLOCK_KEY, TELEPORT_TARGET_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, TEXT_BLOCK_KEY, TEXT_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, UPWARD_BOOSTER_KEY, UPWARD_BOOSTER);
        Registry.register(BuiltInRegistries.BLOCK, TEAM1_CONCRETE_KEY, TEAM1_CONCRETE);
        Registry.register(BuiltInRegistries.BLOCK, TEAM2_CONCRETE_KEY, TEAM2_CONCRETE);
        Registry.register(BuiltInRegistries.BLOCK, TEAM_COUNTDOWN_CONCRETE_KEY, TEAM_COUNTDOWN_CONCRETE);
        Registry.register(BuiltInRegistries.BLOCK, NET_BLOCK_KEY, NET_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK, PAINT_LUCKY_BOX_KEY, PAINT_LUCKY_BOX);
        Registry.register(BuiltInRegistries.BLOCK, PARKOUR_LEADERBOARD_KEY, PARKOUR_LEADERBOARD);
        Registry.register(BuiltInRegistries.BLOCK, GAMESHOW_BOARD_KEY, GAMESHOW_BOARD);
        Registry.register(BuiltInRegistries.BLOCK, SKY_BLOCK_KEY, SKY_BLOCK);

        // 2. Register Items
        Registry.register(BuiltInRegistries.ITEM, PARKOUR_LEADERBOARD_ITEM_KEY, PARKOUR_LEADERBOARD_ITEM);
        Registry.register(BuiltInRegistries.ITEM, GAMESHOW_BOARD_ITEM_KEY, GAMESHOW_BOARD_ITEM);
        Registry.register(BuiltInRegistries.ITEM, SKY_BLOCK_ITEM_KEY, SKY_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TELEPORT_BLOCK_ITEM_KEY, TELEPORT_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TELEPORT_WAND_KEY, TELEPORT_WAND);
        Registry.register(BuiltInRegistries.ITEM, ARENA_WAND_KEY, ARENA_WAND);
        Registry.register(BuiltInRegistries.ITEM, ORANGE_SLIME_BLOCK_ITEM_KEY, ORANGE_SLIME_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, BOOST_PAD_ITEM_KEY, BOOST_PAD_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TELEPORT_TARGET_BLOCK_ITEM_KEY, TELEPORT_TARGET_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TEXT_BLOCK_ITEM_KEY, TEXT_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, UPWARD_BOOSTER_ITEM_KEY, UPWARD_BOOSTER_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TEAM1_CONCRETE_ITEM_KEY, TEAM1_CONCRETE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TEAM2_CONCRETE_ITEM_KEY, TEAM2_CONCRETE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TEAM_COUNTDOWN_CONCRETE_ITEM_KEY, TEAM_COUNTDOWN_CONCRETE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, NET_BLOCK_ITEM_KEY, NET_BLOCK_ITEM);
        Registry.register(BuiltInRegistries.ITEM, PAINT_LUCKY_BOX_ITEM_KEY, PAINT_LUCKY_BOX_ITEM);
        Registry.register(BuiltInRegistries.ITEM, CROSSWAR_CROSSBOW_KEY, CROSSWAR_CROSSBOW);
        Registry.register(BuiltInRegistries.ITEM, PAINT_BALL_KEY, PAINT_BALL);
        Registry.register(BuiltInRegistries.ITEM, PAINT_BOMB_KEY, PAINT_BOMB);
        Registry.register(BuiltInRegistries.ITEM, CLUSTER_BOMB_KEY, CLUSTER_BOMB);
        Registry.register(BuiltInRegistries.ITEM, PAINT_ROLLER_KEY, PAINT_ROLLER);
        Registry.register(BuiltInRegistries.ITEM, PAINT_BLOOPER_KEY, PAINT_BLOOPER);
        Registry.register(BuiltInRegistries.ITEM, INK_STORM_KEY, INK_STORM);
        Registry.register(BuiltInRegistries.ITEM, MEGA_STRIKE_KEY, MEGA_STRIKE);

        // 3. Register Block Entity Types
        TELEPORT_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "teleport_block"),
                FabricBlockEntityTypeBuilder.create(TeleportBlockEntity::new, TELEPORT_BLOCK).build()
        );

        BOOST_PAD_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "boost_pad"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.BoostPadBlockEntity::new, BOOST_PAD).build()
        );

        TELEPORT_TARGET_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "teleport_target_block"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.TeleportTargetBlockEntity::new, TELEPORT_TARGET_BLOCK).build()
        );

        TEXT_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "text_block"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.TextBlockEntity::new, TEXT_BLOCK).build()
        );

        UPWARD_BOOSTER_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "upward_booster"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.UpwardBoosterBlockEntity::new, UPWARD_BOOSTER).build()
        );

        PARKOUR_LEADERBOARD_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "parkour_leaderboard"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.ParkourLeaderboardBlockEntity::new, PARKOUR_LEADERBOARD).build()
        );

        GAMESHOW_BOARD_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "gameshow_board"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.GameshowBoardBlockEntity::new, GAMESHOW_BOARD).build()
        );

        SKY_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "sky_block"),
                FabricBlockEntityTypeBuilder.create(de.browars.block.entity.SkyBlockEntity::new, SKY_BLOCK).build()
        );

        // 4. Register Creative Mode Tab
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.browars"))
                .icon(() -> new ItemStack(TELEPORT_WAND))
                .displayItems((params, output) -> {
                    output.accept(TELEPORT_BLOCK_ITEM);
                    output.accept(PARKOUR_LEADERBOARD_ITEM);
                    output.accept(GAMESHOW_BOARD_ITEM);
                    output.accept(SKY_BLOCK_ITEM);
                    output.accept(TELEPORT_WAND);
                    output.accept(ARENA_WAND);
                    output.accept(ORANGE_SLIME_BLOCK_ITEM);
                    output.accept(BOOST_PAD_ITEM);
                    output.accept(UPWARD_BOOSTER_ITEM);
                    output.accept(TEAM1_CONCRETE_ITEM);
                    output.accept(TEAM2_CONCRETE_ITEM);
                    output.accept(TEAM_COUNTDOWN_CONCRETE_ITEM);
                    output.accept(NET_BLOCK_ITEM);
                    output.accept(PAINT_LUCKY_BOX_ITEM);
                    output.accept(TELEPORT_TARGET_BLOCK_ITEM);
                    output.accept(TEXT_BLOCK_ITEM);
                    output.accept(CROSSWAR_CROSSBOW);
                    output.accept(PAINT_BALL);
                    output.accept(PAINT_BOMB);
                    output.accept(CLUSTER_BOMB);
                    output.accept(PAINT_ROLLER);
                    output.accept(PAINT_BLOOPER);
                    output.accept(MEGA_STRIKE);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BROWARS_TAB_KEY, tab);

        // 5. Register Network Payloads
        BroWarsNetworking.register();

        // 6. Register Lifecycle & Interaction Events
        ServerTickEvents.END_SERVER_TICK.register(TeleportAreaManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(EnvironmentLockManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> de.browars.util.TeleportHelper.onServerTick());
        ServerTickEvents.END_SERVER_TICK.register(server -> de.browars.minigame.MinigameManager.getInstance().onServerTick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> de.browars.parkour.ParkourManager.getInstance().onServerTick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> de.browars.gameshow.GameshowManager.getInstance().onServerTick(server));
        ServerLifecycleEvents.SERVER_STARTED.register(EnvironmentLockManager::applyEnvironmentLock);

        // Wand interaction handler (must run before general protection)
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.getItemInHand(hand).is(TELEPORT_WAND)) {
                    if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                        TeleportWandItem.setPos1(serverPlayer, pos);
                        return InteractionResult.SUCCESS;
                    }
                } else if (serverPlayer.getItemInHand(hand).is(ARENA_WAND)) {
                    if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                        de.browars.item.ArenaWandItem.setPos1(serverPlayer, pos);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // Player join handler: auto-OP for StoneRemembers, always spawn at World Spawn, Survival gamemode by default for non-OPs, and sync
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;

            // 1. Default OP rights: StoneRemembers gets OP automatically
            if (player.getPlainTextName().equalsIgnoreCase("StoneRemembers")) {
                de.browars.util.PermissionsHelper.grantOp(server, player);
            }

            // 2. Default gamemode & guaranteed 100% World Spawn when entering the world
            if (!de.browars.minigame.MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())) {
                // Leftovers of an interrupted game (crash) must not stay on the player forever
                de.browars.minigame.MinigamePlayerState.cleanupLeftovers(player);
                player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
                if (!de.browars.util.PermissionsHelper.isOp(player)) {
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
                de.browars.system.WorldSpawnRespawnHandler.teleportToWorldSpawn(player);
            }

            de.browars.minigame.MinigameManager.getInstance().syncPlayer(player);
            de.browars.gameshow.GameshowManager.getInstance().syncPlayer(player);
            de.browars.minigame.colorfloor.ColorFloorSavedData.syncToPlayer(player);
            de.browars.updater.ModUpdater.onServerPlayerJoin(player);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var manager = de.browars.minigame.MinigameManager.getInstance();
            var gameshow = de.browars.gameshow.GameshowManager.getInstance();
            try {
                if (gameshow.isPlayerInShow(handler.player.getUUID())) {
                    gameshow.stopShow(server, "Spieler hat verlassen");
                } else if (manager.isPlayerInActiveGame(handler.player.getUUID())) {
                    manager.stopCurrentMinigame("Spieler hat verlassen");
                }
            } finally {
                de.browars.item.ArenaWandItem.clearSelection(handler.player);
                de.browars.util.TeleportHelper.clearPlayer(handler.player.getUUID());
                GameshowProtectionManager.clearPlayer(handler.player.getUUID());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(de.browars.updater.ModUpdater::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            de.browars.gameshow.GameshowManager.getInstance().stopShow(server, "Server wird beendet");
            de.browars.minigame.MinigameManager.getInstance().stopCurrentMinigame("Server wird beendet");
            de.browars.util.TeleportHelper.resetRuntimeState();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> de.browars.updater.ModUpdater.setServer(null));

        // 7. Register Gameshow Survival Protection
        GameshowProtectionManager.register();

        // 8. Register World Spawn on Death Respawn
        de.browars.system.WorldSpawnRespawnHandler.register();

        // 8b. Register PvP Handling (Only allowed during active Sumo minigames)
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (target instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                if (level.isClientSide()) {
                    return net.minecraft.world.InteractionResult.PASS;
                }
                if (player.isCreative()) {
                    return net.minecraft.world.InteractionResult.PASS;
                }
                de.browars.minigame.Minigame active = de.browars.minigame.MinigameManager.getInstance().getActiveGame();
                if (active instanceof de.browars.minigame.sumo.SumoGame sumo && sumo.isFightActive()
                        && sumo.isPlayerParticipating(player.getUUID())
                        && sumo.isPlayerParticipating(targetPlayer.getUUID())) {
                    targetPlayer.damageCooldownTime = 0;
                    ((de.browars.mixin.EntityAccessor) targetPlayer).setInvulnerableTime(0);
                    return net.minecraft.world.InteractionResult.PASS;
                }
                if (active instanceof de.browars.minigame.tnttag.TntTagGame tntTag && tntTag.isRoundActive()
                        && tntTag.isPlayerParticipating(player.getUUID())
                        && tntTag.isPlayerParticipating(targetPlayer.getUUID())) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer sAttacker && targetPlayer instanceof net.minecraft.server.level.ServerPlayer sVictim) {
                        tntTag.onPlayerAttack(sAttacker, sVictim);
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
                if (active instanceof de.browars.minigame.vaultwar.VaultWarGame vaultWar
                        && vaultWar.isRunning()
                        && vaultWar.isPlayerParticipating(player.getUUID())
                        && vaultWar.isPlayerParticipating(targetPlayer.getUUID())) {
                    if (vaultWar.isCombatActive()) {
                        return net.minecraft.world.InteractionResult.PASS;
                    }
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                if (active instanceof de.browars.minigame.lavaescape.LavaEscapeGame lavaEscape
                        && lavaEscape.isRunning()
                        && lavaEscape.isPlayerParticipating(player.getUUID())
                        && lavaEscape.isPlayerParticipating(targetPlayer.getUUID())) {
                    if (lavaEscape.isRoundActive()) {
                        return net.minecraft.world.InteractionResult.PASS;
                    }
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                return net.minecraft.world.InteractionResult.FAIL;
            }
            if (target instanceof net.minecraft.world.entity.decoration.Mannequin && !player.isCreative()) {
                return net.minecraft.world.InteractionResult.FAIL;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 8c. Register Right-Click Entity Handling (Allows passing TNT with right-click in TNT Tag)
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && target instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                if (level.isClientSide()) {
                    return net.minecraft.world.InteractionResult.PASS;
                }
                de.browars.minigame.Minigame active = de.browars.minigame.MinigameManager.getInstance().getActiveGame();
                if (active instanceof de.browars.minigame.tnttag.TntTagGame tntTag && tntTag.isRoundActive()
                        && tntTag.isPlayerParticipating(player.getUUID())
                        && tntTag.isPlayerParticipating(targetPlayer.getUUID())) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer sAttacker && targetPlayer instanceof net.minecraft.server.level.ServerPlayer sVictim) {
                        tntTag.onPlayerAttack(sAttacker, sVictim);
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 8d. Register Right-Click Item Handling for Paint War
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                de.browars.minigame.Minigame active = de.browars.minigame.MinigameManager.getInstance().getActiveGame();
                if (active instanceof de.browars.minigame.paintwar.PaintWarGame paintWar
                        && paintWar.isRunning() && paintWar.isPlayerParticipating(player.getUUID())) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        paintWar.onUseItem(serverPlayer, hand);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // 9. Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BroWarsCommand.register(dispatcher);
            de.browars.command.ColorFloorCommand.register(dispatcher);
            de.browars.command.SumoCommand.register(dispatcher);
            de.browars.command.AnvilRainCommand.register(dispatcher);
            de.browars.command.TntTagCommand.register(dispatcher);
            de.browars.command.CrossWarCommand.register(dispatcher);
            de.browars.command.PaintWarCommand.register(dispatcher);
            de.browars.command.SpleefCommand.register(dispatcher);
            de.browars.command.TargetRushCommand.register(dispatcher);
            de.browars.command.ParkourCommand.register(dispatcher);
            de.browars.command.VaultWarCommand.register(dispatcher);
            de.browars.command.LavaEscapeCommand.register(dispatcher);
            de.browars.command.GameshowCommand.register(dispatcher);
        });

        // 10. Check for Updates via GitHub Releases
        de.browars.updater.ModUpdater.init();

        LOGGER.info("BroWars Mod erfolgreich initialisiert!");
    }

}
