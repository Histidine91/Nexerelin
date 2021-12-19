package exerelin.utilities;

import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

public class NexUtilsGUI {
		
	public static TooltipCreator createSimpleTextTooltip(final String str, final float width) {
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return width;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				tooltip.addPara(str, 0);
			}
		};
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
			String elite = skill.getLevel() > 1 ? StringHelper.getString("elite") + " " : "";
			String skillString = String.format("%s%s", elite, skill.getSkill().getName());
			TooltipMakerAPI inner = tooltip.beginImageWithText(skill.getSkill().getSpriteName(), 18);
			inner.addPara(skillString, 0, Misc.getHighlightColor(), "Elite");
			tooltip.addImageWithText(2);
		}
	}
	
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
}
