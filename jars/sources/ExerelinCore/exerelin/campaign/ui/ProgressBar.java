package exerelin.campaign.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * Hacky progress bar drawer.
 * From Void-Tec; https://github.com/Schaf-Unschaf/Void-Tec/blob/main/src/de/schafunschaf/voidtec/util/ui/ProgressBar.java
 * @author Schaf-Unschaf
 */
public class ProgressBar {

    public static UIComponentAPI addBarLTR(TooltipMakerAPI tooltip, String text, @Nullable Alignment textAlign, @Nullable String font,
                                           float width, float height, float borderSize, float borderMargin, float progress, float pad,
                                           @Nullable Color textColor, Color borderColor, Color bgColor, Color barColor) {
        if (textAlign == null) {
            textAlign = Alignment.MID;
        }
        if (textColor == null) {
            textColor = Misc.getTextColor();
        }

        CustomPanelAPI customPanel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI uiElement = customPanel.createUIElement(width, height, false);
        if (font != null && !font.isEmpty()) {
            uiElement.setParaFont(font);
        }

        float maxBarWidth = width - borderSize * 2 - borderMargin * 2;
        float progressBarWidth = Math.min(Math.max(maxBarWidth / 100 * progress, 0), maxBarWidth);
        String barText = tooltip.shortenString(text, maxBarWidth);

        // background
        uiElement.addSectionHeading("", Color.BLACK, bgColor, Alignment.MID, 0f).getPosition()
                 .setSize(width, height)
                 .inTL(0, 0);
        // bar
        uiElement.addSectionHeading("", Color.BLACK, barColor, Alignment.MID, 0f).getPosition()
                 .setSize(progressBarWidth, height - borderSize * 2 - borderMargin * 2)
                 .inTL(borderMargin + borderSize, borderMargin + borderSize);
        // text
        uiElement.addPara(barText, textColor, 0f).setAlignment(textAlign);
        uiElement.getPrev().getPosition().setSize(maxBarWidth, height - borderSize * 2 - borderMargin * 2)
                 .inTL(borderMargin + borderSize, borderMargin + borderSize);
        // top border
        uiElement.addSectionHeading("", Color.BLACK, borderColor, Alignment.MID, 0f).getPosition()
                 .setSize(width, borderSize)
                 .inTL(0, 0);
        // left border
        uiElement.addSectionHeading("", Color.BLACK, borderColor, Alignment.MID, 0f).getPosition()
                 .setSize(borderSize, height - borderSize * 2)
                 .inTL(0, borderSize);
        // right border
        uiElement.addSectionHeading("", Color.BLACK, borderColor, Alignment.MID, 0f).getPosition()
                 .setSize(borderSize, height - borderSize * 2)
                 .inTL(width - borderSize, borderSize);
        // bot border
        uiElement.addSectionHeading("", Color.BLACK, borderColor, Alignment.MID, 0f).getPosition()
                 .setSize(width, borderSize)
                 .inTL(0, height - borderSize);

        customPanel.addUIElement(uiElement).inTL(0, 0);
        tooltip.addCustom(customPanel, pad);

        return customPanel;
    }
}
