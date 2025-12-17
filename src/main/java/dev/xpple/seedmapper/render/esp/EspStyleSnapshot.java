package dev.xpple.seedmapper.render.esp;

public record EspStyleSnapshot(
    int outlineColor,
    float outlineAlpha,
    boolean fillEnabled,
    int fillColor,
    float fillAlpha,
    boolean rainbow,
    float rainbowSpeed
) {
}
