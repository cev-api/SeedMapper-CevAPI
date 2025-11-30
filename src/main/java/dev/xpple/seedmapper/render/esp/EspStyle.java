package dev.xpple.seedmapper.render.esp;

import com.google.gson.annotations.SerializedName;
import dev.xpple.seedmapper.util.ColorUtils;
import net.minecraft.util.Mth;

public class EspStyle {

    @SerializedName(value = "OutlineColor", alternate = "OutlineColour")
    public String OutlineColor = "#00CFFF";
    public double OutlineAlpha = 0.9D;
    @SerializedName(value = "UseCommandColor", alternate = "UseCommandColour")
    public boolean UseCommandColor = false;
    public boolean FillEnabled = false;
    @SerializedName(value = "FillColor", alternate = "FillColour")
    public String FillColor = "#00CFFF";
    public double FillAlpha = 0.25D;
    public boolean Rainbow = false;
    public double RainbowSpeed = 1.0D;

    public EspStyleSnapshot snapshot(int fallbackColor) {
        int baseColor = this.UseCommandColor ? fallbackColor : ColorUtils.parseHex(this.OutlineColor, fallbackColor);
        int fillColor = ColorUtils.parseHex(this.FillColor, baseColor);
        return new EspStyleSnapshot(
            ColorUtils.ensureOpaque(baseColor),
            ColorUtils.clampAlpha(this.OutlineAlpha),
            this.FillEnabled,
            ColorUtils.ensureOpaque(fillColor),
            ColorUtils.clampAlpha(this.FillAlpha),
            this.Rainbow,
            (float) Mth.clamp(this.RainbowSpeed, 0.05D, 5.0D)
        );
    }

    public static EspStyle defaults() {
        return new EspStyle();
    }

    public static EspStyle useCommandColorDefaults() {
        EspStyle style = new EspStyle();
        style.UseCommandColor = true;
        return style;
    }
}
