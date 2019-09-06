package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidBeltTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		Set<PlayerOutpostIntel> outposts = getOutposts();
		outposts.add(outpost);
	}
	
	public PlayerOutpostIntel() {
		
	}
	
	protected Object readResolve() {
		if (outpost == null && market != null)
			outpost = market.getPrimaryEntity();
		if (timestamp == null)
			timestamp = Global.getSector().getClock().getTimestamp();
		
		if (!Global.getSector().getIntelManager().hasIntel(this)) {
			Global.getSector().getIntelManager().addIntel(this, true);
		}
		
		return this;
	}
	
	public SectorEntityToken createOutpost(CampaignFleetAPI fleet, SectorEntityToken target)
	{
		// FIXME: orbit period seems too fast
		SectorEntityToken toOrbit = target;
		float orbitRadius = 100;
		float orbitPeriod = target.getOrbit().getOrbitalPeriod();
		String name = target.getStarSystem().getBaseName();
		
		if (toOrbit instanceof PlanetAPI)
		{
			orbitRadius += toOrbit.getRadius();
			orbitPeriod = ExerelinUtilsAstro.getOrbitalPeriod(toOrbit, orbitRadius);
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
				CampaignTerrainPlugin terrain = ((CampaignTerrainAPI)asteroidSource).getPlugin();
				if (terrain instanceof AsteroidBeltTerrainPlugin)
				{
					toOrbit = asteroidSource.getOrbitFocus();
					orbitPeriod = target.getOrbit().getOrbitalPeriod();
				}
				else if (terrain instanceof AsteroidFieldTerrainPlugin)
				{
					toOrbit = asteroidSource;
					orbitPeriod = target.getOrbit().getOrbitalPeriod();
				}
			}
			orbitRadius = Misc.getDistance(fleet.getLocation(), toOrbit.getLocation());
		}
		float angle = Misc.getAngleInDegrees(fleet.getLocation(), toOrbit.getLocation()) - 180;

		// FIXME: name based on star system
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
		//market.addIndustry(Industries.SPACEPORT);
		market.addIndustry(Industries.WAYSTATION);
		//market.getIndustry(Industries.SPACEPORT).startBuilding();
		market.getIndustry(Industries.WAYSTATION).startBuilding();
		
		market.setHidden(true);
		market.setPlanetConditionMarketOnly(false);
		market.setEconGroup(market.getId());
		market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
		market.getMemoryWithoutUpdate().set(MARKET_MEMORY_FLAG, true);
		
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);	// not doing this makes market condition tooltips fail to appear
		market.addSubmarket(Submarkets.LOCAL_RESOURCES);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		StoragePlugin storage = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
		storage.setPlayerPaidToUnlock(true);

		outpost.setMarket(market);
		
		Global.getSector().getEconomy().addMarket(market, false);
		Global.getSector().getEconomy().addUpdateListener(this);
		Global.getSector().getListenerManager().addListener(this);
		
		// interaction image
		picker.clear();
		picker.addAll(INTERACTION_IMAGES);
		outpost.setInteractionImage("illustrations", picker.pick());
		outpost.setCustomDescriptionId("nex_playerOutpost");
		outpost.getMemoryWithoutUpdate().set("$nex_outpost_intel", this);
		
		registerOutpost(this);
		
		Global.getSector().getIntelManager().addIntel(this, true);
		
		timestamp = Global.getSector().getClock().getTimestamp();
		
		return outpost;
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
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		info.addPara(getSmallDescriptionTitle(), c, 0f);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		if (outpost != null) {
			str = outpost.getName();
			
		}
		if (!isAlive()) {
			str += " - " + getString("intelTitleLost"); 
		}
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		Color c = getFactionForUIColors().getBaseUIColor();
		Color d = getFactionForUIColors().getDarkUIColor();
		
		if (outpost != null)
			info.addImage(outpost.getCustomEntitySpec().getSpriteName(), width, 96, opad);
		
		if (!isAlive() || outpost == null) {
			// TODO: show offline message
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
		
		//display stored items
		if (market != null)
			Misc.addStorageInfo(info, c, d, market, true, false);
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
		return isEnded();
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
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
		outpostNode.custom = "node_id_nex_outposts";
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
