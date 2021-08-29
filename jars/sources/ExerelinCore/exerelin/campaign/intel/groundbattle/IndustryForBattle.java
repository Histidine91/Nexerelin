package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin;
import exerelin.campaign.intel.groundbattle.plugins.MarketMapDrawer;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.lwjgl.util.vector.Vector2f;

public class IndustryForBattle {
	
	public static final int HEIGHT = 95;
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
		
	protected Vector2f posOnMap;
	protected transient Vector2f posOnMapGraphical;
	protected transient Rectangle rectangle;

	public IndustryForBattle(GroundBattleIntel intel, Industry ind) {
		this.intel = intel;
		this.ind = ind;
		plugin = IndustryForBattlePlugin.loadPlugin(ind.getId(), this);
	}
	
	protected Object readResolve() {
		if (posOnMap != null) {
			setPosOnMapGraphical();
		}
		return this;
	}
	
	public IndustryForBattlePlugin getPlugin() {
		return plugin;
	}
	
	public Industry getIndustry() {
		return ind;
	}
	
	public String getName() {
		return ind.getCurrentName();
	}
	
	public GroundBattleIntel getIntel() {
		return intel;
	}
		
	public GroundBattleSide getHoldingSide() {
		return intel.getSide(heldByAttacker);
	}
	
	public GroundBattleSide getNonHoldingSide() {
		return intel.getSide(!heldByAttacker);
	}
	
	public List<GroundUnit> getUnits() {
		return units;
	}

	public boolean containsEnemyOf(boolean attacker) {
		for (GroundUnit unit : intel.getSide(!attacker).units) {
			if (unit.location != this) continue;
			return true;
		}
		return false;
	}
	
