package de.browars.minigame;

/** Common spawn contract; each game's existing saved-data codec remains compatible. */
public interface DuelArena {
    double getSpawn1X(); double getSpawn1Y(); double getSpawn1Z();
    float getSpawn1Yaw(); float getSpawn1Pitch(); boolean hasSpawn1();
    double getSpawn2X(); double getSpawn2Y(); double getSpawn2Z();
    float getSpawn2Yaw(); float getSpawn2Pitch(); boolean hasSpawn2();
    TeamColor getTeam1ColorEnum(); TeamColor getTeam2ColorEnum();

    default Spawn spawn(int number) {
        return number == 1
                ? new Spawn(getSpawn1X(), getSpawn1Y(), getSpawn1Z(), getSpawn1Yaw(), getSpawn1Pitch())
                : new Spawn(getSpawn2X(), getSpawn2Y(), getSpawn2Z(), getSpawn2Yaw(), getSpawn2Pitch());
    }

    default net.minecraft.core.BlockPos getArenaMin() { return null; }
    default net.minecraft.core.BlockPos getArenaMax() { return null; }

    record Spawn(double x, double y, double z, float yaw, float pitch) {}
}
