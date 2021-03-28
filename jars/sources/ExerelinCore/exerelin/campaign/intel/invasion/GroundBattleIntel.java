package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

// may not actually use this in the end and just go with the "disrupt everything" system
@Deprecated
public class GroundBattleIntel extends BaseIntelPlugin {
	
	public static final float MAX_SUPPORT_DIST = 250;
	public static final float DISRUPT_WHEN_CAPTURED_TIME = 0.25f;	// days
	
	protected int turnNum;
	
	protected MarketAPI market;
	protected FactionAPI attacker;
	protected FactionAPI defender;
	protected boolean playerInvolved;
	
	protected MutableStat attackerStrength = new MutableStat(1);
	protected MutableStat defenderStrength = new MutableStat(1);
	
	protected MutableStat liftCapacityAtk = new MutableStat(1);
	protected MutableStat liftCapacityDef = new MutableStat(1);
	protected float liftCapacityAtkRemaining;
	protected float liftCapacityDefRemaining;
	
	public List<IndustryForInvasion> industries = new ArrayList<>();
	
	public GroundBattleIntel(MarketAPI market, FactionAPI attacker, FactionAPI defender)
	{
		this.market = market;
		this.attacker = attacker;
		this.defender = defender;
	}
	
	public void initDebug() {
		attackerStrength = computeAttackerStrength();
		defenderStrength = computeDefenderStrength();
		
		addIndustryDebug(Industries.SPACEPORT, 400, 5, 0, 0, 0, true);
		addIndustryDebug(Industries.FARMING, 500, 150, 300, 20, 500, false);
		addIndustryDebug(Industries.MINING, 200, 20, 100, 10, 0, true);
		addIndustryDebug(Industries.HEAVYBATTERIES, 0, 0, 500, 200, 2000, false);
		addIndustryDebug(Industries.MILITARYBASE, 0, 0, 1000, 100, 2000, false);
		turnNum = 2;
		
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	public void addIndustryDebug(String industry, int mar1, int heavy1, int mar2, 
			int heavy2, int mil2, boolean attackerControlled) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForInvasion ifi = new IndustryForInvasion();
		ifi.ind = ind;
		ifi.attacker.currCount.put(ForceType.MARINE, mar1);
		ifi.attacker.currCount.put(ForceType.MECH, heavy1);
		ifi.defender.currCount.put(ForceType.MARINE, mar2);
		ifi.defender.currCount.put(ForceType.MECH, heavy2);
		ifi.defender.currCount.put(ForceType.MILITIA, mil2);
		
		ifi.heldByDefender = !attackerControlled;
		
		ifi.attacker.resetWanted();
		ifi.attacker.computeStrength(false);
		ifi.defender.resetWanted();
		ifi.defender.computeStrength(false);
		
		industries.add(ifi);
	}
	
	protected boolean canSupportAttacker(FactionAPI faction) {
		if (faction.isPlayerFaction() && Misc.getCommissionFaction() == attacker)
			return true;
		if (AllianceManager.areFactionsAllied(faction.getId(), attacker.getId()))
			return true;
		
		return false;
	}
	
	protected boolean canSupportAttacker(CampaignFleetAPI fleet) {
		if (!fleet.isPlayerFleet() && !"exerelinInvasionFleet".equals(
					fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE)))
			return false;
		
