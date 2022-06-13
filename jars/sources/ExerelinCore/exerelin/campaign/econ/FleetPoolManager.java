package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySourceType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import lombok.extern.log4j.Log4j;

@Log4j
public class FleetPoolManager extends BaseIntelPlugin {
	
	public static final boolean USE_POOL = true;
	
	public static final String DATA_KEY = "nex_fleetPoolManager";
	public static final String MEMORY_KEY_POINTS_LAST_TICK = "$nex_fleetPoolPointsLastTick";
	public static final Set<String> EXCEPTION_LIST = InvasionFleetManager.EXCEPTION_LIST;
	public static final float PLAYER_AUTONOMOUS_POINT_MULT = 0.25f;
	public static final List<String> COMMODITIES = Arrays.asList(new String[] {
		Commodities.SHIPS, Commodities.SUPPLIES, Commodities.FUEL
	});
	public static final List<String> COMMODITIES_EXTRA = Arrays.asList(new String[] {
		Commodities.MARINES, Commodities.HAND_WEAPONS
	});
	
	public static final float MARGIN = 40;
	
	protected HashMap<String, Float> factionPools = new HashMap<>();
	protected final IntervalUtil tracker = new IntervalUtil(1, 1);
	
	public FleetPoolManager init() {
		Global.getSector().addScript(this);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().getPersistentData().put(DATA_KEY, this);
		return this;
	}
	
	public static FleetPoolManager getManager() {
		return (FleetPoolManager)Global.getSector().getPersistentData().get(DATA_KEY);
	}
	
	public float getCurrentPool(String factionId) {
		if (!USE_POOL) return 1000000;
		if (!factionPools.containsKey(factionId)) {
			factionPools.put(factionId, 0f);
		}
		return factionPools.get(factionId);
	}
	
	public float modifyPool(String factionId, float amount) {
		float pool = getCurrentPool(factionId);
		pool += amount;
		factionPools.put(factionId, pool);
		return pool;
	}
	
	/**
	 * Request points from the pool, following the specified parameters on overdraft and such.
	 * @param factionId
	 * @param params
	 * @return The points granted for use from the pool (may be zero).
	 */
	public float drawFromPool(String factionId, RequisitionParams params) {
		if (!USE_POOL) return params.amount;
		
		float pool = getCurrentPool(factionId);
		float wantedBase = params.amount * params.amountMult;
		
		// requisition would take us below abort threshold
		if (params.thresholdBeforeAbort != null && pool - wantedBase < params.thresholdBeforeAbort) 
		{
			return 0;
		}
		
		float available = pool - params.thresholdBeforeOverdraft;
		
		// have enough
		if (available >= wantedBase) {
			modifyPool(factionId, -wantedBase);
			return params.amount;
		}
		
		// not enough, overdraw
		float nonOverdraft = available;
		float overdraft = (wantedBase - available) * params.overdraftMult;
		float totalToDraw = overdraft + nonOverdraft;
		
		float returnAmount = params.amount * (totalToDraw/wantedBase);
		if (params.abortIfNotAtLeast > returnAmount) return 0;
		
		modifyPool(factionId, -totalToDraw);
		return returnAmount;
	}
	
	/**
	 * Reverse compatibility for existing saves: transfer points from the existing invasion fleet manager.
	 */
	public void initPointsFromIFM() {
		HashMap<String, Float> invPoints = InvasionFleetManager.getManager().getSpawnCounter();
		for (String factionId : invPoints.keySet()) {
			log.info(String.format("Faction %s has %.0f points", factionId, invPoints.get(factionId)));
		}
		
		factionPools.putAll(invPoints);
	}
	
	/**
	 * Gets the value of the specified commodity availability on the specified 
	 * market in contributing to invasion points. Imported commodities are worth less,
	 * locally manufactured commodities worth more.
	 * @param market
	 * @param commodity
	 * @return
	 */
	public static float getCommodityPoints(MarketAPI market, String commodity) {
		float pts = market.getCommodityData(commodity).getAvailable();
		CommoditySourceType source = market.getCommodityData(commodity).getCommodityMarketData().getMarketShareData(market).getSource();
		if (source == CommoditySourceType.GLOBAL)
			pts *= 0.75f;
		else if (source == CommoditySourceType.LOCAL)
			pts *= 5;
		
		return pts;
	}
	
	public static float getPointsLastTick(MarketAPI market) {
		return market.getMemoryWithoutUpdate().getFloat(MEMORY_KEY_POINTS_LAST_TICK);
	}
	
	public static float getPointsLastTick(FactionAPI faction) {
		return faction.getMemoryWithoutUpdate().getFloat(MEMORY_KEY_POINTS_LAST_TICK);
	}
	
