package de.browars.minigame.anvilrain;

import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.colorfloor.ColorFloorArenaData;
import de.browars.minigame.colorfloor.ColorFloorGame;
import de.browars.minigame.sumo.SumoArenaData;
import de.browars.minigame.sumo.SumoGame;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class MinigameIntegrationTests {
    @GameTest(skyAccess = true, maxTicks = 100)
    public void anvilEntitiesAndImpact(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        double x = floor.getX() + 0.5, z = floor.getZ() + 0.5, y = floor.getY() + 1;
        AABB area = new AABB(floor).inflate(3, 24, 3);
        FallingAnvil falling = new FallingAnvil(level, x, z, y, 60);
        try {
            helper.assertTrue(level.getEntitiesOfClass(Display.BlockDisplay.class, area).size() == 1, "One visible anvil");
            for (Display display : level.getEntitiesOfClass(Display.class, area)) helper.assertFalse(display.shouldBeSaved(), "Temporary displays must not persist across restart");
            helper.assertFalse(falling.advance(), "First movement must glide, not land");
            helper.assertFalse(falling.intersects(new AABB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3)), "High anvil cannot hit a player on the ground");
            boolean hit = false;
            for (int i = 1; i < 60; i++) {
                boolean landed = falling.advance();
                hit |= falling.intersects(new AABB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3));
                helper.assertTrue(landed == (i == 59), "Only the last tick lands");
            }
            helper.assertTrue(hit, "Swept collision detects the player before impact");
            falling.impact();
            helper.assertTrue(falling.removed(), "Impact removes both displays");
            helper.assertTrue(level.getBlockState(floor).is(Blocks.STONE), "Impact leaves the floor intact");
            helper.assertTrue(level.getBlockState(floor.above()).isAir(), "Impact never places an anvil block");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Impact drops no items");
        } finally { falling.discard(); }
        helper.succeed();
    }

    @GameTest(skyAccess = true, maxTicks = 200)
    public void allGamesShareLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var manager = MinigameManager.getInstance();
        BlockPos min = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = min.offset(4, 0, 4);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        double ground = min.getY() + 1;
        first.setGameMode(GameType.CREATIVE);
        first.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        first.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(7);
        try {
            var sumoArena = new SumoArenaData();
            sumoArena.setLossZone(min.below(2), max.below(2), level.dimension());
            sumoArena.setSpawn1(min.getX() + 1, ground, min.getZ() + 1, 0, 0);
            sumoArena.setSpawn2(max.getX(), ground, max.getZ(), 180, 0);
            var sumo = new SumoGame(server, level, sumoArena, first, second, TeamColor.RED, TeamColor.BLUE);
            manager.startMinigame(() -> sumo);
            for (int i = 0; i < 100; i++) sumo.tick();
            helper.assertTrue(sumo.isFightActive(), "Common countdown starts Sumo after 100 ticks");
            first.attack(second);
            helper.assertTrue(first.getAttackStrengthScale(0) == 1.0f,
                    "Sumo stick stays ready immediately after an attack");
            first.setPos(min.getX() + 1, min.getY() - 2, min.getZ() + 1);
            second.setPos(max.getX(), min.getY() - 2, max.getZ());
            sumo.tick();
            helper.assertTrue(sumo.getHudState().p1Score() == 0 && sumo.getHudState().p2Score() == 0, "Simultaneous Sumo losses award no points");
            helper.assertTrue(sumo.getHudState().currentRound() == 1, "Draw repeats the round");

            var colorArena = new ColorFloorArenaData();
            colorArena.setArenaBounds(min, max, level.dimension());
            colorArena.setSpawn1(min.getX() + 1, ground, min.getZ() + 1, 0, 0);
            colorArena.setSpawn2(max.getX(), ground, max.getZ(), 180, 0);
            var color = new ColorFloorGame(server, level, colorArena, first, second, TeamColor.RED, TeamColor.BLUE);
            manager.startMinigame(() -> color);
            helper.assertFalse(sumo.isRunning(), "Starting Color Floor stops Sumo first");
            for (int i = 0; i < 100; i++) color.tick();
            boolean targetExists = false;
            for (var value : ColorFloorGame.FloorColor.values()) {
                if (value.hexColor != color.getHudState().targetColorHex()) continue;
                for (BlockPos pos : BlockPos.betweenClosed(min, max)) targetExists |= level.getBlockState(pos).is(value.block);
            }
            helper.assertTrue(targetExists, "Color Floor always selects an existing color");
            first.setPos(min.getX() + 1, min.getY(), min.getZ() + 1);
            color.tick();
            helper.assertTrue(color.getHudState().p2Score() == 1, "Landing one block below the playing surface awards the opponent one point");
            manager.stopCurrentMinigame("test");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Color Floor restores every original block");
            assertRestored(helper, first);

            var arena = new AnvilRainArenaData();
            arena.setArenaBounds(min, max, level.dimension());
            arena.setSpawn1(min.getX() + 1, ground, min.getZ() + 1, 0, 0);
            arena.setSpawn2(max.getX(), ground, max.getZ(), 180, 0);
            var rain = new AnvilRainGame(server, level, arena, first, second, TeamColor.RED, TeamColor.BLUE);
            manager.startMinigame(() -> rain);
            for (int i = 0; i < 101; i++) rain.tick();
            var displays = level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(min).minmax(new AABB(max)).inflate(2, 23, 2));
            helper.assertTrue(!displays.isEmpty(), "Anvil Rain starts spawning after countdown");
            var anvil = displays.getFirst();
            first.setPos(anvil.getX(), ground, anvil.getZ());
            second.setPos(anvil.getX() < min.getX() + 2.5 ? max.getX() + .5 : min.getX() + .5,
                    ground, anvil.getZ() < min.getZ() + 2.5 ? max.getZ() + .5 : min.getZ() + .5);
            for (int i = 0; i < 180 && rain.isRoundActive(); i++) rain.tick();
            helper.assertTrue(rain.getHudState().p2Score() == 1, "The first anvil hit awards exactly one point");
            helper.assertFalse(rain.isRoundActive(), "A hit ends the round immediately");
            for (int point = 2; point <= 5; point++) {
                for (int i = 0; i < 150; i++) rain.tick();
                helper.assertTrue(rain.isRoundActive(), "Next round has the same pause and countdown");
                first.setPos(min.getX() - 2, ground, min.getZ());
                rain.tick();
                helper.assertTrue(rain.getHudState().p2Score() == point, "First-to-five score advances once per round");
            }
            for (int i = 0; i < 120; i++) rain.tick();
            helper.assertFalse(rain.isRunning(), "Match ends after the fifth point and victory display");
            assertRestored(helper, first);
            helper.assertTrue(level.getEntitiesOfClass(Display.class, new AABB(min).minmax(new AABB(max)).inflate(2, 23, 2),
                    entity -> entity.entityTags().contains(FallingAnvil.TEMPORARY_TAG)).isEmpty(), "Match end removes all hazards");

            var solo = new AnvilRainGame(server, level, arena, first, null, TeamColor.RED, null);
            manager.startMinigame(() -> solo);
            for (int i = 0; i < 100; i++) solo.tick();
            first.setPos(min.getX() - 2, ground, min.getZ());
            solo.tick();
            for (int i = 0; i < 80; i++) solo.tick();
            helper.assertFalse(solo.isRunning(), "Solo loss ends the match rather than restarting");
            assertRestored(helper, first);
        } finally {
            manager.stopCurrentMinigame("test cleanup");
            server.getPlayerList().remove(first);
            server.getPlayerList().remove(second);
            first.discard(); second.discard();
        }
        helper.succeed();
    }
    private static void assertRestored(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND) && player.getInventory().getItem(0).getCount() == 7, "Personal inventory restored");
        helper.assertTrue(player.isCreative(), "Original game mode restored");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_SPEED).getBaseValue() == 7, "Original attack speed restored");
    }
}