		return canSupportAttacker(fleet.getFaction());
	}
	
	// TODO
	protected List<CampaignFleetAPI> getAttackerSupportingFleets() {
		List<CampaignFleetAPI> fleets = new ArrayList<>();
		SectorEntityToken token = market.getPrimaryEntity();
		for (CampaignFleetAPI fleet : token.getContainingLocation().getFleets()) 
		{
			if (!canSupportAttacker(fleet))
				continue;			
			
			if (MathUtils.getDistance(fleet, token) > MAX_SUPPORT_DIST)
				continue;
			
			fleets.add(fleet);
		}
		
		return fleets;
	}
	
	public MutableStat computeAttackerStrength() {
		MutableStat stat = new MutableStat(1);
		List<CampaignFleetAPI> fleets = getAttackerSupportingFleets();
		if (fleets.isEmpty())
			stat.modifyMult("no_fleet_support", 0.75f, getString("intelDesc_strength_noFleetSupport"));
		else {
			// get best ground support strength and faction invasion mult
			float bestGroundMult = 0;
			float bestFactionMult = 0;
			FactionAPI bestFaction = null;
			for (CampaignFleetAPI fleet : fleets) {
				float groundMult = fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).computeEffective(1);
				NexFactionConfig atkConf = NexConfig.getFactionConfig(fleet.getFaction().getId());
				float factionMult = 1 + atkConf.invasionStrengthBonusAttack;
				
				if (groundMult > bestGroundMult)
					bestGroundMult = groundMult;
				if (factionMult > bestFactionMult) {
					bestFactionMult = factionMult;
					bestFaction = fleet.getFaction();
				}
			}
			if (bestGroundMult != 1)
				stat.modifyMult("fleet_support_ground", bestGroundMult, StringHelper.getString(
						"exerelin_invasion", "groundSupportCapability"));
			if (bestFaction != null) {
				String desc = StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
						"attackBonus", "$Faction", bestFaction.getDisplayName());
				stat.modifyMult("fleet_support_ground", bestFactionMult, StringHelper.getString(
						"exerelin_invasion", desc));
			}	
		}
		
		return stat;
	}
	
	// FIXME: doesn't handle flat bonuses
	public MutableStat computeDefenderStrength() {
		MutableStat stat = new MutableStat(1);
		StatBonus defStat = InvasionRound.getDefenderStrengthStat(market);
		stat.getMultMods().putAll(defStat.getMultBonuses());
		stat.getPercentMods().putAll(defStat.getPercentBonuses());
		
		return stat;		
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = getSmallDescriptionTitle();

        info.addPara(title, Misc.getBasePlayerColor(), 0f);
		bullet(info);
		info.addPara(attacker.getDisplayName(), attacker.getBaseUIColor(), 3);
		info.addPara(defender.getDisplayName(), defender.getBaseUIColor(), 0);
		unindent(info);
    }
	
	// TODO: market conditions should also be listed here
	public void generateIntro(TooltipMakerAPI info, float width, float pad) {
		info.addImages(width, 128, pad, pad, attacker.getLogo(), defender.getLogo());
		
		String str = getString("intelDesc_intro");
		Map<String, String> sub = new HashMap<>();
		sub.put("$attacker", attacker.getDisplayName());
		sub.put("$theAttacker", attacker.getDisplayNameWithArticle());
		sub.put("$defender", defender.getDisplayNameWithArticle());
		sub.put("$theDefender", defender.getDisplayNameWithArticle());
		sub.put("$market", market.getName());
		sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(),
				market.getName(),
				defender.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(attacker.getBaseUIColor(),
				Misc.getHighlightColor(),
				defender.getBaseUIColor());
		
		str = getString("intelDesc_round");
		info.addPara(str, pad, Misc.getHighlightColor(), turnNum + "");
	}
	
	public void printStrengthStat(TooltipMakerAPI info, MutableStat stat, boolean color) {
		float strength = stat.getModifiedValue();
		String str = getString("intelDesc_strength");
		
		Color hl;
		if (strength > 1) hl = Misc.getPositiveHighlightColor();
		else if (strength < 1) hl = Misc.getNegativeHighlightColor();
		else hl = Misc.getHighlightColor();
		
		info.setParaFontDefault();
		info.addPara(str, 0, hl, String.format("%.2f", strength));
		info.setParaSmallInsignia();
		info.addStatModGrid(350, 50, 10, 3, stat, true, NexUtils.getStatModValueGetter(color, 0));
		info.setParaFontDefault();
	}
	
	float itemPanelHeight = 160;
	public void generateStrengthDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) {
		
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, itemPanelHeight, null);
		
		TooltipMakerAPI dispAtk = strPanel.createUIElement(width/2, itemPanelHeight, false);
		TooltipMakerAPI dispDef = strPanel.createUIElement(width/2, itemPanelHeight, false);
		
		dispAtk.addSectionHeading(getString("intelDesc_strengthHeaderAttacker"), 
				attacker.getBaseUIColor(), attacker.getDarkUIColor(), Alignment.MID, pad);
		dispDef.addSectionHeading(getString("intelDesc_strengthHeaderDefender"), 
				defender.getBaseUIColor(), defender.getDarkUIColor(), Alignment.MID, pad);
		
		printStrengthStat(dispAtk, attackerStrength, true);
		printStrengthStat(dispDef, defenderStrength, true);
		
		strPanel.addUIElement(dispAtk).inLMid(0);
		strPanel.addUIElement(dispDef).inRMid(0);
		
		info.addCustom(strPanel, pad);
	}
	
	public void generateLiftCapacityTables(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		
	}
	
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) {
		info.beginTable(Global.getSector().getPlayerFaction(), 20,
				getString("industryPanel_header_industry"), IndustryForInvasion.COLUMN_WIDTH_INDUSTRY,
				getString("industryPanel_header_heldBy"), IndustryForInvasion.COLUMN_WIDTH_CONTROLLED_BY,
				getString("industryPanel_header_attacker"), IndustryForInvasion.COLUMN_WIDTH_TROOP_TOTAL,
				getString("industryPanel_header_defender"), IndustryForInvasion.COLUMN_WIDTH_TROOP_TOTAL
		);
		info.addTable("", 0, 3);
		
		for (IndustryForInvasion ifi : industries) {
			ifi.renderPanel(panel, info, width);
		}
	}
	
	// adapted from Starship Legends' BattleReport
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		FactionAPI faction = market.getFaction();
		
		TooltipMakerAPI outer = panel.createUIElement(width, height, true);
		
		outer.addSectionHeading(getSmallDescriptionTitle(), faction.getBaseUIColor(), 
				faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		generateIntro(outer, width, opad);
		generateStrengthDisplay(outer, panel, width, opad);
		generateIndustryDisplay(outer, panel, width);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		return str;
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/markets/mercenaries.png";
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (defender.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(attacker.getId());
		tags.add(defender.getId());
		return tags;
	}
	
	@Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() { 
		return true; 
	}	
	
	protected static String getString(String id) {
		return getString(id, false);
	}
	
	protected static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_invasion2", id, ucFirst);
	}
	
	// runcode exerelin.campaign.intel.invasion.GroundBattleIntel.createDebugEvent();
	public static void createDebugEvent() {
		MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
		FactionAPI attacker = Global.getSector().getFaction("tritachyon");
		FactionAPI defender = market.getFaction();
		
		new GroundBattleIntel(market, attacker, defender).initDebug();		
	}
	
	public static boolean isIndustryTrueDisrupted(Industry ind) {
		return ind.getDisruptedDays() > DISRUPT_WHEN_CAPTURED_TIME;
	}
	
	
	public static class IndustryForInvasion {
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
		
		public static final Map<ForceType, Integer[]> ICON_COUNTS = new HashMap<>();
		
		public Industry ind;
		public boolean heldByDefender = true;
		public ForceOnIndustry attacker = new ForceOnIndustry(this, false);
		public ForceOnIndustry defender = new ForceOnIndustry(this, true);
		
		static {
			ICON_COUNTS.put(ForceType.MARINE, new Integer[]{0, 50, 100, 200, 500, 1000, 2000});
			ICON_COUNTS.put(ForceType.MECH, new Integer[]{0, 10, 20, 50, 100, 200, 500});
			ICON_COUNTS.put(ForceType.MILITIA, new Integer[]{0, 100, 200, 500, 1000, 2000, 5000});
			ICON_COUNTS.put(ForceType.REBEL, new Integer[]{0, 100, 200, 500, 1000, 2000, 5000});
		}
		
		public void init() {
			
		}
		
		/**
		 * Gets the number of icons that should be drawn for a given number of a unit type.
		 * @param type
		 * @param count Number of the unit type present.
		 * @return
		 */
		public static int getNumIcons(ForceType type, int count) {
			Integer[] array = ICON_COUNTS.get(type);
			int numIcons = 0;
			while (numIcons < array.length) {
				if (count <= array[numIcons]) break;
				numIcons++;
			}
			return numIcons;
		}
		
		public TooltipMakerAPI renderForcePanel(CustomPanelAPI panel, float width, 
				ForceOnIndustry force, UIComponentAPI rightOf) 
		{
			int height = HEIGHT/3;
			TooltipMakerAPI troops = panel.createUIElement(width, height, false);
			MarketAPI market = ind.getMarket();
			Color hp = Misc.getPositiveHighlightColor();
			Color hn = Misc.getNegativeHighlightColor();
			
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
			TooltipMakerAPI atkPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, attacker, ttOwner);
			TooltipMakerAPI defPanel = renderForcePanel(row, COLUMN_WIDTH_TROOP_TOTAL, defender, atkPanel);	
			
			tooltip.addCustom(row, 10);
		}
	}
	
	public static class ForceOnIndustry {
		public IndustryForInvasion indForInv;
		public boolean isDefender;
		public Map<ForceType, Integer> currCount = new HashMap<>();
		public transient Map<ForceType, Integer> wantedCount = new HashMap<>();
		public transient Map<ForceType, Float> currStrength = new HashMap<>();
		public transient Map<ForceType, Float> wantedStrength = new HashMap<>();
		
		public ForceOnIndustry(IndustryForInvasion indForInv, boolean isDefender) {
			this.indForInv = indForInv;
			this.isDefender = isDefender;
		}
		
		public int getCurrCount(ForceType type) {
			if (!currCount.containsKey(type))
				return 0;
			return currCount.get(type);
		}
		
		public int getWantedCount(ForceType type) {
			if (!wantedCount.containsKey(type))
				return 0;
			return wantedCount.get(type);
		}
		
		public float getCurrStrength(ForceType type) {
			if (!currStrength.containsKey(type))
				return 0;
			return currStrength.get(type);
		}
		
		public float getWantedStrength(ForceType type) {
			if (!wantedStrength.containsKey(type))
				return 0;
			return wantedStrength.get(type);
		}
		
		public void resetWanted() {
			wantedCount = new HashMap<>(currCount);
			computeStrength(true);
		}
		
		public void computeStrength() {
			computeStrength(false);
			computeStrength(true);
		}
		
		public void computeStrength(boolean wanted) {
			for (ForceType type : ForceType.values()) {
				computeStrength(type, wanted);
			}
		}
		
		public void computeStrength(ForceType type, boolean wanted) {
			Integer count = wanted ? wantedCount.get(type) : currCount.get(type);
			if (count == null) return;
			float str = count * type.strength;

			if (type == ForceType.MECH) {
				// mech offensive bonus
				boolean offensive = indForInv.heldByDefender != isDefender;
				if (offensive) str *= 1.25f;
				// mech station penalty
				if (indForInv.ind.getMarket().getPlanetEntity() != null)
					str *= 0.75f;
			}
			
			if (wanted)
				wantedStrength.put(type, str);
			else
				currStrength.put(type, str);
		}
		
		protected Object readResolve() {
			wantedCount = new HashMap<>(currCount);
			wantedStrength = new HashMap<>();
			currStrength = new HashMap<>();
			computeStrength(true);
			computeStrength(false);
			return this;
		}
	}
	
	public static class EventLog {
		
	}
	
	public enum ForceType {
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
}
