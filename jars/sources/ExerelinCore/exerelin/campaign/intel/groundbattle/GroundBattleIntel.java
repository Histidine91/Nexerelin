package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
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
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitSize;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

// may not actually use this in the end and just go with the "disrupt everything" system
public class GroundBattleIntel extends BaseIntelPlugin {
	
	public static final float MAX_SUPPORT_DIST = 250;
	public static final int MAX_PLAYER_UNITS = 12;
	public static final float DISRUPT_WHEN_CAPTURED_TIME = 0.25f;	// days
	
	public static Logger log = Global.getLogger(GroundBattleIntel.class);
	
	protected UnitSize unitSize;
	
	protected int turnNum;
	
	protected MarketAPI market;
	protected boolean playerInvolved;
	
	protected GroundBattleSide attacker = new GroundBattleSide(this, true);
	protected GroundBattleSide defender = new GroundBattleSide(this, false);
	protected List<GroundUnit> playerUnits = new LinkedList<>();
	
	protected MutableStat liftCapacityAtk = new MutableStat(1);
	protected MutableStat liftCapacityDef = new MutableStat(1);
	protected float liftCapacityAtkRemaining;
	protected float liftCapacityDefRemaining;
	
	public List<IndustryForBattle> industries = new ArrayList<>();
	
	public GroundBattleIntel(MarketAPI market, FactionAPI attacker, FactionAPI defender)
	{
		this.market = market;
		this.attacker.faction = attacker;
		this.defender.faction = defender;
		
		int size = market.getSize();
		if (size <= 3)
			unitSize = UnitSize.PLATOON;
		else if (size <= 5)
			unitSize = UnitSize.COMPANY;
		else if (size <= 7)
			unitSize = UnitSize.BATALLION;
		else
			unitSize = UnitSize.REGIMENT;
	}
	
	protected void generateDebugUnits() 
	{
		for (int i=0; i<6; i++) {
			GroundUnit unit = new GroundUnit(this, ForceType.MARINE, 0, i);
			unit.faction = Global.getSector().getPlayerFaction();
			unit.isPlayer = true;
			unit.isAttacker = true;
			unit.type = i >= 4 ? ForceType.HEAVY : ForceType.MARINE;
			
			if (unit.type == ForceType.HEAVY) {
				unit.heavyArms = MathUtils.getRandomNumberInRange(12, 16);
				unit.men = unit.heavyArms * 2;
			} else {
				unit.men = MathUtils.getRandomNumberInRange(100, 120);
				//unit.heavyArms = MathUtils.getRandomNumberInRange(10, 15);
			}
			
			unit.morale = (float)Math.random();
			IndustryForBattle loc = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			unit.setLocation(loc);
			if (unit.morale < GroundUnit.REORGANIZE_AT_MORALE) {
				if (!unit.data.containsKey("reorganizing"))
					unit.data.put("reorganizing", 1);
			}
			else if (Math.random() > 0.5f) {
				unit.dest = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			}
			
			playerUnits.add(unit);
		}
		
		defender.generateDefenders();
	}
	
	public void initDebug() {
		
		List<Industry> mktIndustries = market.getIndustries();
		Collections.sort(mktIndustries, INDUSTRY_COMPARATOR);
		for (Industry ind : mktIndustries) {
			if (ind.getId().equals(Industries.POPULATION)) continue;
			if (ind.isHidden()) continue;
			if (ind.getSpec().hasTag(Industries.TAG_STATION)) continue;
			
			addIndustry(ind.getId());
		}
		if (industries.isEmpty()) {
			addIndustry(Industries.POPULATION);
		}
		turnNum = 2;
		playerInvolved = true;
		
		generateDebugUnits();
		
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	public IndustryForBattle addIndustry(String industry) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForBattle ifb = new IndustryForBattle(this, ind);
		
		industries.add(ifb);
		return ifb;
	}
	
	public GroundBattleSide getSide(boolean isAttacker) {
		if (isAttacker) return attacker;
		else return defender;
	}
	