	public void updatePoints() {
		HashMap<String, Float> pointsPerFaction = new HashMap<>();
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		
		for (MarketAPI market : markets)
		{
			String factionId = market.getFactionId();
			// transfer points for markets originally held by derelicts
			String origFactionId = NexUtilsMarket.getOriginalOwner(market);
			if (factionId.equals("nex_derelict") && origFactionId != null)
				factionId = origFactionId;
			
			if (EXCEPTION_LIST.contains(factionId)) continue;
			
			if (market.isHidden()) continue;
			
			float mult = 1;
			
			if (factionId.equals(Factions.PLAYER)) {
				if (!market.isPlayerOwned()) mult *= PLAYER_AUTONOMOUS_POINT_MULT;
			}
			
			if (!pointsPerFaction.containsKey(factionId))
				pointsPerFaction.put(factionId, 0f);
			
			float currPoints = pointsPerFaction.get(factionId);
			float addedPoints = getPointsPerMarketPerTick(market) * mult;
			market.getMemoryWithoutUpdate().set(MEMORY_KEY_POINTS_LAST_TICK, addedPoints, 3);
			
			currPoints += addedPoints;
			pointsPerFaction.put(factionId, currPoints);
		}
		
		int playerLevel = Global.getSector().getPlayerPerson().getStats().getLevel();
		
		// increment points for all live factions
		List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
		for (String factionId: liveFactionIds)
		{
			FactionAPI faction = Global.getSector().getFaction(factionId);
			NexFactionConfig config = NexConfig.getFactionConfig(factionId);
			
			// safety (faction can be live without markets if its last market decivilizes)
			if (!pointsPerFaction.containsKey(factionId))
				pointsPerFaction.put(factionId, 0f);
			
			float pool = getCurrentPool(factionId);
			float increment = pointsPerFaction.get(factionId);
			if (!faction.isPlayerFaction() || NexConfig.followersInvasions) {
				increment += NexConfig.baseInvasionPointsPerFaction;
				increment += NexConfig.invasionPointsPerPlayerLevel * playerLevel;
			}
			
			increment *= config.invasionPointMult;
			faction.getMemoryWithoutUpdate().set(MEMORY_KEY_POINTS_LAST_TICK, increment, 3);
			
			modifyPool(factionId, increment);
		}
	}
	
	/**
	 * Gets the contribution of the specified market to fleet pool points, based on its commodity availability.
	 * Unlike invasion points, does not count marines or hand weapons.
	 * @param market
	 * @return
	 */
	public static float getMarketFleetPoolCommodityValue(MarketAPI market) {
		float ships = getCommodityPoints(market, Commodities.SHIPS);
		float supplies = getCommodityPoints(market, Commodities.SUPPLIES);
		float fuel = getCommodityPoints(market, Commodities.FUEL);
		
		float stabilityMult = 0.25f + (0.75f * market.getStabilityValue()/10);
		
		float total = (ships*3 + supplies*2 + fuel*2) * stabilityMult;
		
		return total;
	}
	
	public static float getPointsPerMarketPerTick(MarketAPI market)
	{
		return getMarketFleetPoolCommodityValue(market) * NexConfig.invasionPointEconomyMult;
	}
	
	/*
	============================================================================
	// start of GUI stuff
	============================================================================
	*/
	
	public void createFactionTable(TooltipMakerAPI tooltip, float width, float pad) 
	{
		width -= MARGIN;
		
		List<String> names = StringHelper.commodityIdListToCommodityNameList(COMMODITIES);
		String str = String.format(getString("desc"), StringHelper.writeStringCollection(names, true, true));
		tooltip.addPara(str, pad, Misc.getHighlightColor(), names.toArray(new String[0]));
		
		names = StringHelper.commodityIdListToCommodityNameList(COMMODITIES_EXTRA);
		str = String.format(getString("desc2"), StringHelper.writeStringCollection(names, true, true));
		tooltip.addPara(str, pad, Misc.getHighlightColor(), names.toArray(new String[0]));		
		
		tooltip.addSectionHeading(getString("tableHeader"), com.fs.starfarer.api.ui.Alignment.MID, pad);
		
		float cellWidth = 0.2f * width;
		tooltip.beginTable(getFactionForUIColors(), 20, StringHelper.getString("faction", true), 0.2f * width,
				getString("tablePool"), cellWidth,
				getString("tableIncrement"), cellWidth,
				getString("tablePool2"), cellWidth,
				getString("tableIncrement"), cellWidth
		);
		
		List<FactionAPI> factions = NexUtilsFaction.factionIdsToFactions(SectorManager.getLiveFactionIdsCopy());
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		
		for (FactionAPI faction : factions) {
			String factionId = faction.getId();
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			if (!conf.playableFaction || conf.disableDiplomacy) continue;
			
			List<Object> rowContents = new ArrayList<>();
			
			// add faction
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(faction.getBaseUIColor());
			rowContents.add(Misc.ucFirst(faction.getDisplayName()));
			
			// current pool
			float pool = getCurrentPool(factionId);
			rowContents.add(String.format("%.0f", pool));
			// last increment
			float increment = getPointsLastTick(faction);
			rowContents.add(String.format("%.0f", increment));
			
			// invasion points
			float invPoints = InvasionFleetManager.getManager().getSpawnCounter(factionId);
			rowContents.add(String.format("%.0f", invPoints));
			// last increment
			float increment2 = InvasionFleetManager.getPointsLastTick(faction);
			rowContents.add(String.format("%.0f", increment2));
			
			tooltip.addRow(rowContents.toArray());
		}
		
		tooltip.addTable("", 0, pad);
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/3, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);
		
