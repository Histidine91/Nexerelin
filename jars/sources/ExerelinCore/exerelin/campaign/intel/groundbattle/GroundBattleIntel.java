package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitSize;
import exerelin.campaign.intel.groundbattle.dialog.UnitOrderDialogPlugin;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
	
	public static final float VIEW_BUTTON_WIDTH = 128;
	public static final float VIEW_BUTTON_HEIGHT = 24;
	
	public static final Object BUTTON_RESOLVE = new Object();
	
	public static Logger log = Global.getLogger(GroundBattleIntel.class);
	
	protected UnitSize unitSize;
	
	protected int turnNum;
	
	protected MarketAPI market;
	protected boolean playerInitiated;
	protected Boolean playerIsAttacker;
	
	protected GroundBattleSide attacker = new GroundBattleSide(this, true);
	protected GroundBattleSide defender = new GroundBattleSide(this, false);
	protected List<GroundUnit> playerUnits = new LinkedList<>();
	
	protected MutableStat liftCapacityAtk = new MutableStat(1);
	protected MutableStat liftCapacityDef = new MutableStat(1);
	protected float liftCapacityAtkRemaining;
	protected float liftCapacityDefRemaining;
	
	protected transient ViewMode viewMode;
	
	protected List<GroundBattleLog> battleLog = new LinkedList<>();
	protected transient List<String> rawLog;
	
	protected List<IndustryForBattle> industries = new ArrayList<>();
	protected List<GroundBattlePlugin> conditionPlugins = new LinkedList<>();
	
	protected Map<String, Object> data = new HashMap<>();
	
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
				unit.heavyArms = Math.round(this.unitSize.avgSize / GroundUnit.HEAVY_COUNT_DIVISOR * MathUtils.getRandomNumberInRange(1, 1.4f));
				unit.men = unit.heavyArms * 2;
			} else {
				unit.men = Math.round(this.unitSize.avgSize * MathUtils.getRandomNumberInRange(1, 1.4f));
				//unit.heavyArms = MathUtils.getRandomNumberInRange(10, 15);
			}
			
			IndustryForBattle loc = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			unit.setLocation(loc);
			if (unit.morale < GBConstants.REORGANIZE_AT_MORALE) {
				unit.reorganize(1);
			}
			else if (Math.random() > 0.5f) {
				unit.dest = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			}
			attacker.units.add(unit);
			playerUnits.add(unit);
		}
		
		defender.generateDefenders();
	}
	
	public void init() {
		List<Industry> mktIndustries = new ArrayList<>(market.getIndustries());
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
		turnNum = 1;
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	public void initDebug() {
		init();
		
		playerInitiated = true;
		playerIsAttacker = true;
		
		generateDebugUnits();
	}
	
	public IndustryForBattle addIndustry(String industry) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForBattle ifb = new IndustryForBattle(this, ind);
		
		industries.add(ifb);
		return ifb;
	}
	
	public List<GroundBattlePlugin> getPlugins() {
		List<GroundBattlePlugin> list = new ArrayList<>();
		for (IndustryForBattle ifb : industries) {
			if (ifb.getPlugin() == null) {
				log.info("Null plugin for " + ifb.ind.getId());
				continue;
			}
			list.add(ifb.getPlugin());
		}
		
		// TODO: add other plugins
		return list;
	}
	
	public MarketAPI getMarket() {
		return market;
	}
	
	public List<IndustryForBattle> getIndustries() {
		return industries;
	}
	
	public IndustryForBattle getIndustryForBattleByIndustry(Industry ind) {
		for (IndustryForBattle ifb : industries) {
			if (ifb.getIndustry() == ind) {
				return ifb;
			}
		}
		return null;
	}
	
	public GroundBattleSide getSide(boolean isAttacker) {
		if (isAttacker) return attacker;
		else return defender;
	}
	
	public Boolean isPlayerAttacker() {
		return playerIsAttacker;
	}
		
	public Boolean isPlayerFriendly(boolean isAttacker) {
		if (playerIsAttacker == null) return null;
		return (playerIsAttacker == isAttacker);
	}
	
	public Color getHighlightColorForSide(boolean isAttacker) {
		Boolean friendly = isPlayerFriendly(isAttacker);
		if (friendly == null) return Misc.getHighlightColor();
		else if (friendly == true) return Misc.getPositiveHighlightColor();
		else return Misc.getNegativeHighlightColor();
	}
	
	public PersonAPI getCommander(GroundUnit unit) {
		if (unit.isPlayer) {
			// TODO: check for whether player is in command range
			return Global.getSector().getPlayerPerson();
		}
		GroundBattleSide side = getSide(unit.isAttacker);
		return side.commander;
	}
	
	public List<GroundUnit> getAllUnits() {
		List<GroundUnit> results = new ArrayList<>(attacker.units);
		results.addAll(defender.units);
		return results;
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
	
	public static void applyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		Collection<String> reasons;
		if (param != null) 
			reasons = (Collection<String>)param;
		else
			reasons = new HashSet<>();
		
		reasons.add(reason);
	}
	
	public static void unapplyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		if (param == null) return;
		
		Collection<String> reasons = (Collection<String>)param;
		reasons.remove(reason);
		if (reasons.isEmpty()) data.remove(tag);
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
	public void generateIntro(CustomPanelAPI outer, TooltipMakerAPI info, float width, float pad) {
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
		
		str = getString("intelDesc_unitSize");
		info.addPara(str, pad, Misc.getHighlightColor(), Misc.ucFirst(unitSize.getName()), unitSize.avgSize + "", unitSize.maxSize + "");
		
		str = getString("intelDesc_round");
		info.addPara(str, pad, Misc.getHighlightColor(), turnNum + "");
		
		if (ExerelinModPlugin.isNexDev) {
			ButtonAPI button = info.addButton("Resolve round", BUTTON_RESOLVE, 128, 24, pad);
		}
		
		// view mode buttons
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder1.addButton(getString("btnViewOverview"), ViewMode.OVERVIEW, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder1).inTL(0, 3);
		
		TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder2.addButton(getString("btnViewCommand"), ViewMode.COMMAND, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);
		
		TooltipMakerAPI btnHolder3 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder3.addButton(getString("btnViewLog"), ViewMode.LOG, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder3).rightOfTop(btnHolder2, 4);
		
		info.addCustom(buttonRow, 0);
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
	
	public void placeCard(TooltipMakerAPI unitCard, int numCards, int numPrevious, 
			CustomPanelAPI unitPanel, List<TooltipMakerAPI> unitCards,
			int maxPerRow) {
		if (numPrevious == 0) {
			// first card, place in TL
			unitPanel.addUIElement(unitCard).inTL(0, 3);
			//log.info("Placing card in TL");
		}
		else if (numPrevious % maxPerRow == 0) {
			// row filled, place under first card of previous row
			int rowNum = numPrevious/maxPerRow - 1;
			TooltipMakerAPI firstOfPrevious = unitCards.get(numCards * rowNum);
			unitPanel.addUIElement(unitCard).belowLeft(firstOfPrevious, 3);
			//log.info("Placing card in new row");
		}
		else {
			// right of last card
			unitPanel.addUIElement(unitCard).rightOfTop(unitCards.get(numPrevious - 1), GroundUnit.PADDING_X);
			//log.info("Placing card in current row");
		}
	}
	
	public void generateUnitDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		info.addSectionHeading(getString("unitPanel_header"), Alignment.MID, pad);
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		info.addPara(getString("unitPanel_resources"), 3);
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
			numCards++;
		}
		if (playerUnits.size() < MAX_PLAYER_UNITS)
			numCards++;	// for the "create unit" card
		
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
				TooltipMakerAPI unitCard = unit.createUnitCard(unitPanel);
				//log.info("Created card for " + unit.name);
				
				int numPrevious = unitCards.size();
				placeCard(unitCard, numCards, numPrevious, unitPanel, unitCards, CARDS_PER_ROW);
				unitCards.add(unitCard);
			}
			if (playerUnits.size() < MAX_PLAYER_UNITS) {
				TooltipMakerAPI newCard = GroundUnit.createBlankCard(unitPanel);
				placeCard(newCard, unitCards.size(), unitCards.size(), unitPanel, unitCards, CARDS_PER_ROW);
			}
			
		} catch (Exception ex) {
			log.error("Failed to display cards", ex);
		}
				
		info.addCustom(unitPanel, 3);
	}
	
	public void populateModifiersDisplay(CustomPanelAPI outer, TooltipMakerAPI disp, 
			float width, float pad, Boolean isAttacker) 
	{
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.addModifierEntry(disp, outer, width, pad, isAttacker);
		}
	}
	
	static float itemPanelHeight = 200;
	public void generateModifiersDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, itemPanelHeight, null);
		
		float subWidth = width/3;
		try {
			TooltipMakerAPI dispAtk = strPanel.createUIElement(subWidth, itemPanelHeight, true);
			strPanel.addUIElement(dispAtk).inTL(0, 0);
			TooltipMakerAPI dispCom = strPanel.createUIElement(subWidth, itemPanelHeight, true);
			strPanel.addUIElement(dispCom).inTMid(0);
			TooltipMakerAPI dispDef = strPanel.createUIElement(subWidth, itemPanelHeight, true);
			strPanel.addUIElement(dispDef).inTR(0, 0);

			FactionAPI player = Global.getSector().getPlayerFaction();

			dispAtk.addSectionHeading(getString("intelDesc_headerAttackerMod"), 
					attacker.faction.getBaseUIColor(), attacker.faction.getDarkUIColor(), Alignment.MID, pad);
			dispCom.addSectionHeading(getString("intelDesc_headerCommonMod"), 
					player.getBaseUIColor(), player.getDarkUIColor(), Alignment.MID, pad);
			dispDef.addSectionHeading(getString("intelDesc_headerDefenderMod"),
					defender.faction.getBaseUIColor(), defender.faction.getDarkUIColor(), Alignment.MID, pad);
			
		
			populateModifiersDisplay(strPanel, dispAtk, subWidth, 3, true);
			populateModifiersDisplay(strPanel, dispCom, subWidth, 3, null);
			populateModifiersDisplay(strPanel, dispDef, subWidth, 3, false);
		} catch (Exception ex) {
			log.error("Failed to display modifiers", ex);
		}
		
		info.addCustom(strPanel, pad);
	}
	
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) 
	{
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
	
	static float logPanelHeight = 240;
	public void generateLogDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width) 
	{
		info.addSectionHeading(getString("logHeader"), Alignment.MID, 10);
		try {
			CustomPanelAPI logPanel = outer.createCustomPanel(width, logPanelHeight, null);
			TooltipMakerAPI scroll = logPanel.createUIElement(width, logPanelHeight, true);
			for (int i=battleLog.size() - 1; i>=0; i--) {
				battleLog.get(i).writeLog(logPanel, scroll, width - 4);
			}

			logPanel.addUIElement(scroll);
			info.addCustom(logPanel, 3);
		} catch (Exception ex) {
			log.error("Failed to create log display", ex);
		}		
	}
	
	public void updateStability() {
		int total = 0, attacker = 0;
		for (IndustryForBattle ifb : industries) {
			total++;
			if (ifb.heldByAttacker) attacker++;
		}
		String desc = getString("stabilityDesc");
		market.getStability().addTemporaryModMult(3, "invasionFlat", desc, -2);
		market.getStability().addTemporaryModMult(3, "invasion", desc, (float)attacker/total);
	}
	
	public void reapply() {
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.unapply();
			plugin.apply();
		}
		updateStability();
	}
	
	public void advanceTurn() {
		reapply();
		new GroundBattleRoundResolve(this).resolveRound();
		turnNum++;
	}
	
	public void addLogEvent(GroundBattleLog log) {
		battleLog.add(log);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_RESOLVE) {
			advanceTurn();
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof ViewMode) {
			viewMode = (ViewMode)buttonId;
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof GroundUnit) {
			ui.showDialog(market.getPrimaryEntity(), new UnitOrderDialogPlugin(this, (GroundUnit)buttonId, ui));
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
		
		if (viewMode == null) viewMode = ViewMode.OVERVIEW;
		
		generateIntro(panel, outer, width, opad);
		generateUnitDisplay(outer, panel, width, opad);
		if (viewMode == ViewMode.OVERVIEW) {
			generateModifiersDisplay(outer, panel, width - 6, opad);
		} else if (viewMode == ViewMode.COMMAND) {
			// abilities, when we get that
		}
		else if (viewMode == ViewMode.LOG) {
			generateLogDisplay(outer, panel, width - 14);
		}
		
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
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_invasion2", id, ucFirst);
	}
	
	// runcode exerelin.campaign.intel.groundbattle.GroundBattleIntel.createDebugEvent();
	public static void createDebugEvent() {
		MarketAPI market = Global.getSector().getEconomy().getMarket("yesod");
		FactionAPI attacker = Global.getSector().getFaction("hegemony");
		FactionAPI defender = market.getFaction();
		
		new GroundBattleIntel(market, attacker, defender).initDebug();
	}
	
	public enum ViewMode {
		OVERVIEW, COMMAND, LOG, HELP
	}
	
	public static final Comparator<Industry> INDUSTRY_COMPARATOR = new Comparator<Industry>() {
		@Override
		public int compare(Industry one, Industry two) {
			return Integer.compare(one.getSpec().getOrder(), two.getSpec().getOrder());
		}
	};
}
