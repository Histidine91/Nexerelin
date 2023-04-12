package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.*;

public class PlayerOutpostIntel extends BaseIntelPlugin implements EconomyUpdateListener, EconomyTickListener {
	
	public static final List<String> STATION_IMAGES = new ArrayList<>(Arrays.asList(new String[] {
		"station_mining00", "station_side03", "station_side05"
	}));
	public static final List<String> INTERACTION_IMAGES = new ArrayList<>(Arrays.asList(new String[] {
		"orbital", "orbital_construction"
	}));
	public static final Set<String> UNWANTED_COMMODITIES = new HashSet<>(Arrays.asList(new String[] {
		Commodities.CREW, Commodities.ORGANICS, Commodities.DOMESTIC_GOODS, Commodities.LUXURY_GOODS,
		Commodities.DRUGS, Commodities.FOOD
	}));
	public static final String ENTITY_TAG = "nex_playerOutpost";
	public static final String MARKET_MEMORY_FLAG = "$nex_playerOutpost";
	public static final String BUTTON_SCUTTLE = "scuttle";
	
	protected SectorEntityToken outpost;
	protected MarketAPI market;
	protected Long timestamp;
	
	public static final String OUTPOST_SET_KEY = "nex_playerOutposts";
		
	public static int getMetalsRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_metals");
	}

	public static int getMachineryRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_machinery");
	}

	public static int getSuppliesRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_supplies");
	}
	
	public static int getCrewRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_crew");
	}
	
	public static int getGammaCoresRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_gammacores");
	}
	
	public static int getCreditsRequired() {
		return Global.getSettings().getInt("nex_playerOutpost_credits");
	}
	
	public static int getUpkeep() {
		return Global.getSettings().getInt("nex_playerOutpost_upkeep");
	}
	
	public static Set<PlayerOutpostIntel> getOutposts() {
		Map<String, Object> data = Global.getSector().getPersistentData();
		Set<PlayerOutpostIntel> outposts = null;
		if (data.containsKey(OUTPOST_SET_KEY))
			outposts = (HashSet<PlayerOutpostIntel>)data.get(OUTPOST_SET_KEY);
		else {
			outposts = new HashSet<>();
			data.put(OUTPOST_SET_KEY, outposts);
		}
		return outposts;
	}
	
	public static void registerOutpost(PlayerOutpostIntel outpost) {
		getOutposts().add(outpost);
	}
	
	public static void deregisterOutpost(PlayerOutpostIntel outpost) {
		getOutposts().remove(outpost);
	}
		
	public MarketAPI getMarket() {
		return market;
	}
	
	public SectorEntityToken createOutpost(CampaignFleetAPI fleet, SectorEntityToken target)
	{
		SectorEntityToken toOrbit = target;
		float orbitRadius = 100;
		float orbitPeriod = 60;	//target.getOrbit().getOrbitalPeriod();
		String name = target.getStarSystem().getBaseName();
		
		if (toOrbit instanceof PlanetAPI)
		{
			orbitRadius += toOrbit.getRadius();
			orbitPeriod = NexUtilsAstro.getOrbitalPeriod(toOrbit, orbitRadius);
			name = toOrbit.getName();
		}
		else if (toOrbit instanceof AsteroidAPI)
		{
			SectorEntityToken asteroidSource = toOrbit.getOrbitFocus();
			
			// orbit planet/star directly
			// shouldn't happen though
			if (asteroidSource instanceof PlanetAPI)	
			{
				toOrbit = asteroidSource;
			}
			else if (asteroidSource instanceof CampaignTerrainAPI) 
			{
				OrbitAPI targOrbit = target.getOrbit();
				CampaignTerrainPlugin terrain = ((CampaignTerrainAPI)asteroidSource).getPlugin();
				if (terrain.getTerrainId().equals(Terrain.ASTEROID_BELT))
				{
					toOrbit = asteroidSource.getOrbitFocus();
					if (targOrbit != null) orbitPeriod = targOrbit.getOrbitalPeriod();
				}
				else if (terrain.getTerrainId().equals(Terrain.ASTEROID_FIELD))
				{
					toOrbit = asteroidSource;
					if (targOrbit != null) orbitPeriod = targOrbit.getOrbitalPeriod();
				}
			}
			orbitRadius = Misc.getDistance(fleet.getLocation(), toOrbit.getLocation());
		}
		if (toOrbit == null) toOrbit = target;	// safety
		
		float angle = Misc.getAngleInDegrees(fleet.getLocation(), toOrbit.getLocation()) - 180;
		
		name += " " + getString("outpostProperName");
		String id = "nex_outpost_" + (getOutposts().size() + 1);
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		picker.addAll(STATION_IMAGES);
		
		outpost = toOrbit.getContainingLocation().addCustomEntity(id, name, picker.pick(), Factions.PLAYER);
		
		outpost.setCircularOrbitPointingDown(toOrbit, angle, orbitRadius, orbitPeriod);
		outpost.addTag("nex_playerOutpost");
		
		// add market
		market = Global.getFactory().createMarket(id, name, 2);
		market.setFactionId(Factions.PLAYER);
		market.setPrimaryEntity(outpost);
		market.addCondition(Conditions.ABANDONED_STATION);
		market.addIndustry(Industries.POPULATION);
		market.addIndustry(Industries.SPACEPORT);
		market.addIndustry(Industries.WAYSTATION);
		market.getIndustry(Industries.SPACEPORT).startBuilding();
		market.getIndustry(Industries.WAYSTATION).startBuilding();
		
		market.setHidden(true);
		market.setPlanetConditionMarketOnly(false);
		market.setEconGroup(market.getId());
		market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
		market.getMemoryWithoutUpdate().set(MARKET_MEMORY_FLAG, this);
		
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);	// not doing this makes market condition tooltips fail to appear
		market.addSubmarket(Submarkets.LOCAL_RESOURCES);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		StoragePlugin storage = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
		storage.setPlayerPaidToUnlock(true);
		market.setInvalidMissionTarget(true);
		market.addTag(Tags.MARKET_NO_OFFICER_SPAWN);

		outpost.setMarket(market);
		
		Global.getSector().getEconomy().addMarket(market, false);
		Global.getSector().getEconomy().addUpdateListener(this);
		Global.getSector().getListenerManager().addListener(this);
		
		// interaction image
		picker.clear();
		picker.addAll(INTERACTION_IMAGES);
		outpost.setInteractionImage("illustrations", picker.pick());
		outpost.setCustomDescriptionId("nex_playerOutpost");
		
		registerOutpost(this);
		
		Global.getSector().getIntelManager().addIntel(this, true);
		
		timestamp = Global.getSector().getClock().getTimestamp();
		
		return outpost;
	}
	
	public void endEvent() {
		Global.getSector().getEconomy().removeMarket(market);
		Global.getSector().getEconomy().removeUpdateListener(this);
		Global.getSector().getListenerManager().removeListener(this);
		deregisterOutpost(this);
		endAfterDelay();
	}
	
	public void dismantleOutpost() {
		endEvent();		
		Misc.fadeAndExpire(outpost);
	}
	
	@Override
	public void commodityUpdated(String commodityId) 
	{
		CommodityOnMarketAPI com = market.getCommodityData(commodityId);
		String modId = market.getId();
		
		// zero demand for "human" commodities
		if (UNWANTED_COMMODITIES.contains(commodityId)) {
			//Global.getLogger(this.getClass()).info("Nullifying demand for " + com.getId());
			for (Industry ind : market.getIndustries()) {
				ind.getDemand(commodityId).getQuantity().modifyMult(modId, 0, getString("commodityStatDescUnneeded"));
				ind.getSupply(commodityId).getQuantity().modifyMult(modId, 0, getString("commodityStatDescUnneeded"));
			}
		}
		else {
			for (Industry ind : market.getIndustries()) {
				ind.getDemand(com.getId()).getQuantity().unmodify(modId);
				ind.getSupply(commodityId).getQuantity().unmodify(modId);
			}
		}
		
		int curr = 0;
		
		StatMod mod = com.getAvailableStat().getFlatStatMod(modId);
		if (mod != null) {
			curr = Math.round(mod.value);
		}
		
		int a = com.getAvailable() - curr;
		int d = com.getMaxDemand();
		if (d > a) {
			//int supply = Math.max(1, d - a - 1);
			int supply = Math.max(1, d - a);
			com.getAvailableStat().modifyFlat(modId, supply, getString("commodityStatDesc"));
		}
	}
	
	public boolean isAlive() {
		return outpost != null && outpost.isAlive() && !isEnding() && !isEnded();
	}
	
	@Override
	public String getName() {
		String str = getString("intelTitle");
		if (outpost != null) {
			str = outpost.getName();
			
		}
		if (!isAlive()) {
			str += " - " + getString("intelTitleLost", true); 
		}
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color c = getFactionForUIColors().getBaseUIColor();
		Color d = getFactionForUIColors().getDarkUIColor();
		
		if (outpost != null)
			info.addImage(outpost.getCustomEntitySpec().getSpriteName(), width, 96, opad);
		
		if (!isAlive()) {
			info.addPara(getString("intelDescLost"), opad);
			return;
		}
		
		Map<String, String> replace = new HashMap<>();
		StarSystemAPI system = outpost.getStarSystem();
		
		replace.put("$name", outpost.getName());
		replace.put("$location", system.getNameWithLowercaseType());
		
		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);
		
		String str = StringHelper.getStringAndSubstituteTokens("nex_playerOutpost", "intelDesc", replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(outpost.getName());
		label.setHighlightColors(Misc.getHighlightColor());
		
		// scuttle button
		ButtonAPI button = info.addButton(getString("intelButtonScuttle", true), 
					BUTTON_SCUTTLE, c, d, (int)(width), 20f, opad * 2f);
		
		//display stored items
		if (market != null)
			Misc.addStorageInfo(info, c, d, market, true, false);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		dismantleOutpost();
		super.buttonPressConfirmed(buttonId, ui);
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return true;
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		String str = getString("intelPromptScuttle");
		prompt.addPara(str, 0);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		//tags.add(Factions.PLAYER);
		tags.add(StringHelper.getString("colonies", true));
		return tags;
	}
	
	@Override
	public String getIcon() {
		if (outpost != null)
			return outpost.getCustomEntitySpec().getSpriteName();
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (isAlive()) return outpost;
		return null;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_playerOutpost", id, ucFirst);
	}

	@Override
	public void economyUpdated() {		
		
	}

	@Override
	public boolean isEconomyListenerExpired() {
		return isEnding() || isEnded();
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		if (market.getFaction().isNeutralFaction()) {	// abandoned
			endEvent();
			return;
		}
		
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		
		//CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
		marketsNode.name = StringHelper.getString("colonies", true);
		marketsNode.custom = MonthlyReport.OUTPOSTS;
		marketsNode.tooltipCreator = report.getMonthlyReportTooltip();
		
		FDNode outpostNode = report.getNode(marketsNode, "nex_node_id_outposts");
		outpostNode.name = getString("outposts", true);
		outpostNode.custom = "nex_node_id_outposts";
		outpostNode.icon = "graphics/stations/station_side03.png";
		outpostNode.tooltipCreator = OUTPOST_NODE_TOOLTIP;
		outpostNode.upkeep += getUpkeep() * f;
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static final TooltipMakerAPI.TooltipCreator OUTPOST_NODE_TOOLTIP = new TooltipMakerAPI.TooltipCreator() {
		public boolean isTooltipExpandable(Object tooltipParam) {
			return false;
		}
		public float getTooltipWidth(Object tooltipParam) {
			return 450;
		}
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			tooltip.addPara(getString("tooltipUpkeep"), 0,
				Misc.getHighlightColor(), 
				Misc.getWithDGS(Global.getSettings().getInt("nex_playerOutpost_upkeep")));
		}
	};
}
