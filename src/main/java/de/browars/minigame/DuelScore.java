package de.browars.minigame;

/** Shared first-to-five rule. A simultaneous loss never awards a point. */
public final class DuelScore {
    public static final int TARGET = 5;
    public enum Result { NONE, DRAW, PLAYER_ONE, PLAYER_TWO, SOLO_OUT }
    private final int target;
    private int first;
    private int second;
    private int round = 1;

    public DuelScore() {
        this(TARGET);
    }

    public DuelScore(int target) {
        this.target = target;
    }

    public Result resolve(boolean firstLost, boolean secondLost, boolean solo) {
        if (isFinished() || (!firstLost && !secondLost)) return Result.NONE;
        if (solo) return firstLost ? Result.SOLO_OUT : Result.NONE;
        if (firstLost && secondLost) return Result.DRAW;
        Result result = firstLost ? Result.PLAYER_TWO : Result.PLAYER_ONE;
        if (firstLost) second++; else first++;
        return result;
    }

    public void nextRound() { if (!isFinished()) round++; }
    public boolean isFinished() { return first >= target || second >= target; }
    public int first() { return first; }
    public int second() { return second; }
    public int round() { return round; }
    public int target() { return target; }
}
