package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin;
import exerelin.campaign.intel.groundbattle.plugins.MarketMapDrawer;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

public class IndustryForBattle {
	
	public static final int COLUMN_WIDTH_INDUSTRY = 400;
	//public static final int COLUMN_WIDTH_CONTROLLED_BY = 80;

	public static final int COLUMN_WIDTH_TROOP_ICON = 64;
	public static final int COLUMN_WIDTH_TROOP_NAME = 96;
	public static final int COLUMN_WIDTH_TROOP_COUNT = 48;
	public static final int COLUMN_WIDTH_TROOP_STR = 56;
	public static final int COLUMN_WIDTH_TROOP_BUTTON = 24;
	public static final int COLUMN_WIDTH_TROOP_TOTAL = COLUMN_WIDTH_TROOP_ICON + COLUMN_WIDTH_TROOP_NAME
			+ COLUMN_WIDTH_TROOP_COUNT + COLUMN_WIDTH_TROOP_STR + 2 * COLUMN_WIDTH_TROOP_BUTTON + 16;
	public static final int DEFAULT_TOOLTIP_WIDTH = 240;
	public static final int NUM_ICONS_PER_UNIT = 3;	// 3 icons == 1 platoon/company/etc.

	public static final Map<GroundUnit.ForceType, Integer[]> ICON_COUNTS = new HashMap<>();
	
	protected GroundBattleIntel intel;
	protected Industry ind;
	protected IndustryForBattlePlugin plugin;
	protected List<GroundUnit> units = new LinkedList<>();
	public boolean heldByAttacker = false;
	@Getter	@Setter	protected boolean looted = false;
		
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
	
	public boolean hasLootables() {
		return ind.getAICoreId() != null || ind.getSpecialItem() != null;
	}
	
	public String getNoLootReason() {
		// check already done before showing the button
		//if (intel.playerIsAttacker == null) return "[temp] Not in battle";

		if (looted) return GroundBattleIntel.getString("noLootReasonAlreadyLooted");
		
		if (heldByAttacker != intel.playerIsAttacker) return GroundBattleIntel.getString("noLootReasonNotHeld");
		
		// player is helping defender: can only loot if this is not our planet
		boolean playerOwned = intel.market.isPlayerOwned();
		if (!intel.playerIsAttacker && playerOwned) {
			return GroundBattleIntel.getString("noLootReasonOwnMarket");
		}
		
		if (!intel.isFleetInRange(Global.getSector().getPlayerFleet()))
			return GroundBattleIntel.getString("ability_outOfRange");
		
		// can loot if we started this battle
		if (intel.playerInitiated) return null;
		
		// else, our units must account for at least half of total strength on this industry
		float ourStr = 0, totalStr = 0;
		for (GroundUnit unit : units) {
			float str = unit.getAttackStrength();
			if (unit.isPlayer || unit.getFaction().isPlayerFaction())
				ourStr += str;
			
			totalStr += str;
		}
		if (ourStr <= 0 || ourStr < totalStr * 0.5f)
			return GroundBattleIntel.getString("noLootReasonStrength");
		
		
		return null;
	}
	
	public String getIconTooltipPartial(GroundUnitDef def, float value) {
		String displayNum = String.format("%.1f", value);
		String str = GroundBattleIntel.getString("unitEquivalent");
		str = StringHelper.substituteToken(str, "$num", displayNum);
		str = StringHelper.substituteToken(str, "$type", def.name);
		str = StringHelper.substituteToken(str, "$unit", intel.unitSize.getNamePlural());
		return str;
	}
	
	/**
	 *
	 * @param attacker
	 * @return 0 = no info, 1 = color, 2 = number
	 */
	public int getMoraleDetailLevel(boolean attacker) {
		if (Global.getSettings().isDevMode()) return 5;
		if (intel.playerIsAttacker != null && intel.playerIsAttacker == attacker) return 5;
		
		// agent intel
		int agentLevel = 0;
		for (AgentIntel agent : CovertOpsManager.getManager().getAgents()) {
			if (agent.getMarket() == intel.getMarket())
				agentLevel += agent.getLevel();
		}
		if (agentLevel >= 5) return 2;
		else if (agentLevel >= 3) return 1; 
		
		return 0;
	}
	
