package exerelin.utilities;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class NexUtilsGUI {
	
	public static CustomPanelGenResult addPanelWithFixedWidthImage(CustomPanelAPI external,
			CustomUIPanelPlugin plugin, float width, float height, 
			String text, float textWidth, float textPad, String imagePath, float imageWidth, 
			float pad, Color textColor, boolean largeFont, 
			TooltipCreator tooltip)
	{
		if (textColor == null) textColor = Misc.getTextColor();
		
		CustomPanelAPI panel = external.createCustomPanel(width, height, plugin);
		
		TooltipMakerAPI image = panel.createUIElement(height, imageWidth, false);
		if (imagePath != null) 
			image.addImage(imagePath, imageWidth, 0);
		panel.addUIElement(image).inTL(0, 0);
		
		TooltipMakerAPI textHolder = panel.createUIElement(textWidth, height, false);
		if (largeFont)
			textHolder.setParaSmallInsignia();
		textHolder.addPara(text, textColor, pad);
		if (tooltip != null)
			textHolder.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.BELOW);
		panel.addUIElement(textHolder).rightOfTop(image, textPad);
		
		CustomPanelGenResult result = new CustomPanelGenResult(panel);
		result.elements.add(image);
		result.elements.add(textHolder);
		return result;
	}
	
	public static class CustomPanelGenResult {
		public CustomPanelAPI panel;
		public List<UIComponentAPI> elements = new ArrayList<>();
		
		public CustomPanelGenResult(CustomPanelAPI panel) {
			this.panel = panel;
		}
	}
}
