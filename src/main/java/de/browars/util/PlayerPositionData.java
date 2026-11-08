package de.browars.util;

public record PlayerPositionData(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String dimension
) {}
