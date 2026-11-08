package de.browars.system;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

public class EnvironmentLockManager {
    private static boolean weatherTimeLockEnabled = true;
    private static int tickCounter = 0;

    public static boolean isWeatherTimeLockEnabled() {
        return weatherTimeLockEnabled;
    }

    public static void setWeatherTimeLockEnabled(boolean enabled) {
        weatherTimeLockEnabled = enabled;
    }

    public static void onServerTick(MinecraftServer server) {
        if (!weatherTimeLockEnabled) return;

        tickCounter++;
        // Maintain day time & clear weather every 40 ticks (2 seconds)
        if (tickCounter % 40 == 0) {
            applyEnvironmentLock(server);
        }
    }

    public static void applyEnvironmentLock(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            try {
                // Lock game rules: advanceTime false, advanceWeather false
                level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);

                // Set clear weather in weather data
                WeatherData weather = level.getWeatherData();
                if (weather != null) {
                    if (weather.isRaining() || weather.isThundering() || weather.getRainTime() > 0) {
                        weather.setRaining(false);
                        weather.setThundering(false);
                        weather.setRainTime(0);
                        weather.setThunderTime(0);
                        weather.setClearWeatherTime(1000000);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Run commands to keep world clocks and visual skies perfectly clear and bright
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear 1000000");
        } catch (Exception ignored) {}
    }
}