	protected boolean shouldShowDetailedDisplay(boolean attacker) {
		if (Global.getSettings().isDevMode()) return true;
		if (intel.playerIsAttacker == null || intel.playerIsAttacker != attacker)
			return false;
		
		// check if any units are player units
		for (GroundUnit unit : units) {
			if (unit.isAttacker == attacker && unit.isPlayer)
				return true;
		}
		return false;
	}
	
	protected boolean stillExistsOnMarket() {
		return ind.getMarket() != null && ind.getMarket().hasIndustry(ind.getId());
	}
	
	// old tabular display, should not be used
	@Deprecated
	public TooltipMakerAPI renderForcePanel(CustomPanelAPI panel, float width, 
			boolean attacker, UIComponentAPI rightOf) 
	{
		int height = Math.round(MarketMapDrawer.getIndustryImageWidth()/2);
		float pad = 3;
		TooltipMakerAPI troops = panel.createUIElement(width, height, false);
		final Color hl = Misc.getHighlightColor();
		
		final Map<GroundUnitDef, Float> strengths = new HashMap<>();
		
		// display units present here
		boolean any = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			any = true;
			NexUtils.modifyMapEntry(strengths, unit.unitDef, unit.getNumUnitEquivalents());
		}
		if (!any) {	// nothing to display, quit now
			panel.addUIElement(troops).rightOfTop(rightOf, 0);
			return troops;
		}
		
		troops.beginIconGroup();
		List<GroundUnitDef> keys = new ArrayList<>(strengths.keySet());
		Collections.sort(keys);
		for (GroundUnitDef def : keys) {
			float val = strengths.get(def);
			int count = Math.round(val * NUM_ICONS_PER_UNIT);
			if (count <= 0 && val > 0) count = 1;
			
			CommodityOnMarketAPI com = intel.market.getCommodityData(def.getCommodityIdForIcon());
			troops.addIcons(com, count, IconRenderMode.NORMAL);
		}
		troops.addIconGroup(40, pad);
		
		boolean detailed = shouldShowDetailedDisplay(attacker);
		troops.addTooltipToPrevious(generateForceTooltip(attacker, detailed ? 360 : 160), TooltipLocation.BELOW);
		
		// strength
		float strength = getStrength(attacker);
		String strengthNum = Math.round(strength) + "";
		troops.addPara(GroundBattleIntel.getString("intelDesc_strength"), pad, hl, strengthNum);
		
