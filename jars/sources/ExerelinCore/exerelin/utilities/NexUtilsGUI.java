package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import lombok.RequiredArgsConstructor;
import org.magiclib.util.MagicTxt;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NexUtilsGUI {
		
	public static TooltipCreator createSimpleTextTooltip(final String str, final float width) {
		return new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return width;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				MagicTxt.addPara(tooltip, str, 0, Misc.getTextColor(), Misc.getHighlightColor());
			}
		};
	}
	
	public static TooltipMakerAPI createFleetMemberImageForPanel(CustomPanelAPI panel, 
			FleetMemberAPI member, float width, float height) 
	{
		TooltipMakerAPI image = panel.createUIElement(width, height, false);
		
		addSingleShipList(image, width, member, 0);
		return image;
	}
	
	public static TooltipMakerAPI createPersonImageForPanel(CustomPanelAPI panel, 
			PersonAPI person, float width, float height) 
	{
		TooltipMakerAPI image = panel.createUIElement(width, height, false);
		image.addImage(person.getPortraitSprite(), height, 0);
		return image;
	}
		
	// yeeted from Stelnet (GPL v3)
	// https://github.com/jaghaimo/stelnet
	public static String getAbbreviatedName(PersonAPI person) {
        FullName fullName = person.getName();
		if (fullName.getLast().isEmpty()) return fullName.getFullName();
        return String.format("%c. %s", fullName.getFirst().charAt(0), fullName.getLast());
    }
	
	// yeeted from Stelnet (GPL v3)
	// https://github.com/jaghaimo/stelnet
	@RequiredArgsConstructor
	public static class ShowPeopleOfficerTooltip implements TooltipCreator {

		private final PersonAPI person;

		@Override
		public boolean isTooltipExpandable(Object tooltipParam) {
			return false;
		}

		@Override
		public float getTooltipWidth(Object tooltipParam) {
			return 250;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			String level = String.format(" %s (L%d)", person.getNameString(), person.getStats().getLevel());
			tooltip.addSectionHeading(level, Alignment.LMID, 2);
			tooltip.addSpacer(4);
			List<MutableCharacterStatsAPI.SkillLevelAPI> skills = person.getStats().getSkillsCopy();
			for (MutableCharacterStatsAPI.SkillLevelAPI skill : skills) {
				if (skill.getSkill().isAptitudeEffect()) continue;
				addSkill(tooltip, skill);
			}
		}

		private void addSkill(TooltipMakerAPI tooltip, MutableCharacterStatsAPI.SkillLevelAPI skill) {
			String elite = skill.getLevel() > 1 ? StringHelper.getString("elite", true) + " " : "";
			String skillString = String.format("%s%s", elite, skill.getSkill().getName());
			TooltipMakerAPI inner = tooltip.beginImageWithText(skill.getSkill().getSpriteName(), 18);
			inner.addPara(skillString, 0, Misc.getHighlightColor(), "Elite");
			tooltip.addImageWithText(2);
		}
	}

	/**
	 * Generates a panel where an image will be shown on the left end, with (optional) text to its right. Doesn't need to be a real image,
	 * can be a shipList for instance (or indeed just left empty).
	 * @param external
	 * @param plugin
	 * @param width
	 * @param height
	 * @param text
	 * @param textWidth
	 * @param textPad
	 * @param imagePath
	 * @param imageWidth
	 * @param pad
	 * @param textColor
	 * @param largeFont
	 * @param tooltip
	 * @return A {@code CustomPanelGenResult} with the image holder as the first element and the text holder as the second.
	 */
	public static CustomPanelGenResult addPanelWithFixedWidthImage(CustomPanelAPI external,
			CustomUIPanelPlugin plugin, float width, float height, 
			String text, float textWidth, float textPad, String imagePath, float imageWidth, 
			float pad, Color textColor, boolean largeFont, 
			TooltipCreator tooltip)
	{
		if (textColor == null) textColor = Misc.getTextColor();
		
		CustomPanelAPI panel = external.createCustomPanel(width, height, plugin);
		
		TooltipMakerAPI image = panel.createUIElement(height, imageWidth, false);
		if (imagePath != null) {
			image.addImage(imagePath, imageWidth, 0);
			if (tooltip != null)
				image.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.BELOW);
		}
		
		panel.addUIElement(image).inTL(0, 0);
		
		TooltipMakerAPI textHolder = panel.createUIElement(textWidth, height, false);
		if (text != null) {
			if (largeFont)
				textHolder.setParaSmallInsignia();
			textHolder.addPara(text, textColor, pad);
			textHolder.setParaFontDefault();
			if (tooltip != null)
				textHolder.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.BELOW);
		}
		
		panel.addUIElement(textHolder).rightOfTop(image, textPad);
		
		CustomPanelGenResult result = new CustomPanelGenResult(panel);
		result.elements.add(image);
		result.elements.add(textHolder);
		return result;
	}

	public static PositionAPI addToCustomPanel(CustomPanelAPI holder, UIPanelAPI element) {
		if (element instanceof TooltipMakerAPI)
			return holder.addUIElement((TooltipMakerAPI) element);
		else return holder.addComponent(element);
	}

	public static void placeElementInRows(CustomPanelAPI holder, UIPanelAPI element, List<? extends UIPanelAPI> prevElements,
										  int maxPerRow, float xpad) {

		int numPrevious = prevElements.size();
		if (numPrevious == 0) {
			// first card, place in TL
			addToCustomPanel(holder, element).inTL(0, 3);
			//log.info("Placing card in TL");
		}
		else if (numPrevious % maxPerRow == 0) {
			// row filled, place under first card of previous row
			int rowNum = numPrevious/maxPerRow - 1;
			UIPanelAPI firstOfPrevious = prevElements.get(maxPerRow * rowNum);
			addToCustomPanel(holder, element).belowLeft(firstOfPrevious, 3);
			//log.info("Placing card in new row");
		}
		else {
			// right of last card
			addToCustomPanel(holder, element).rightOfTop(prevElements.get(numPrevious - 1), xpad);
			//log.info("Placing card in current row");
		}
	}
	
	public static void addShipList(TooltipMakerAPI info, float width, List<FleetMemberAPI> ships, float iconWidth, float pad) 
	{
		int numColumns = (int)(width/iconWidth);
		int numRows = (int)Math.ceil((float)ships.size()/numColumns);
		info.addShipList(numColumns, numRows, iconWidth, Global.getSettings().getBasePlayerColor(), ships, pad);
	}
	
	public static void addShipList(TooltipMakerAPI info, float width, int numColumns, List<FleetMemberAPI> ships, float pad) 
	{
		float iconWidth = width/numColumns;
		int numRows = (int)Math.ceil((float)ships.size()/numColumns);
		info.addShipList(numColumns, numRows, iconWidth, Global.getSettings().getBasePlayerColor(), ships, pad);
	}
	
	public static void addSingleShipList(TooltipMakerAPI info, float width, FleetMemberAPI ship, float pad) 
	{
		List<FleetMemberAPI> temp = new ArrayList<>();
		temp.add(ship);
		info.addShipList(1, 1, width, Global.getSettings().getBasePlayerColor(), temp, pad);
	}
	
	public static class CustomPanelGenResult {
		public CustomPanelAPI panel;
		public List<UIComponentAPI> elements = new ArrayList<>();
		
		public CustomPanelGenResult(CustomPanelAPI panel) {
			this.panel = panel;
		}
	}
	
	public static class NullCoreInteractionListener implements CoreInteractionListener {
		@Override
		public void coreUIDismissed() {

		}
	}

	public static class RowSortCalc {
		// provided
		public int numElements;
		public float width;
		/**
		 * Include any padding.
		 */
		public float elementWidth;
		/**
		 * Include any padding.
		 */
		public float elementHeight;

		// computed
		public int numRows;
		public int numPerRow;
		public float height;

		public RowSortCalc(int numElements, float width, float elementWidth, float elementHeight) {
			this.numElements = numElements;
			this.width = width;
			this.elementWidth = elementWidth;
			this.elementHeight = elementHeight;

			numPerRow = (int)(width/elementWidth);
			numRows = (int)Math.ceil((float)numElements/numPerRow);
			height = numRows * elementHeight;
		}
	}
}
