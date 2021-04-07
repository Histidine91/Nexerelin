package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.isIndustryTrueDisrupted;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IndustryForBattle {
	
	public static final int HEIGHT = 100;
	public static final int COLUMN_WIDTH_INDUSTRY = 400;
	//public static final int COLUMN_WIDTH_CONTROLLED_BY = 80;

	public static final int COLUMN_WIDTH_TROOP_ICON = 64;
	public static final int COLUMN_WIDTH_TROOP_NAME = 96;
	public static final int COLUMN_WIDTH_TROOP_COUNT = 48;
	public static final int COLUMN_WIDTH_TROOP_STR = 56;
	public static final int COLUMN_WIDTH_TROOP_BUTTON = 24;
	public static final int COLUMN_WIDTH_TROOP_TOTAL = COLUMN_WIDTH_TROOP_ICON + COLUMN_WIDTH_TROOP_NAME
			+ COLUMN_WIDTH_TROOP_COUNT + COLUMN_WIDTH_TROOP_STR + 2 * COLUMN_WIDTH_TROOP_BUTTON + 16;
	public static final int NUM_ICONS_PER_UNIT = 3;	// 3 icons == 1 platoon/company/etc.

	public static final Map<GroundUnit.ForceType, Integer[]> ICON_COUNTS = new HashMap<>();
	
	protected GroundBattleIntel intel;
	protected Industry ind;
	protected IndustryForBattlePlugin plugin;
	protected List<GroundUnit> units = new LinkedList<>();
	public boolean heldByAttacker = false;

	public IndustryForBattle(GroundBattleIntel intel, Industry ind) {
		this.intel = intel;
		this.ind = ind;
		plugin = IndustryForBattlePlugin.loadPlugin(ind.getId(), this);
	}
	
	public IndustryForBattlePlugin getPlugin() {
		return plugin;
	}
	
	public Industry getIndustry() {
		return ind;
	}

	public boolean containsEnemyOf(boolean attacker) {
		for (GroundUnit unit : intel.getSide(!attacker).units) {
			if (unit.location != this) continue;
			return true;
		}
		return false;
	}
	
	public void addUnit(GroundUnit unit) {
		units.add(unit);
	}
	
	public void removeUnit(GroundUnit unit) {
		units.remove(unit);
	}
	
	
	public String getNameForIconTooltip(int count) {
		String displayNum = String.format("%.2f", (float)count/NUM_ICONS_PER_UNIT);
		String str = String.format(GroundBattleIntel.getString("unitEquivalent"), displayNum, intel.unitSize.getName());
		return str;
	}

	public TooltipMakerAPI renderForcePanel(CustomPanelAPI panel, float width, 
			boolean attacker, UIComponentAPI rightOf) 
	{
		float pad = 3;
		TooltipMakerAPI troops = panel.createUIElement(width, HEIGHT, false);
		MarketAPI market = ind.getMarket();
		Color hp = Misc.getPositiveHighlightColor();
		Color hn = Misc.getNegativeHighlightColor();
		
		Map<ForceType, Float> strengths = new HashMap<>();
		
		// TODO: list units on this location and total strength
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			
			NexUtils.modifyMapEntry(strengths, unit.type, unit.getNumUnitEquivalents());
		}
		
		troops.beginIconGroup();
		Iterator<ForceType> iter = strengths.keySet().iterator();
		while (iter.hasNext()) {
			final ForceType type = iter.next();
			float val = strengths.get(type);
			int count = Math.round(val * NUM_ICONS_PER_UNIT);
			if (count <= 0 && val > 0) count = 1;
			final int countFinal = count;
			
			CommodityOnMarketAPI com = intel.market.getCommodityData(type.commodityId);
			troops.addIcons(com, count, IconRenderMode.NORMAL);
			troops.addTooltipToPrevious(new TooltipCreator() {
				public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}
				public float getTooltipWidth(Object tooltipParam) {
					return 120;	// FIXME
				}
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					tooltip.addPara(getNameForIconTooltip(countFinal), 0f);
				}
			}, TooltipLocation.BELOW);
		}
		troops.addIconGroup(pad);
		
		float strength = 0;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			strength += unit.getBaseStrength();
		}
		String strengthNum = Math.round(strength) + "";
		troops.addPara("Strength: %s", pad, Misc.getHighlightColor(), strengthNum);
		
		panel.addUIElement(troops).rightOfTop(rightOf, 0);
		return troops;
	}

	public void renderPanel(CustomPanelAPI panel, TooltipMakerAPI tooltip, float width) {
		CustomPanelAPI row = panel.createCustomPanel(width, HEIGHT, null);

		// Industry image and text
		TooltipMakerAPI ttIndustry = row.createUIElement(COLUMN_WIDTH_INDUSTRY, HEIGHT, false);
		TooltipMakerAPI sub = ttIndustry.beginImageWithText(ind.getCurrentImage(), 95);
		String name = ind.getCurrentName();
		
		sub.addPara(name, 0, Misc.getHighlightColor(), ind.getCurrentName());
		
		if (isIndustryTrueDisrupted(ind)) {
			name = StringHelper.getString("disrupted", true);
			sub.addPara(name, Misc.getHighlightColor(), 0);
		}
		
		String owner = StringHelper.getString(heldByAttacker ? "attacker" : "defender", true);
		// TODO: color-code based on relationship of attacker to player
		sub.addPara("Held by: " + owner, 3, heldByAttacker ? Misc.getPositiveHighlightColor() 
				: Misc.getNegativeHighlightColor(), owner);
		
		ttIndustry.addImageWithText(0);

		row.addUIElement(ttIndustry).inLMid(0);

		// Troops
		TooltipMakerAPI atkPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, true, ttIndustry);
		TooltipMakerAPI defPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, false, atkPanel);	

		tooltip.addCustom(row, 10);
	}
}
	