		panel.addUIElement(superheaderHolder).inTL(width*0.4f, 0);
		
		TooltipMakerAPI tableHolder = panel.createUIElement(width, 600, true);
		
		createFactionTable(tableHolder, width, 10);
		panel.addUIElement(tableHolder).inTL(3, 48);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);		
		tracker.advance(days);
		if (tracker.intervalElapsed()) {
			updatePoints();
		}
	}

	@Override
	public boolean hasSmallDescription() {
		return false;
	}

	@Override
	public boolean hasLargeDescription() {
		return true;
	}
	
	@Override
	public String getIcon() {
		return Global.getSettings().getHullSpec("hammerhead").getSpriteName();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		tags.add(DiplomacyProfileIntel.getString("intelTag"));
		return tags;
	}	

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_1;
	}
	
	@Override
	protected String getName() {
		return getString("title");
	}
	
	@Override
	public boolean isHidden() {
		return !USE_POOL && !ExerelinModPlugin.isNexDev;
	}
	
	protected String getString(String id) {
		return StringHelper.getString("nex_fleetPool", id);
	}
	
	public TooltipCreator createTooltip(final String str, final String... args) {
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 360;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				tooltip.addPara(str, 0, Misc.getHighlightColor(), args);
			}			
		};
	}
			
	public static class ScoreEntry implements Comparable<ScoreEntry> {
		public String factionId;
		public List<String> allianceMembers;
		public Alliance alliance;
		public int score;
		
		public ScoreEntry(String factionId, int score) {
			this.factionId = factionId;
			this.score = score;
		}
		
		public ScoreEntry(Alliance alliance, int score) {
			this.alliance = alliance;
			this.score = score;
			allianceMembers = alliance.getMembersSorted();
			factionId = allianceMembers.get(0);			
		}
		
		public String getIcon() {
			return Global.getSettings().getHullSpec("hammerhead").getSpriteName();
		}
		
		public Color getColor() {
			return Global.getSector().getFaction(factionId).getBaseUIColor();
		}
		
		public String getName() {
			if (alliance != null) return alliance.getName();
			return Global.getSector().getFaction(factionId).getDisplayName();
		}
		
		/**
		 * Excludes the largest member (since their crest will be used as the main icon).
		 * @return
		 */
		public String[] getAllianceMemberCrests() {
			if (allianceMembers == null) return null;
			String[] crests = new String[allianceMembers.size() - 1];
			if (crests.length <= 0) return crests;
			for (int i=0; i<crests.length; i++) {
				crests[i] = Global.getSector().getFaction(allianceMembers.get(i+1)).getCrest();
				//log.info("Adding crest " + crests[i]);
			}			
			return crests;
		}

		@Override
		public int compareTo(ScoreEntry other) {
			return Integer.compare(other.score, score);
		}
	}
	
	public static class RequisitionParams {
		
		public static final float DEFAULT_ABORT_RATIO = 0.75f;
		
		/**
		 * How many points we want to take.
		 */
		public float amount;
		/**
		 * Multiplier for the points actually requested from pool.<br/>
		 * Use this if {@code amount} is not known when params are instantiated but added later by some other code.
		 */
		public float amountMult = 1;
		/**
		 * If pool would drop below this amount, {@code overdraftMult} applies. Default 0.
		 */
		public float thresholdBeforeOverdraft;		
		/**
		 * If pool would drop below this amount, abort the requisition completely.
		 * Can e.g. set to zero if we don't want any overdraft.
		 */
		public Float thresholdBeforeAbort;		
		/**
		 * If we would overdraw from the pool, the overdraft amount is multiplied by this figure.
		 * e.g. if requested amount is 200, available is 100, and overdraftMult is 0.5,
		 * give us 150 points (100 in pool, plus overdraft of (200-100)*0.5).
		 */
		public float overdraftMult = 1;
		
		/**
		 * If the pool won't give us at least this many points, cancel the requisition. Defaults to {@code DEFAULT_ABORT_RATIO} times amount.
		 */
		public float abortIfNotAtLeast;
		
		public RequisitionParams() {}
		
		public RequisitionParams(float amount) {
			this.amount = amount;
			this.abortIfNotAtLeast = amount * DEFAULT_ABORT_RATIO;
		}
		
		public RequisitionParams(float amount, float thresholdBeforeOverdraft, Float thresholdBeforeAbort, float overdraftMult) 
		{
			this.amount = amount;
			this.thresholdBeforeOverdraft = thresholdBeforeOverdraft;
			this.thresholdBeforeAbort = thresholdBeforeAbort;
			this.overdraftMult = overdraftMult;
			this.abortIfNotAtLeast = amount * DEFAULT_ABORT_RATIO;
		}
	}
}
