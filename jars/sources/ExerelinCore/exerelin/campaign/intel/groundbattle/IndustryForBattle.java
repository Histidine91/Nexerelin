package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.isIndustryTrueDisrupted;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndustryForBattle {
	
	public static final int HEIGHT = 100;
	public static final int COLUMN_WIDTH_INDUSTRY = 320;
	public static final int COLUMN_WIDTH_CONTROLLED_BY = 80;

	public static final int COLUMN_WIDTH_TROOP_ICON = 64;
	public static final int COLUMN_WIDTH_TROOP_NAME = 96;
	public static final int COLUMN_WIDTH_TROOP_COUNT = 48;
	public static final int COLUMN_WIDTH_TROOP_STR = 56;
	public static final int COLUMN_WIDTH_TROOP_BUTTON = 24;
	public static final int COLUMN_WIDTH_TROOP_TOTAL = COLUMN_WIDTH_TROOP_ICON + COLUMN_WIDTH_TROOP_NAME
			+ COLUMN_WIDTH_TROOP_COUNT + COLUMN_WIDTH_TROOP_STR + 2 * COLUMN_WIDTH_TROOP_BUTTON + 16;

	public static final Map<GroundUnit.ForceType, Integer[]> ICON_COUNTS = new HashMap<>();
	
	public GroundBattleIntel intel;
	public Industry ind;
	public boolean heldByDefender = true;

	static {
		ICON_COUNTS.put(GroundUnit.ForceType.MARINE, new Integer[]{0, 50, 100, 200, 500, 1000, 2000});
		ICON_COUNTS.put(GroundUnit.ForceType.MECH, new Integer[]{0, 10, 20, 50, 100, 200, 500});
		ICON_COUNTS.put(GroundUnit.ForceType.MILITIA, new Integer[]{0, 100, 200, 500, 1000, 2000, 5000});
		ICON_COUNTS.put(GroundUnit.ForceType.REBEL, new Integer[]{0, 100, 200, 500, 1000, 2000, 5000});
	}

	public IndustryForBattle(GroundBattleIntel intel, Industry ind) {
		this.intel = intel;
		this.ind = ind;
	}

	public boolean containsEnemyOf(boolean attacker) {
		for (GroundUnit unit : intel.getSide(!attacker).units) {
			if (unit.location != this) continue;
			return true;
		}
		return false;
	}

	/**
	 * Gets the number of icons that should be drawn for a given number of a unit type.
	 * @param type
	 * @param count Number of the unit type present.
	 * @return
	 */
	public static int getNumIcons(GroundUnit.ForceType type, int count) {
		Integer[] array = ICON_COUNTS.get(type);
		int numIcons = 0;
		while (numIcons < array.length) {
			if (count <= array[numIcons]) break;
			numIcons++;
		}
		return numIcons;
	}

	public TooltipMakerAPI renderForcePanel(CustomPanelAPI panel, float width, 
			boolean attacker, UIComponentAPI rightOf) 
	{
		int height = HEIGHT/3;
		TooltipMakerAPI troops = panel.createUIElement(width, height, false);
		MarketAPI market = ind.getMarket();
		Color hp = Misc.getPositiveHighlightColor();
		Color hn = Misc.getNegativeHighlightColor();

		// TODO: list units on this location and total strength

		/*
		for (ForceType type : ForceType.values()) {

			boolean shouldDisplay = true;
			if (force.isDefender) {
				if (type == ForceType.REBEL) shouldDisplay = false;
			} else {
				if (type == ForceType.MILITIA) shouldDisplay = false;
				else if (type == ForceType.REBEL && force.getCurrCount(type) <= 0)
					shouldDisplay = false;
			}

			if (!shouldDisplay) continue;

			List<TooltipMakerAPI> rowElements = new ArrayList<>();
			CustomPanelAPI row = panel.createCustomPanel(width, height, null);

			// draw icons
			TooltipMakerAPI icons = row.createUIElement(COLUMN_WIDTH_TROOP_ICON, height, false);
			icons.beginIconGroup();
			icons.addIcons(market.getCommodityData(type.commodityId), 
					getNumIcons(type, force.getWantedCount(type)), IconRenderMode.NORMAL);
			icons.addIconGroup(height, 0);
			row.addUIElement(icons).inTL(0, 0);
			rowElements.add(icons);

			// print name
			TooltipMakerAPI name = row.createUIElement(COLUMN_WIDTH_TROOP_NAME, height, false);
			name.addPara(type.getName(), 3);
			row.addUIElement(name).rightOfTop(icons, 0);
			rowElements.add(name);

			// TODO: partial information concealment
			// print count
			TooltipMakerAPI count = row.createUIElement(COLUMN_WIDTH_TROOP_STR, height, false);
			int currCount = force.getCurrCount(type);
			int wantedCount = force.getWantedCount(type);
			int diffCount = wantedCount - currCount;

			String diffString = diffCount == 0 ? "" : "(" + diffCount + ")";
			String string = wantedCount + " " + diffString;
			count.addPara(string, 3, diffCount > 0 ? hp: hn, diffString);
			row.addUIElement(count).rightOfTop(name, 0);
			rowElements.add(count);

			// print strength
			TooltipMakerAPI strength = row.createUIElement(COLUMN_WIDTH_TROOP_STR, height, false);
			float currStrength = force.getCurrStrength(type);
			float wantedStrength = force.getWantedStrength(type);
			float diffStrength = wantedStrength - currStrength;

			diffString = diffStrength == 0 ? "" : "(" + String.format("%.1f", diffStrength) + ")";
			string = String.format("%.1f", wantedStrength) + " " + diffString;
			strength.addPara(string, 3, diffCount > 0 ? hp: hn, diffString);
			row.addUIElement(strength).rightOfTop(count, 0);
			rowElements.add(strength);

			// add/remove buttons (TODO)
			TooltipMakerAPI btnAddHolder = row.createUIElement(COLUMN_WIDTH_TROOP_BUTTON, 
					height, false);
			btnAddHolder.addButton("+", new Object(), COLUMN_WIDTH_TROOP_BUTTON, height-4, 0);
			row.addUIElement(btnAddHolder).rightOfTop(strength, 0);
			rowElements.add(btnAddHolder);

			TooltipMakerAPI btnRemoveHolder = row.createUIElement(COLUMN_WIDTH_TROOP_BUTTON, 
					height, false);
			btnRemoveHolder.addButton("-", new Object(), COLUMN_WIDTH_TROOP_BUTTON, height-4, 0);
			row.addUIElement(btnRemoveHolder).rightOfTop(btnAddHolder, 0);
			rowElements.add(btnRemoveHolder);

			troops.addCustom(row, 0);
		}
		*/
		panel.addUIElement(troops).rightOfTop(rightOf, 0);
		return troops;
	}

	public void renderPanel(CustomPanelAPI panel, TooltipMakerAPI tooltip, float width) {
		CustomPanelAPI row = panel.createCustomPanel(width, HEIGHT, null);

		// Industry image and text
		TooltipMakerAPI ttIndustry = row.createUIElement(COLUMN_WIDTH_INDUSTRY, HEIGHT, false);
		TooltipMakerAPI sub = ttIndustry.beginImageWithText(ind.getCurrentImage(), 95);
		String name = ind.getCurrentName();
		if (isIndustryTrueDisrupted(ind)) {
			name += "(" + StringHelper.getString("disrupted") + ")";
		}
		sub.addPara(name, 0, Misc.getHighlightColor(), ind.getCurrentName());
		ttIndustry.addImageWithText(0);

		row.addUIElement(ttIndustry).inLMid(0);

		// Controlling faction
		TooltipMakerAPI ttOwner = row.createUIElement(COLUMN_WIDTH_CONTROLLED_BY, HEIGHT, false);
		String owner = StringHelper.getString(heldByDefender ? "defender" : "attacker", true);
		// TODO: color-code based on relationship of attacker to player
		ttOwner.addPara(owner, !heldByDefender ? Misc.getPositiveHighlightColor() 
				: Misc.getNegativeHighlightColor(), HEIGHT/2 - 10);

		row.addUIElement(ttOwner).rightOfTop(ttIndustry, 0);

		// Troops
		TooltipMakerAPI atkPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, true, ttOwner);
		TooltipMakerAPI defPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, false, atkPanel);	

		tooltip.addCustom(row, 10);
	}
}
	