	protected boolean canSupportAttacker(FactionAPI faction) {
		if (faction.isPlayerFaction() && Misc.getCommissionFaction() == attacker)
			return true;
		if (AllianceManager.areFactionsAllied(faction.getId(), attacker.faction.getId()))
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
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = getSmallDescriptionTitle();

        info.addPara(title, Misc.getBasePlayerColor(), 0f);
		bullet(info);
		info.addPara(attacker.faction.getDisplayName(), attacker.faction.getBaseUIColor(), 3);
		info.addPara(defender.faction.getDisplayName(), defender.faction.getBaseUIColor(), 0);
		unindent(info);
    }
	
	// TODO: market conditions should also be listed here
	public void generateIntro(TooltipMakerAPI info, float width, float pad) {
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
		
		String str = getString("intelDesc_intro");
		Map<String, String> sub = new HashMap<>();
		sub.put("$attacker", attacker.faction.getDisplayName());
		sub.put("$theAttacker", attacker.faction.getDisplayNameWithArticle());
		sub.put("$defender", defender.faction.getDisplayNameWithArticle());
		sub.put("$theDefender", defender.faction.getDisplayNameWithArticle());
		sub.put("$market", market.getName());
		sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(attacker.faction.getDisplayNameWithArticleWithoutArticle(),
				market.getName(),
				defender.faction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(attacker.faction.getBaseUIColor(),
				Misc.getHighlightColor(),
				defender.faction.getBaseUIColor());
		
		str = getString("intelDesc_round");
		info.addPara(str, pad, Misc.getHighlightColor(), turnNum + "");
	}
	
	protected String getCommoditySprite(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}
	
	public TooltipMakerAPI addResourceSubpanel(CustomPanelAPI resourcePanel, float width, 
			TooltipMakerAPI rightOf, String commodity, int amount) 
	{
		TooltipMakerAPI subpanel = resourcePanel.createUIElement(width, 32, false);
		TooltipMakerAPI image = subpanel.beginImageWithText(getCommoditySprite(commodity), 32);
		image.addPara(amount + "", 0);
		subpanel.addImageWithText(0);
		if (rightOf == null)
			resourcePanel.addUIElement(subpanel).inTL(0, 0);
		else
			resourcePanel.addUIElement(subpanel).rightOfTop(rightOf, 0);
		
		return subpanel;
	}
	
	public void generateUnitDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		info.addSectionHeading("Available units", Alignment.MID, pad);
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		info.addPara("Resources on fleet", 3);
		CustomPanelAPI resourcePanel = panel.createCustomPanel(width, 32, null);
		TooltipMakerAPI resourceSubPanel;
		
		int subWidth = 96;
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, null, 
				Commodities.MARINES, cargo.getMarines());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.HAND_WEAPONS, (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS));
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.SUPPLIES, (int)cargo.getSupplies());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.FUEL, (int)cargo.getFuel());
		
		info.addCustom(resourcePanel, 3);
				
		int CARDS_PER_ROW = (int)(width/(GroundUnit.PANEL_WIDTH + GroundUnit.PADDING_X));
		
		int numCards = 0;
		for (GroundUnit unit : playerUnits) {
			if (unit.faction == Global.getSector().getPlayerFaction())
				numCards++;
		}
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		//log.info("Number of rows: " + NUM_ROWS);
		//log.info("Cards per row: " + CARDS_PER_ROW);
		
		CustomPanelAPI unitPanel = panel.createCustomPanel(width, NUM_ROWS * (GroundUnit.PANEL_HEIGHT + 3), null);
		
		//TooltipMakerAPI test = unitPanel.createUIElement(64, 64, true);
		//test.addPara("wololo", 0);
		//unitPanel.addUIElement(test).inTL(0, 0);
		
		List<TooltipMakerAPI> unitCards = new ArrayList<>();
		
		try {
			for (GroundUnit unit : playerUnits) {
				if (unit.faction != Global.getSector().getPlayerFaction()) continue;

				TooltipMakerAPI unitCard = unit.createUnitCard(unitPanel);
				//log.info("Created card for " + unit.name);

				int numPrevious = unitCards.size();
				if (numPrevious == 0) {
					// first card, place in TL
					unitPanel.addUIElement(unitCard).inTL(0, 3);
					//log.info("Placing card in TL");
				}
				else if (numPrevious % CARDS_PER_ROW == 0) {
					// row filled, place under first card of previous row
					int rowNum = numPrevious/CARDS_PER_ROW - 1;
					TooltipMakerAPI firstOfPrevious = unitCards.get(numCards * rowNum);
					unitPanel.addUIElement(unitCard).belowLeft(firstOfPrevious, 3);
					//log.info("Placing card in new row");
				}
				else {
					// right of last card
					unitPanel.addUIElement(unitCard).rightOfTop(unitCards.get(numPrevious - 1), GroundUnit.PADDING_X);
					//log.info("Placing card in current row");
				}
				unitCards.add(unitCard);
			}
		} catch (Exception ex) {
			log.error("Failed to display cards", ex);
		}
				
		info.addCustom(unitPanel, 3);
	}
	
	float itemPanelHeight = 160;
	public void generateStrengthDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, itemPanelHeight, null);
		
		TooltipMakerAPI dispAtk = strPanel.createUIElement(width/2, itemPanelHeight, false);
		TooltipMakerAPI dispDef = strPanel.createUIElement(width/2, itemPanelHeight, false);
		
		dispAtk.addSectionHeading(getString("intelDesc_headerAttackerBonus"), 
				attacker.faction.getBaseUIColor(), attacker.faction.getDarkUIColor(), Alignment.MID, pad);
		dispDef.addSectionHeading(getString("intelDesc_headerDefenderBonus"), 
				defender.faction.getBaseUIColor(), defender.faction.getDarkUIColor(), Alignment.MID, pad);
				
		strPanel.addUIElement(dispAtk).inLMid(0);
		strPanel.addUIElement(dispDef).inRMid(0);
		
		info.addCustom(strPanel, pad);
	}
	
	public void generateLiftCapacityTables(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		
	}
	
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) {
		info.beginTable(Global.getSector().getPlayerFaction(), 0,
				getString("industryPanel_header_industry"), IndustryForBattle.COLUMN_WIDTH_INDUSTRY,
				//getString("industryPanel_header_heldBy"), IndustryForBattle.COLUMN_WIDTH_CONTROLLED_BY,
				getString("industryPanel_header_attacker"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL,
				getString("industryPanel_header_defender"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL
		);
		info.addTable("", 0, 3);
		
		for (IndustryForBattle ifb : industries) {
			ifb.renderPanel(panel, info, width);
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
		generateUnitDisplay(outer, panel, width, opad);
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
		if (defender.faction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(attacker.faction.getId());
		tags.add(defender.faction.getId());
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
	
	// runcode exerelin.campaign.intel.groundbattle.GroundBattleIntel.createDebugEvent();
	public static void createDebugEvent() {
		MarketAPI market = Global.getSector().getEconomy().getMarket("yesod");
		FactionAPI attacker = Global.getSector().getFaction("hegemony");
		FactionAPI defender = market.getFaction();
		
		new GroundBattleIntel(market, attacker, defender).initDebug();		
	}
	
	public static boolean isIndustryTrueDisrupted(Industry ind) {
		return ind.getDisruptedDays() > DISRUPT_WHEN_CAPTURED_TIME;
	}
	
	public static class EventLog {
		
	}
	
	public enum Tab {
		UNITS, BATTLE, LOG, HELP
	}
	
	public static final Comparator<Industry> INDUSTRY_COMPARATOR = new Comparator<Industry>() {
		@Override
		public int compare(Industry one, Industry two) {
			return Integer.compare(one.getSpec().getOrder(), two.getSpec().getOrder());
		}
	}; 
}