	public boolean isContested() {
		boolean haveAttacker = false;
		boolean haveDefender = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker) haveAttacker = true;
			else haveDefender = true;
		}
		return haveAttacker && haveDefender;
	}
	
	public float getPriority() {
		return GroundBattleSide.getDefendPriority(ind);
	}
	
	public Vector2f getPosOnMap() {
		return posOnMap;
	}
	
	public Vector2f getGraphicalPosOnMap() {
		return posOnMapGraphical;
	}
	
	public Rectangle getRectangle() {
		if (rectangle == null) 
			rectangle = new Rectangle((int)posOnMap.x, (int)posOnMap.y, 
					(int)MarketMapDrawer.getIndustryPanelWidth(), (int)MarketMapDrawer.getIndustryPanelHeight());
		return rectangle;
	}
	
	public void setPosOnMap(Vector2f pos) {
		posOnMap = pos;
		rectangle = null;
		setPosOnMapGraphical();
	}
	
	public void setPosOnMapGraphical() {
		if (posOnMap == null) return;
		posOnMapGraphical = new Vector2f(posOnMap);
		posOnMapGraphical.x += MarketMapDrawer.getIndustryPanelWidth()/2;
		posOnMapGraphical.y += MarketMapDrawer.getIndustryPanelHeight()/2;
	}
	
	/**
	 * Check whether this industry should change hands.
	 * @param hadCombatThisTurn
	 * @return The new owner (may be same as previous owner).
	 */
	public boolean updateOwner(boolean hadCombatThisTurn) {
		boolean nowHeldByAttacker = this.heldByAttacker;
		
		boolean haveAttacker = false;
		boolean haveDefender = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker) haveAttacker = true;
			else haveDefender = true;
		}
		if (haveAttacker && !haveDefender) {
			nowHeldByAttacker = true;
		} else if (!haveAttacker) {
			nowHeldByAttacker = false;
		}
		
		if (heldByAttacker != nowHeldByAttacker) {
			plugin.unapply();
			heldByAttacker = nowHeldByAttacker;
			plugin.apply();
			
			if (hadCombatThisTurn) {
				for (GroundUnit unit : intel.getAllUnits()) {
					float morale = GBConstants.CAPTURE_MORALE;
					if (unit.isAttacker() != nowHeldByAttacker) morale *= -1;	// enemy lost this location
					if (unit.getLocation() == this) morale *= 3f;
					unit.modifyMorale(morale);
				}
			}
			GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_INDUSTRY_CAPTURED, intel.turnNum);
			lg.params.put("industry", this);
			lg.params.put("heldByAttacker", heldByAttacker);
			if (hadCombatThisTurn)
				lg.params.put("morale", GBConstants.CAPTURE_MORALE);
			intel.addLogEvent(lg);		
		}
		return heldByAttacker;
	}
	
	public void addUnit(GroundUnit unit) {
		units.add(unit);
	}
	
	public void removeUnit(GroundUnit unit) {
		units.remove(unit);
	}
	
	public float getStrength(boolean attacker) {
		float strength = 0;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			strength += unit.getAttackStrength();
		}
		return strength;
	}
	
	public boolean isIndustryTrueDisrupted() {
		return ind.getDisruptedDays() > GBConstants.DISRUPT_WHEN_CAPTURED_TIME;
	}
	
	public String getIconTooltipPartial(ForceType type, float value) {
		String displayNum = String.format("%.1f", value);
		String str = GroundBattleIntel.getString("unitEquivalent");
		str = StringHelper.substituteToken(str, "$num", displayNum);
		str = StringHelper.substituteToken(str, "$type", type.getName());
		str = StringHelper.substituteToken(str, "$unit", intel.unitSize.getNamePlural());
		return str;
	}
	
	public boolean isMoraleKnown(boolean attacker) {
		return Global.getSettings().isDevMode() || intel.playerIsAttacker == attacker;	// TODO: agent intel
	}

	public TooltipMakerAPI renderForcePanel(CustomPanelAPI panel, float width, 
			boolean attacker, UIComponentAPI rightOf) 
	{
		float pad = 3;
		TooltipMakerAPI troops = panel.createUIElement(width, HEIGHT, false);
		MarketAPI market = ind.getMarket();
		final Color hl = Misc.getHighlightColor();
		Color hp = Misc.getPositiveHighlightColor();
		Color hn = Misc.getNegativeHighlightColor();
		
		final Map<ForceType, Float> strengths = new HashMap<>();
		
		// display units present here
		boolean any = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			any = true;
			NexUtils.modifyMapEntry(strengths, unit.type, unit.getNumUnitEquivalents());
		}
		if (!any) {	// nothing to display, quit now
			panel.addUIElement(troops).rightOfTop(rightOf, 0);
			return troops;
		}
		
		troops.beginIconGroup();
		List<ForceType> keys = new ArrayList<>(strengths.keySet());
		Collections.sort(keys);
		for (ForceType type : keys) {
			float val = strengths.get(type);
			int count = Math.round(val * NUM_ICONS_PER_UNIT);
			if (count <= 0 && val > 0) count = 1;
			
			CommodityOnMarketAPI com = intel.market.getCommodityData(type.commodityId);
			troops.addIcons(com, count, IconRenderMode.NORMAL);
		}
		troops.addIconGroup(40, pad);
		
		boolean detailed = intel.playerIsAttacker != null && intel.playerIsAttacker == attacker;
		troops.addTooltipToPrevious(generateForceTooltip(attacker, detailed ? 360 : 160), TooltipLocation.BELOW);
		
		// strength
		float strength = getStrength(attacker);
		String strengthNum = Math.round(strength) + "";
		troops.addPara(GroundBattleIntel.getString("intelDesc_strength"), pad, hl, strengthNum);
		
		// morale
		if (isMoraleKnown(attacker)) {
			float totalUnits = 0;	// denominator
			float totalMorale = 0;	// numerator
			for (GroundUnit unit : units) {
				if (unit.isAttacker != attacker) continue;
				float unitSize = unit.getNumUnitEquivalents();
				totalUnits += unitSize;
				totalMorale += unit.morale * unitSize;
			}
			if (totalUnits > 0) {
				float avgMorale = totalMorale/totalUnits;
				Color h = GroundUnit.getMoraleColor(avgMorale);
				troops.addPara(GroundBattleIntel.getString("intelDesc_moraleAvg"), pad, 
					h, StringHelper.toPercent(avgMorale));
			}
		}		
		
		if (heldByAttacker && intel.playerIsAttacker) {
			boolean haveLootables = ind.getAICoreId() != null || ind.getSpecialItem() != null;
			if (haveLootables) {
				troops.addButton(GroundBattleIntel.getString("btnLoot"), 
						new Pair<String, IndustryForBattle> ("loot", this),
						64, 16, pad);
			}
		}	
		
		panel.addUIElement(troops).rightOfTop(rightOf, 0);
		return troops;
	}

	/**
	 * Generates a panel displaying the industry and the forces on it, for the old tabular display.
	 * @param panel The external panel holding this one.
	 * @param tooltip
	 * @param width
	 */
	public void renderPanel(CustomPanelAPI panel, TooltipMakerAPI tooltip, float width) {
		CustomPanelAPI row = panel.createCustomPanel(width, HEIGHT, null);
		float pad = 3;

		// Industry image and text
		TooltipMakerAPI ttIndustry = row.createUIElement(COLUMN_WIDTH_INDUSTRY, HEIGHT, false);
		TooltipMakerAPI sub = ttIndustry.beginImageWithText(ind.getCurrentImage(), 95);
		String str = ind.getCurrentName();
		
		sub.addPara(str, 0, Misc.getHighlightColor(), ind.getCurrentName());
		
		if (isIndustryTrueDisrupted()) {
			str = StringHelper.getString("disrupted", true);
			sub.addPara(str, Misc.getHighlightColor(), pad);
		}
		float strMult = plugin.getStrengthMult();
		if (strMult != 1) {
			str = StringHelper.getString("nex_invasion2", "industryPanel_header_defBonus") + ": %s";
			sub.addPara(str, pad, strMult > 1 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 
					String.format("%.2f×", strMult));
		}
		
		String owner = StringHelper.getString(heldByAttacker ? "attacker" : "defender", true);
		str = StringHelper.getString("nex_invasion2", "industryPanel_header_heldBy");
		sub.addPara(str + ": " + owner, pad, heldByAttacker ? Misc.getPositiveHighlightColor() 
				: Misc.getNegativeHighlightColor(), owner);
		
		ttIndustry.addImageWithText(0);	

		row.addUIElement(ttIndustry).inLMid(0);

		// Troops
		TooltipMakerAPI atkPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, true, ttIndustry);
		TooltipMakerAPI defPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, false, atkPanel);	

		tooltip.addCustom(row, 0);
	}
	
	/**
	 * Generates a panel with the strength and (if known) morale numbers.
	 * @param indPanel The {@code IndustryForBattle} panel holding this one.
	 * @param width
	 * @param height
	 * @param attacker
	 * @return
	 */
	public CustomPanelAPI renderStrPanel(CustomPanelAPI indPanel, float width, float height, 
			boolean attacker) 
	{
		boolean largeText = MarketMapDrawer.getIndustryPanelSizeMult() >= 0.8f;
		float textPad = largeText ? 6 : 4;
		
		CustomUIPanelPlugin panelPlugin = new FramedCustomPanelPlugin(0.5f, intel.getSide(attacker).getFaction().getBaseUIColor(), true);
		CustomPanelAPI stats = indPanel.createCustomPanel(width, height, panelPlugin);
		final Color hl = Misc.getHighlightColor();
		Color hp = Misc.getPositiveHighlightColor();
		Color hn = Misc.getNegativeHighlightColor();
		
		float totalUnits = 0;	// denominator
		float totalMorale = 0;	// numerator
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			float unitSize = unit.getNumUnitEquivalents();
			totalUnits += unitSize;
			totalMorale += unit.morale * unitSize;
		}
				
		if (totalUnits <= 0) return stats;
		
		String side = StringHelper.getString(attacker ? "attacker" : "defender");
		
		// strength
		TooltipMakerAPI strTT = stats.createUIElement(width/2, height, false);
		if (largeText) strTT.setParaInsigniaLarge();
		else strTT.setParaSmallInsignia();
		strTT.addPara(Math.round(getStrength(attacker)) + "", textPad).setAlignment(Alignment.RMID);
		String ttStr = StringHelper.substituteToken(GroundBattleIntel.getString(
				"industryPanel_tooltipStrength"), "$side", side);
		TooltipCreator tt = NexUtilsGUI.createSimpleTextTooltip(ttStr, 320);
		strTT.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
		stats.addUIElement(strTT).inTL(2, 0);
		
		// morale
		float avgMorale = totalMorale/totalUnits;
		Color h = GroundUnit.getMoraleColor(avgMorale);

		TooltipMakerAPI moraleTT = stats.createUIElement(width/2, height, false);
		if (largeText) moraleTT.setParaInsigniaLarge();
		else moraleTT.setParaSmallInsignia();
		if (isMoraleKnown(attacker)) {
			String moraleStr = StringHelper.toPercent(avgMorale);
			moraleTT.addPara(moraleStr + "%", textPad, h, moraleStr).setAlignment(Alignment.RMID);
			ttStr = StringHelper.substituteToken(GroundBattleIntel.getString(
				"industryPanel_tooltipMorale"), "$side", side);
			tt = NexUtilsGUI.createSimpleTextTooltip(ttStr, 320);
			moraleTT.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
		}
		else {
			moraleTT.addPara("   ", 0);
		}			
		stats.addUIElement(moraleTT).rightOfTop(strTT, 2);
		
		return stats;
	}
	
	/**
	 * Generates a panel with the troop icons in a row.
	 * @param indPanel The {@code IndustryForBattle} panel holding this one.
	 * @param width
	 * @param height
	 * @param attacker
	 * @return
	 */
	public CustomPanelAPI renderTroopPanelNew(CustomPanelAPI indPanel, float width, float height, boolean attacker) 
	{		
		CustomPanelAPI troops = indPanel.createCustomPanel(width, height, null);
		
		final Map<ForceType, Float> strengths = new HashMap<>();
		
		// attacker/defender icon
		TooltipMakerAPI iconHolder = troops.createUIElement(height, height, false);
		iconHolder.addImage(intel.getSide(attacker).getFaction().getCrest(), height, 2);
		troops.addUIElement(iconHolder).inTL(0, 0);
		
		// units icon group
		boolean any = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			any = true;
			NexUtils.modifyMapEntry(strengths, unit.type, unit.getNumUnitEquivalents());
		}
		if (!any) {
			return troops;
		}
		
		TooltipMakerAPI iconGroupHolder = troops.createUIElement(width - height, height, false);
		iconGroupHolder.beginIconGroup();
		
		List<ForceType> keys = new ArrayList<>(strengths.keySet());
		Collections.sort(keys);
		for (ForceType type : keys) {
			float val = strengths.get(type);
			int count = Math.round(val * NUM_ICONS_PER_UNIT);
			if (count <= 0 && val > 0) count = 1;
			
			CommodityOnMarketAPI com = intel.market.getCommodityData(type.commodityId);
			iconGroupHolder.addIcons(com, count, IconRenderMode.NORMAL);
		}
		iconGroupHolder.addIconGroup(height, 0);
		
		boolean detailed = intel.playerIsAttacker != null && intel.playerIsAttacker == attacker;
		iconGroupHolder.addTooltipToPrevious(generateForceTooltip(attacker, detailed ? 360 : 160), TooltipLocation.BELOW);
		troops.addUIElement(iconGroupHolder).rightOfTop(iconHolder, 4);
						
		return troops;
	}
	
	/**
	 * Creates a panel for the industry on the ground battle map.
	 * @param panel The external panel holding this one.
	 * @param width
	 * @param height
	 * @param x
	 * @param y
	 * @param pp
	 * @return
	 */
	public CustomPanelAPI renderPanelNew(CustomPanelAPI panel, float width, float height, float x, float y, 
			CustomUIPanelPlugin pp) 
	{		
		float imageWidth = MarketMapDrawer.getIndustryImageWidth();
		float subHeight = (int)(height / 4 - 2);
		CustomPanelAPI box = panel.createCustomPanel(width, height, pp);

		// Industry image and text
		TooltipMakerAPI ttImage = box.createUIElement(imageWidth, height/2, false);
		ttImage.addImage(ind.getCurrentImage(), imageWidth, 0);
		ttImage.addTooltipToPrevious(new TooltipCreator() {
				public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}
				public float getTooltipWidth(Object tooltipParam) {
					return 240;	// FIXME magic number
				}
				public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object tooltipParam) 
				{
					String str = ind.getCurrentName();
					float pad = 3;
					tt.addPara(str, 0, Misc.getHighlightColor(), ind.getCurrentName());
		
					if (isIndustryTrueDisrupted()) {
						str = StringHelper.getString("disrupted", true);
						tt.addPara(str, Misc.getHighlightColor(), pad);
					}
					float strMult = plugin.getStrengthMult();
					if (strMult != 1) {
						str = StringHelper.getString("nex_invasion2", "industryPanel_header_defBonus") + ": %s";
						tt.addPara(str, pad, strMult > 1 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 
								String.format("%.2f×", strMult));
					}

					String owner = StringHelper.getString(heldByAttacker ? "attacker" : "defender", true);
					str = StringHelper.getString("nex_invasion2", "industryPanel_header_heldBy");
					tt.addPara(str + ": " + owner, pad, heldByAttacker ? Misc.getPositiveHighlightColor() 
							: Misc.getNegativeHighlightColor(), owner);
				}
			}, TooltipLocation.BELOW);

		box.addUIElement(ttImage).inTL(-4, 2);
		
		// Strength meters
		float strWidth = MarketMapDrawer.getIndustryPanelWidth() - MarketMapDrawer.getIndustryImageWidth() - 4;
		CustomPanelAPI atkStrPanel = renderStrPanel(box, strWidth, height/4, true);
		box.addComponent(atkStrPanel).rightOfTop(ttImage, 6);
		CustomPanelAPI defStrPanel = renderStrPanel(box, strWidth, height/4, false);
		box.addComponent(defStrPanel).belowLeft(atkStrPanel, 0);
		
		// Troops
		CustomPanelAPI atkPanel = renderTroopPanelNew(box, width, subHeight, true);
		box.addComponent(atkPanel).belowLeft(ttImage, 0);
		CustomPanelAPI defPanel = renderTroopPanelNew(box, width, subHeight, false);
		box.addComponent(defPanel).belowLeft(atkPanel, 1);

		panel.addComponent(box).inTL(x, y);
		
		if (heldByAttacker && intel.playerIsAttacker) {
			boolean haveLootables = ind.getAICoreId() != null || ind.getSpecialItem() != null;
			if (haveLootables) {
				TooltipMakerAPI lootBtnHolder = box.createUIElement(GroundUnit.BUTTON_SECTION_WIDTH-6, 16, false);
				lootBtnHolder.addButton(GroundBattleIntel.getString("btnLoot"), 
						new Pair<String, IndustryForBattle> ("loot", this),
						GroundUnit.BUTTON_SECTION_WIDTH-6, 16, 0);
				box.addUIElement(lootBtnHolder).inBR(5, 2);
			}
		}	
		
		return box;
	}
	
	public TooltipCreator generateForceTooltip(final boolean isAttacker, final float width) {
		return new TooltipCreator() {
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}
			public float getTooltipWidth(Object tooltipParam) {
				return width;
			}
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				
				Map<ForceType, Float> strengths = new HashMap<>();
				
				// calc strengths of non-player units
				for (GroundUnit unit : units) {
					if (unit.isPlayer) continue;
					if (unit.isAttacker != isAttacker) continue;
					NexUtils.modifyMapEntry(strengths, unit.type, unit.getNumUnitEquivalents());
				}			
				
				// player units
				for (GroundUnit unit : units) {
					if (!unit.isPlayer) continue;
					if (unit.isAttacker != isAttacker) continue;
					String str = unit.toString() + ": " + GroundBattleIntel.getString("industryPanel_tooltipUnitInfo");
					String atk = (int)unit.getAttackStrength() + "";
					String mor = StringHelper.toPercent(unit.getMorale());
					LabelAPI label = tooltip.addPara(str, 0, Color.white, atk, mor);
					label.setHighlight(atk, mor);
					label.setHighlightColors(Misc.getHighlightColor(), GroundUnit.getMoraleColor(unit.morale));
				}
				
				// non-player units
				List<ForceType> keys = new ArrayList<>(strengths.keySet());
				Collections.sort(keys);
				for (ForceType type : keys) {
					float val = strengths.get(type);
					String tooltipStr = getIconTooltipPartial(type, val);
					String displayNum = String.format("%.1f", val);
					tooltip.addPara(tooltipStr, 0f, Misc.getHighlightColor(), displayNum);
				}
			}
		};
	}
}
	
