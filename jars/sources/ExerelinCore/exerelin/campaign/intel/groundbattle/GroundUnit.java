package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public class GroundUnit {
	
	public static final float PANEL_WIDTH = 260, PANEL_HEIGHT = 120;
	public static final float PADDING_X = 4;
	public static final float BUTTON_SECTION_WIDTH = 72;
	
	public static final float REORGANIZE_AT_MORALE = 0.3f;
	public static final float BREAK_AT_MORALE = 0.1f;
	
	public final String id = Misc.genUID();
	public int index = 0;
	public GroundBattleIntel intel;
	public String name;
	public int num;
	public FactionAPI faction;
	public boolean isPlayer;
	public boolean isAttacker;
	public ForceType type;
	public int marines;
	public int heavyArms;
	public float morale;
	
	public Set<String> tags = new HashSet<>();
	
	
	public IndustryForBattle location;
	public IndustryForBattle dest;
	
	public void addActionText(TooltipMakerAPI info) {
		Color color = Misc.getTextColor();
		String text = "Waiting";
		if (tags.contains("reorganizing")) {
			text = "Reorganizing";
			color = Misc.getNegativeHighlightColor();
		}
		else if (dest != null) {
			text = "Moving to " + dest.ind.getCurrentName();
		}
		else if (location.containsEnemyOf(isAttacker)) {
			text = "Engaged at " + location.ind.getCurrentName();
		}
		info.addPara(text, color, 3);
	}
	
	public TooltipMakerAPI createUnitCard(CustomPanelAPI parent)
	{
		String commoditySprite = Global.getSettings().getCommoditySpec(type.commodityId).getIconName();
		String crest = faction.getCrest();
		
		TooltipMakerAPI cardHolder = parent.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, commoditySprite, crest));
		TooltipMakerAPI title = card.createUIElement(PANEL_WIDTH, 16, false);
		title.addPara(name, faction.getBaseUIColor(), 3);
		card.addUIElement(title).inTL(0, 0);
		
		// begin stats section
		TooltipMakerAPI stats = card.createUIElement(PANEL_WIDTH - BUTTON_SECTION_WIDTH, PANEL_HEIGHT - 16, false);
		
		// number of marines
		TooltipMakerAPI line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.MARINES).getIconName(), 16);
		line.addPara(marines + "", 0);
		stats.addImageWithText(3);
		
		// number of heavy arms
		if (heavyArms > 0) {
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName(), 16);
			line.addPara(heavyArms + "", 0);
			stats.addImageWithText(3);
		}
		else {
			stats.addSpacer(19);
		}
		
		// morale
		line = stats.beginImageWithText("graphics/icons/insignia/16x_star_circle.png", 16);
		Color moraleColor = Misc.getHighlightColor();
		if (morale > .6) moraleColor = Misc.getPositiveHighlightColor();
		else if (morale < .3) moraleColor = Misc.getNegativeHighlightColor();
		String moraleStr = Math.round(this.morale * 100) + "%";
		line.addPara(moraleStr, moraleColor, 0);
		stats.addImageWithText(3);
		
		// location
		if (location.ind != null) {
			line = stats.beginImageWithText(location.ind.getCurrentImage(), 32);
			addActionText(line);
			stats.addImageWithText(3);
		}
		
		card.addUIElement(stats).belowLeft(title, 0);
		// end stats section
		
		// button holder
		TooltipMakerAPI buttonHolder = card.createUIElement(BUTTON_SECTION_WIDTH, PANEL_HEIGHT - 16, false);
		buttonHolder.addButton("Move", "wololo", BUTTON_SECTION_WIDTH - 6, 16, 0);
		
		card.addUIElement(buttonHolder).rightOfTop(stats, 0);
		
		cardHolder.addCustom(card, 0);
		return cardHolder;
	}
	
	
	public static enum ForceType {
		MARINE(Commodities.MARINES, "troopNameMarine", 1), 
		MECH(Commodities.HAND_WEAPONS, "troopNameMech", 2.5f),
		MILITIA(Commodities.CREW, "troopNameMilitia", 0.4f), 
		REBEL(Commodities.CREW, "troopNameRebel", 0.4f);
		
		public final String commodityId;
		public final String nameStringId;
		public final float strength;
		
		private ForceType(String commodityId, String nameStringId, float strength) 
		{
			this.commodityId = commodityId;
			this.nameStringId = nameStringId;
			this.strength = strength;
		}
		
		public String getName() {
			return getString(nameStringId);
		}
	}
	
	public static enum UnitSize {
		PLATOON(40, 60),
		COMPANY(120, 200),
		BATALLION(500, 800),
		BRIGADE(2000, 3000);
		
		public int avgSize;
		public int maxSize;
		
		private UnitSize(int avgSize, int maxSize) {
			this.avgSize = avgSize;
			this.maxSize = maxSize;
		}
	}
}