		// morale
		int moraleDetail = getMoraleDetailLevel(attacker);
		if (moraleDetail > 0) {
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
				String str = StringHelper.toPercent(avgMorale);
				if (moraleDetail == 1)
					str = "?";
				troops.addPara(GroundBattleIntel.getString("intelDesc_moraleAvg"), pad, 
					h, str);
			}
		}		
		
		if (intel.playerIsAttacker != null && hasLootables() && stillExistsOnMarket()) {
			ButtonAPI button = troops.addButton(GroundBattleIntel.getString("btnLoot"), 
					new Pair<String, IndustryForBattle> ("loot", this),
					64, 16, pad);
			String noLootReason = getNoLootReason();
			if (noLootReason != null) {
				button.setEnabled(false);
			}
			// tooltip
			troops.addTooltipToPrevious(generateLootTooltip(noLootReason), TooltipLocation.LEFT);
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
	@Deprecated
	public void renderPanel(CustomPanelAPI panel, TooltipMakerAPI tooltip, float width) {
		int height = Math.round(MarketMapDrawer.getIndustryImageWidth()/2);
		CustomPanelAPI row = panel.createCustomPanel(width, height, null);
		float pad = 3;

		// Industry image and text
		TooltipMakerAPI ttIndustry = row.createUIElement(COLUMN_WIDTH_INDUSTRY, height, false);
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
		TooltipMakerAPI strTT = stats.createUIElement(width*0.55f, height, false);
		if (largeText) strTT.setParaSmallInsignia();
		//else strTT.setParaSmallInsignia();
		strTT.addPara(Math.round(getStrength(attacker)) + "", textPad).setAlignment(Alignment.RMID);
		String ttStr = StringHelper.substituteToken(GroundBattleIntel.getString(
				"industryPanel_tooltipStrength"), "$side", side);
		TooltipCreator tt = NexUtilsGUI.createSimpleTextTooltip(ttStr, 320);
		strTT.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
		stats.addUIElement(strTT).inTL(2, 0);
		
		// morale
		float avgMorale = totalMorale/totalUnits;
		Color h = GroundUnit.getMoraleColor(avgMorale);

		TooltipMakerAPI moraleTT = stats.createUIElement(width*0.45f, height, false);
		if (largeText) strTT.setParaSmallInsignia();
		//else moraleTT.setParaSmallInsignia();
		int moraleDetail = getMoraleDetailLevel(attacker);
		if (moraleDetail > 0) {
			String moraleStr = StringHelper.toPercent(avgMorale);
			String append = "%";
			if (moraleDetail == 1) {
				moraleStr = " ? ";
				append = "";
			}
			
			moraleTT.addPara(moraleStr + append, textPad, h, moraleStr).setAlignment(Alignment.RMID);
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
		
		final Map<GroundUnitDef, Float> strengths = new HashMap<>();
		
		// attacker/defender icon
		TooltipMakerAPI iconHolder = troops.createUIElement(height, height, false);
		iconHolder.addImage(intel.getSide(attacker).getFaction().getCrest(), height, 2);
		troops.addUIElement(iconHolder).inTL(0, 0);
		
		// units icon group
		boolean any = false;
		for (GroundUnit unit : units) {
			if (unit.isAttacker != attacker) continue;
			any = true;
			NexUtils.modifyMapEntry(strengths, unit.unitDef, unit.getNumUnitEquivalents());
		}
		if (!any) {
			return troops;
		}
		
		TooltipMakerAPI iconGroupHolder = troops.createUIElement(width - height, height, false);
		iconGroupHolder.beginIconGroup();
		
		List<GroundUnitDef> keys = new ArrayList<>(strengths.keySet());
		Collections.sort(keys);
		for (GroundUnitDef def : keys) {
			float val = strengths.get(def);
			int count = Math.round(val * NUM_ICONS_PER_UNIT);
			if (count <= 0 && val > 0) count = 1;
			
			CommodityOnMarketAPI com = intel.market.getCommodityData(def.getCommodityIdForIcon());
			iconGroupHolder.addIcons(com, count, IconRenderMode.NORMAL);
		}
		iconGroupHolder.addIconGroup(height, 0);
		
		boolean detailed = Global.getSettings().isDevMode() || (intel.playerIsAttacker != null && intel.playerIsAttacker == attacker);
		iconGroupHolder.addTooltipToPrevious(generateForceTooltip(attacker, detailed ? 360 : 160), TooltipLocation.BELOW);
		troops.addUIElement(iconGroupHolder).rightOfTop(iconHolder, 4);
						
		return troops;
	}

	public CustomPanelAPI renderPanelNew(CustomPanelAPI panel, float width, float height, CustomUIPanelPlugin pp) {
		return renderPanelNew(panel, "map", width, height, pp);
	}

	/**
	 * Creates a panel for the industry on the ground battle map.
	 * @param panel The external panel holding this one.
	 * @param width
	 * @param height
	 * @param pp
	 * @return
	 */
	public CustomPanelAPI renderPanelNew(CustomPanelAPI panel, String mode, float width, float height,
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
					return DEFAULT_TOOLTIP_WIDTH;
				}
				public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object tooltipParam) 
				{
					String str = ind.getCurrentName();
					float pad = 3;
					tt.addPara(str, 0, Misc.getHighlightColor(), ind.getCurrentName());
					
					boolean trueDisrupt = isIndustryTrueDisrupted();
					
					if (trueDisrupt) {
						str = GroundBattleIntel.getString("industryPanel_header_disrupt");
						tt.addPara(str, pad, Misc.getNegativeHighlightColor(), "" + Math.round(getIndustry().getDisruptedDays()));
					}
					float strMult = plugin.getStrengthMult();
					if (strMult != 1) {
						str = StringHelper.getString("nex_invasion2", "industryPanel_header_defBonus") + ": %s";
						tt.addPara(str, pad, strMult > 1 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), 
								String.format("%.2f×", strMult));
					}

					String owner = StringHelper.getString(heldByAttacker ? "attacker" : "defender", true);
					str = StringHelper.getString("nex_invasion2", "industryPanel_header_heldBy");
					tt.addPara(str + ": " + owner, pad, intel.getHighlightColorForSide(heldByAttacker), owner);
					
					if (true || !trueDisrupt) {
						if (getPlugin().getDef().hasTag("noBombard")) {
							str = StringHelper.getString("nex_invasion2", "industryPanel_header_bombardmentImmune");
							tt.addPara(str, pad);
						}
						else if (getPlugin().getDef().hasTag("resistBombard")) {
							str = StringHelper.getString("nex_invasion2", "industryPanel_header_bombardmentResistant");
							tt.addPara(str, pad);
						}
					}
					
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
		
		if ("map".equals(mode) && intel.playerIsAttacker != null && hasLootables() && stillExistsOnMarket())
		{
			TooltipMakerAPI lootBtnHolder = box.createUIElement(GroundUnit.BUTTON_SECTION_WIDTH-6, 16, false);
			ButtonAPI button = lootBtnHolder.addButton(GroundBattleIntel.getString("btnLoot"), 
					new Pair<String, IndustryForBattle> ("loot", this),
					GroundUnit.BUTTON_SECTION_WIDTH-6, 16, 0);
			box.addUIElement(lootBtnHolder).inBR(5, 2);
			
			String noLootReason = getNoLootReason();
			if (noLootReason != null) {
				button.setEnabled(false);
			}
			// tooltip
			lootBtnHolder.addTooltipToPrevious(generateLootTooltip(noLootReason), TooltipLocation.LEFT);
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

				Map<GroundUnitDef, Float> strengths = new HashMap<>();
				
				boolean devmode = Global.getSettings().isDevMode();
				
				// calc strengths of non-player units
				if (!devmode) {
					for (GroundUnit unit : units) {
						if (unit.isPlayer) continue;
						if (unit.isAttacker != isAttacker) continue;
						NexUtils.modifyMapEntry(strengths, unit.unitDef, unit.getNumUnitEquivalents());
					}	
				}						
				
				// player units
				for (GroundUnit unit : units) {
					if (!devmode && !unit.isPlayer) continue;
					if (unit.isAttacker != isAttacker) continue;
					String str = unit.toString() + ": " + GroundBattleIntel.getString("industryPanel_tooltipUnitInfo");
					String atk = (int)unit.getAttackStrength() + "";
					String mor = StringHelper.toPercent(unit.getMorale());
					LabelAPI label = tooltip.addPara(str, 0, Color.white, atk, mor);
					label.setHighlight(atk, mor);
					label.setHighlightColors(Misc.getHighlightColor(), GroundUnit.getMoraleColor(unit.morale));
				}
				
				// non-player units
				if (!devmode) {
					List<GroundUnitDef> keys = new ArrayList<>(strengths.keySet());
					Collections.sort(keys);
					for (GroundUnitDef def : keys) {
						float val = strengths.get(def);
						String tooltipStr = getIconTooltipPartial(def, val);
						String displayNum = String.format("%.1f", val);
						tooltip.addPara(tooltipStr, 0f, Misc.getHighlightColor(), displayNum);
					}
				}
			}
		};
	}
	
	public static TooltipCreator generateLootTooltip(final String noLootReason) {
		final float width = 320;
		
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
				tooltip.addPara(GroundBattleIntel.getString("btnLootTooltip"), 0);
				
				if (noLootReason != null) {
					tooltip.addPara(noLootReason, Misc.getNegativeHighlightColor(), 10);
				}
			}
		};
	}
}
	
