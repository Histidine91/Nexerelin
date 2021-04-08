package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.specialforces.namer.PlanetNamer;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class GroundUnit {
	
	public static final float PANEL_WIDTH = 220, PANEL_HEIGHT = 120;
	public static final float TITLE_HEIGHT = 16, LOCATION_SECTION_HEIGHT = 32;
	public static final float PADDING_X = 4;
	public static final float BUTTON_SECTION_WIDTH = 64;
	
	public static final float REORGANIZE_AT_MORALE = 0.3f;
	public static final float BREAK_AT_MORALE = 0.1f;
	public static final float HEAVY_COUNT_DIVISOR = 8.5f;	// a marine platoon has 8.5x as many marines as a mech platoon has mechs
	
	public final String id = Misc.genUID();
	public int index;
	public GroundBattleIntel intel;
	public String name;
	public FactionAPI faction;
	public boolean isPlayer;
	public boolean isAttacker;
	public ForceType type;
	public int men;
	public int heavyArms;
	public float morale;
	
	public Map<String, Object> data = new HashMap<>();
	
	protected IndustryForBattle location;
	protected IndustryForBattle dest;
	
	public GroundUnit(GroundBattleIntel intel, ForceType type, int num, int index) {
		this.intel = intel;
		this.type = type;
		this.index = index;
		if (type == ForceType.HEAVY) {
			heavyArms = num;
			men = num*2;
		}
		else {
			men = num;
		}
		name = generateName();
	}
	
	public String generateName() {
		String name = Misc.ucFirst(intel.unitSize.getName());
		switch (intel.unitSize) {
			case PLATOON:
			case COMPANY:
				int alphabetIndex = this.index % 26;
				return GBDataManager.NATO_ALPHABET.get(alphabetIndex) + " " + name;
			case BATALLION:
				return index + PlanetNamer.getSuffix(index) + " " + name;
			case REGIMENT:
				return Global.getSettings().getRoman(index) + " " + name;
			default:
				return name + " " + index;
		}
	}
	
	public void setLocation(IndustryForBattle loc) {
		if (loc == location) return;
		if (location != null)
			location.removeUnit(this);
		location = loc;
		loc.addUnit(this);
	}
	
	public void addActionText(TooltipMakerAPI info) {
		Color color = Misc.getTextColor();
		String text = "Waiting";
		if (data.containsKey("reorganizing")) {
			text = "Reorganizing";
			color = Misc.getNegativeHighlightColor();
		}
		else if (dest != null) {
			text = "Moving to " + dest.ind.getCurrentName();
		}
		else if (location == null) {
			text = "Awaiting deployment";
		}
		else if (location.containsEnemyOf(isAttacker)) {
			text = "Engaged at " + location.ind.getCurrentName();
		}
		info.addPara(text, color, 3);
	}
	
	public float getBaseStrength() {
		int num = type == ForceType.HEAVY ? heavyArms : men;
		return num * type.strength;
	}
	
	/**
	 * e.g. two half-companies and one full company will return about the same value.
	 * @return
	 */
	public float getNumUnitEquivalents() {
		float num = type == ForceType.HEAVY ? heavyArms : men;
		return num/intel.unitSize.getAverageSizeForType(type);
	}
	
	public TooltipMakerAPI createUnitCard(CustomPanelAPI parent)
	{
		String commoditySprite = Global.getSettings().getCommoditySpec(type.commodityId).getIconName();
		String crest = faction.getCrest();
		
		TooltipMakerAPI cardHolder = parent.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, commoditySprite, crest));
		TooltipMakerAPI title = card.createUIElement(PANEL_WIDTH, TITLE_HEIGHT, false);
		title.addPara(name, faction.getBaseUIColor(), 3);
		card.addUIElement(title).inTL(0, 0);
		
		// begin stats section
		TooltipMakerAPI stats = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH/2), 
				PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT, false);
		
		// number of marines
		TooltipMakerAPI line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.MARINES).getIconName(), 16);
		line.addPara(men + "", 0);
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
		
		card.addUIElement(stats).belowLeft(title, 0);
		
		TooltipMakerAPI stats2 = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH/2), 
				PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT, false);
		
		card.addUIElement(stats2).rightOfTop(stats, 0);
		// end stats section
		
		// location
		TooltipMakerAPI loc = card.createUIElement(PANEL_WIDTH, LOCATION_SECTION_HEIGHT, false);
		
		String img = location.ind != null ? location.ind.getCurrentImage() : "graphics/illustrations/free_orbit.jpg";
		line = loc.beginImageWithText(img, 32);
		addActionText(line);
		loc.addImageWithText(3);
		card.addUIElement(loc).inBL(0, 2);
		
		// button holder
		TooltipMakerAPI buttonHolder = card.createUIElement(BUTTON_SECTION_WIDTH, PANEL_HEIGHT, false);
		buttonHolder.addButton("Move", "wololo", BUTTON_SECTION_WIDTH - 6, 16, 0);
		card.addUIElement(buttonHolder).inTR(1, 2);
		
		cardHolder.addCustom(card, 0);
		return cardHolder;
	}
	
	
	public static enum ForceType {
		MARINE(Commodities.MARINES, "troopNameMarine", 1), 
		HEAVY(Commodities.HAND_WEAPONS, "troopNameMech", 6f),
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
		REGIMENT(2000, 3500);
		
		public int avgSize;
		public int maxSize;
		
		private UnitSize(int avgSize, int maxSize) {
			this.avgSize = avgSize;
			this.maxSize = maxSize;
		}
		
		public int getAverageSizeForType(ForceType type) {
			int count = avgSize;
			if (type == ForceType.HEAVY) count = Math.round(count/GroundUnit.HEAVY_COUNT_DIVISOR);
			return count;
		}
		
		public String getName() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()));
		}
		
		public String getNamePlural() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()) + "Plural");
		}
	}
}
