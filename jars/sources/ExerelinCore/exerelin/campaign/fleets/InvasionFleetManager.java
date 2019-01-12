package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySourceType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.NexRaidIntel;
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles invasion fleets (the ones that capture stations)
 * Originally derived from Dark.Revenant's II_WarFleetManager
 */
public class InvasionFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
	public static final String MANAGER_MAP_KEY = "exerelin_invasionFleetManager";
	
	public static final int MIN_MARINE_STOCKPILE_FOR_INVASION = 200;
	public static final float MAX_MARINE_STOCKPILE_TO_DEPLOY = 0.5f;
	public static final float DEFENDER_STRENGTH_FP_MULT = 0.75f;
	public static final float DEFENDER_STRENGTH_MARINE_MULT = 1.15f;
	public static final float RESPAWN_FLEET_SPAWN_DISTANCE = 18000f;
	// higher = factions (who aren't otherwise at war) invade pirates less often
	public static final float ALL_AGAINST_ONE_INVASION_POINT_MOD = 0.27f;
	// higher = factions are less likely to target pirates (does nothing if pirates are their only enemies)
	public static final float ONE_AGAINST_ALL_INVASION_BE_TARGETED_MOD = 0.35f;
	// pirates get this multiplier bonus to their invasion point growth the more enemies they have
	public static final float ONE_AGAINST_ALL_INVASION_POINT_MOD = 0.215f;
	public static final float HARD_MODE_INVASION_TARGETING_CHANCE = 1.5f;
	public static final float TEMPLAR_INVASION_POINT_MULT = 1.25f;
	public static final float TEMPLAR_COUNTER_INVASION_FLEET_MULT = 1.25f;
	public static final float DEFENSE_ESTIMATION_MULT = 0.9f;
	public static final float BASE_INVASION_COST = 750f;	// for reference, Jangala at start of game is around 975
	public static final float GENERAL_SIZE_MULT = 0.65f;
	public static final float RAID_SIZE_MULT = 0.8f;
	
	public static final float TANKER_FP_PER_FLEET_FP_PER_10K_DIST = 0.25f;
	public static final Set<String> EXCEPTION_LIST = new HashSet<>(Arrays.asList(new String[]{"templars"}));	// Templars have their own handling
	
	public static final int MAX_ONGOING_INTEL = 10;	// does this ever matter?
	
	public static Logger log = Global.getLogger(InvasionFleetManager.class);
	
	protected final List<RaidIntel> activeIntel = new LinkedList();
	protected HashMap<String, Float> spawnCounter = new HashMap<>();
	
	protected final IntervalUtil tracker;
	
	protected float daysElapsed = 0;
	protected float templarInvasionPoints = 0;
	protected float templarCounterInvasionPoints = 0;
	
	private static InvasionFleetManager invasionFleetManager;
	
	public InvasionFleetManager()
	{
		super(true);
	
		float interval = 8; //Global.getSettings().getFloat("averagePatrolSpawnInterval");
		//interval = 2;	 // debug
		//this.tracker = new IntervalUtil(interval * 0.75F, interval * 1.25F);
		this.tracker = new IntervalUtil(1, 1);
	}
	
	protected static String getString(String id)
	{
		return StringHelper.getString("exerelin_fleets", id);
	}
	
	public static String getFleetName(String fleetType, String factionId, float fp)
	{
		String name = "Fleet";
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		
		switch (fleetType) {
			case "exerelinInvasionFleet":
			case "exerelinRespawnFleet":
				name = factionConfig.invasionFleetName;
				if (fp <= 90) name = getString("invasionFleetPrefixSmall") + " " + name;
				else if (fp >= 270) name = getString("invasionFleetPrefixLarge") + " " + name;
				break;
			case "exerelinInvasionSupportFleet":
				name = factionConfig.invasionSupportFleetName;
				if (fp <= 75) name = getString("invasionSupportFleetPrefixSmall") + " " + name;
				else if (fp >= 225) name = getString("invasionSupportFleetPrefixLarge") + " " + name;
				break;
			case "exerelinDefenceFleet":
				name = factionConfig.defenceFleetName;
				if (fp <= 75) name = getString("defenceFleetPrefixSmall") + " " + name;
				else if (fp >= 225) name = getString("defenceFleetPrefixLarge") + " " + name;
				break;
			case "nex_suppressionFleet":
				name = factionConfig.suppressionFleetName;
				if (fp <= 75) name = getString("suppressionFleetPrefixSmall") + " " + name;
				else if (fp >= 225) name = getString("suppressionFleetPrefixLarge") + " " + name;
				break;
		}
		
		return name;
	}
	
	public static float estimateDefensiveStrength(MarketAPI market, float variability) {
		Random random = new Random();
		float strength = 10 * market.getSize();
		
		int maxLight = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).computeEffective(0);
		int maxMedium = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).computeEffective(0);
		int maxHeavy = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).computeEffective(0);
		
		strength += maxLight * MilitaryBase.getPatrolCombatFP(PatrolType.FAST, random);
		strength += maxMedium * MilitaryBase.getPatrolCombatFP(PatrolType.COMBAT, random);
		strength += maxHeavy * MilitaryBase.getPatrolCombatFP(PatrolType.HEAVY, random);
		
		strength *= market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
		
		if (variability > 0)
			strength *= 1 - variability + random.nextGaussian() * variability;
		
		strength *= DEFENSE_ESTIMATION_MULT;
		
		return strength;
	}
	
	public static float estimateDefensiveStrength(FactionAPI attacker, FactionAPI target, 
			StarSystemAPI system, float variability) {
		float strength = 0f;
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system))
		{
			if (market.getFaction() != target)
				continue;
			strength += estimateDefensiveStrength(market, variability);
		}
		
		return strength;
	}
	
	public static float getWantedFleetSize(FactionAPI attacker, MarketAPI target, float variability)
	{
		//return 100 + target.getSize() * 40;
		
		FactionAPI targetFaction = target.getFaction();
		StarSystemAPI system = target.getStarSystem();
		
		float defenderStr = estimateDefensiveStrength(attacker, targetFaction, system, variability);
		float defensiveStr = defenderStr + WarSimScript.getStationStrength(targetFaction, system, target.getPrimaryEntity());
		
		log.info("Estimated strength required for invasion: " + defensiveStr);
		
		defensiveStr *= GENERAL_SIZE_MULT;
		
		return Math.max(defensiveStr, 30);
	}
	
	/*
	public static InvasionFleetData spawnRespawnFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean useOriginLocation)
	{
		float defenderStrength = InvasionRound.getDefenderStrength(targetMarket, 1f);
		float responseFleetSize = ResponseFleetManager.getMaxReserveSize(targetMarket, false);
		float maxFPbase = (responseFleetSize * DEFENDER_STRENGTH_FP_MULT + (originMarket.getSize() + 2));
		maxFPbase *= 0.8f;
		
		float maxFP = maxFPbase + ExerelinUtilsFleet.getPlayerLevelFPBonus();
		maxFP *= MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f);
		
		String name = getFleetName("exerelinRespawnFleet", faction.getId(), maxFP);
		
		LocationAPI spawnLoc = Global.getSector().getHyperspace();
		if (useOriginLocation)
			spawnLoc = originMarket.getContainingLocation();
		else if (Global.getSector().getStarSystems().size() == 1)	// one-star start; target will be inaccessible from hyper
		{
			SectorEntityToken entity = originMarket.getPrimaryEntity();
			spawnLoc = entity.getContainingLocation();
		}
		
		FleetSpawnParams params = new FleetSpawnParams();
		params.name = name;
		params.fleetType = "exerelinRespawnFleet";
		params.faction = faction;
		params.fp = (int)maxFP;
		params.qualityOverride = 1;
		params.originMarket = originMarket;
		params.targetMarket = targetMarket;
		params.spawnLoc = spawnLoc;
		params.jumpToOrigin = false;
		params.noWait = true;
		//params.aiClass = RespawnFleetAI.class.getName();
		params.numMarines = (int)(defenderStrength * DEFENDER_STRENGTH_MARINE_MULT);
		
		InvasionFleetData fleetData = spawnFleet(params);
		if (fleetData == null) return null;
		if (useOriginLocation)
		{
			Vector2f originLoc = originMarket.getPrimaryEntity().getLocation();
			fleetData.fleet.setLocation(originLoc.x, originLoc.y);
		}
		else {
			float distance = RESPAWN_FLEET_SPAWN_DISTANCE;
			float angle = MathUtils.getRandomNumberInRange(0, 359);
			fleetData.fleet.setLocation((float)Math.cos(angle) * distance, (float)Math.sin(angle) * distance);
		}
		log.info("\tSpawned respawn fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
		return fleetData;
	}
	
	public static InvasionFleetData spawnDefenceFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean noWander, boolean noWait)
	{
		int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * 0.66f);
		float qf = originMarket.getShipQualityFactor();
		qf = Math.max(qf+0.1f, 0.4f);
		
		String name = getFleetName("exerelinDefenseFleet", faction.getId(), maxFP);
		
		FleetSpawnParams params = new FleetSpawnParams();
		params.name = name;
		params.fleetType = "exerelinDefenseFleet";
		params.faction = faction;
		params.fp = maxFP;
		params.qualityOverride = qf;
		params.originMarket = originMarket;
		params.targetMarket = targetMarket;
		params.noWander = noWander;
		params.noWait = noWait;
		//params.aiClass = InvasionSupportFleetAI.class.getName();
		
		InvasionFleetData fleetData = spawnFleet(params);
		log.info("\tSpawned defence fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
		return fleetData;
	}
	
	public static InvasionFleetData spawnSupportFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean noWander, boolean noWait)
	{
		int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * 0.66f);
		float qf = originMarket.getShipQualityFactor();
		qf = Math.max(qf+0.1f, 0.4f);
		
		String name = getFleetName("exerelinInvasionSupportFleet", faction.getId(), maxFP);

		FleetSpawnParams params = new FleetSpawnParams();
		params.name = name;
		params.fleetType = "exerelinInvasionSupportFleet";
		params.faction = faction;
		params.fp = maxFP;
		params.qualityOverride = qf;
		params.originMarket = originMarket;
		params.targetMarket = targetMarket;
		params.noWander = true;
		params.noWait = noWait;
		//params.aiClass = InvasionSupportFleetAI.class.getName();
		
		InvasionFleetData fleetData = spawnFleet(params);
		log.info("\tSpawned strike fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
		return fleetData;
	}
	
	public static InvasionFleetData spawnInvasionFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket,
			float marineMult, boolean noWait)
	{
		return spawnInvasionFleet(faction, originMarket, targetMarket, marineMult, 1, noWait);
	}
	
	public static InvasionFleetData spawnInvasionFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, 
			float marineMult, float fpMult, boolean noWait)
	{
		float defenderStrength = InvasionRound.getDefenderStrength(targetMarket, 0.5f);
		
		int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * fpMult);
		float qf = originMarket.getShipQualityFactor();
		qf = Math.max(qf+0.2f, 0.6f);
		
		String name = getFleetName("exerelinInvasionFleet", faction.getId(), maxFP);
				
		FleetSpawnParams params = new FleetSpawnParams();
		params.name = name;
		params.fleetType = "exerelinInvasionFleet";
		params.faction = faction;
		params.fp = maxFP;
		params.qualityOverride = qf;
		params.originMarket = originMarket;
		params.targetMarket = targetMarket;
		params.noWait = noWait;
		//params.aiClass = InvasionFleetAI.class.getName();
		params.numMarines = (int)(defenderStrength * marineMult);
		
		InvasionFleetData fleetData = spawnFleet(params);
		log.info("\tSpawned invasion fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
		return fleetData;
	}
	*/
	
	public OffensiveFleetIntel generateInvasionFleet(FactionAPI faction, FactionAPI targetFaction, boolean raid)
	{
		return generateInvasionFleet(faction, targetFaction, raid, 1);
	}
	
	/**
	 * Try to create an invasion fleet or raid fleet.
	 * @param faction The faction launching an invasion
	 * @param targetFaction
	 * @param raid
	 * @param sizeMult
	 * @return The invasion fleet intel, if one was created
	 */
	public OffensiveFleetIntel generateInvasionFleet(FactionAPI faction, FactionAPI targetFaction, 
			boolean raid, float sizeMult)
	{
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
		String factionId = faction.getId();
		
		// pick a source market
		for (MarketAPI market : markets) {
			if (market.hasCondition(Conditions.ABANDONED_STATION)) continue;
			if (market.getPrimaryEntity() instanceof CampaignFleetAPI) continue;
			// because incoming invasion fleet being ganked by fresh spawns from the market is just annoying
			if (ExerelinUtilsMarket.isMarketBeingInvaded(market)) continue;
			// markets with ongoing rebellions can't launch invasions
			if (RebellionEvent.isOngoing(market))
				continue;
			
			if	( market.getFactionId().equals(factionId) && market.hasSpaceport() && market.getSize() >= 3 )
			{
				//marineStockpile = market.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
				//if (marineStockpile < MIN_MARINE_STOCKPILE_FOR_INVASION)
				//		continue;
				float weight = 1;	 //marineStockpile;
				if (market.hasIndustry(Industries.PATROLHQ)) {
					weight *= 1.2f;
				}
				if (market.hasIndustry(Industries.MILITARYBASE)) {
					weight *= 1.5f;
				}
				if (market.hasIndustry(Industries.HIGHCOMMAND)) {
					weight *= 2;
				}
				if (market.hasIndustry(Industries.MEGAPORT)) {
					weight *= 1.5f;
				}
				if (market.hasIndustry(Industries.HEAVYINDUSTRY)) {
					weight *= 1.2f;
				}
				if (market.hasIndustry(Industries.ORBITALWORKS)) {
					weight *= 1.5f;
				}
				if (market.hasIndustry(Industries.WAYSTATION)) {
					weight *= 1.2f;
				}
				weight *= 0.5f + (0.5f * market.getSize() * market.getStabilityValue());
				sourcePicker.add(market, weight);
			}
		}
		MarketAPI originMarket = sourcePicker.pick();
		if (originMarket == null) {
			return null;
		}
		//log.info("\tStaging from " + originMarket.getName());
		//marineStockpile = originMarket.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();

		// now we pick a target
		Vector2f originMarketLoc = originMarket.getLocationInHyperspace();
		for (MarketAPI market : markets) 
		{
			FactionAPI marketFaction = market.getFaction();
			String marketFactionId = marketFaction.getId();
			
			if (EXCEPTION_LIST.contains(marketFactionId) && targetFaction != marketFaction) continue;
			if (targetFaction != null && targetFaction != marketFaction)
				continue;
			
			if	(marketFaction.isHostileTo(faction)) 
			{
				if (!ExerelinUtilsMarket.shouldTargetForInvasions(market, 0)) continue;
				/*
				float defenderStrength = InvasionRound.GetDefenderStrength(market);
				float estimateMarinesRequired = defenderStrength * 1.2f;
				if (estimateMarinesRequired > marineStockpile * MAX_MARINE_STOCKPILE_TO_DEPLOY)
					continue;	 // too strong for us
				*/
				
				// base weight based on distance
				float dist = Misc.getDistance(market.getLocationInHyperspace(), originMarketLoc);
				if (dist < 5000.0F) {
					dist = 5000.0F;
				}
				float weight = 20000.0F / dist;
				//weight *= market.getSize() * market.getStabilityValue();	// try to go after high value targets
				if (ExerelinUtilsFaction.isFactionHostileToAll(marketFactionId))
					weight *= ONE_AGAINST_ALL_INVASION_BE_TARGETED_MOD;
				
				// revanchism
				if (ExerelinUtilsMarket.wasOriginalOwner(market, factionId))
					weight *= 4;
				
				// defender of the faith
				if (market.hasCondition(Conditions.LUDDIC_MAJORITY) && ExerelinUtilsFaction.isLuddicFaction(factionId))
					weight *= 4;
				
				// hard mode
				if (SectorManager.getHardMode())
				{
					if (marketFactionId.equals(PlayerFactionStore.getPlayerFactionId()) 
							|| marketFactionId.equals(Factions.PLAYER))
						weight *= HARD_MODE_INVASION_TARGETING_CHANCE;
				}
				
				// help ongoing rebellions
				RebellionEvent event = RebellionEvent.getOngoingEvent(market);
				if (event != null && !faction.isHostileTo(event.getRebelFactionId()))
					weight *= 5;
				
				targetPicker.add(market, weight);
			}
		}
		MarketAPI targetMarket = targetPicker.pick();
		if (targetMarket == null) {
			return null;
		}
		//log.info("\tTarget: " + targetMarket.getName());
		
		// FIXME
		float fp = getWantedFleetSize(faction, targetMarket, 0.2f);
		float organizeTime = 10 + fp/30;
		fp *= 1 + ExerelinConfig.getExerelinFactionConfig(factionId).invasionFleetSizeMod;
		if (raid) fp *= RAID_SIZE_MULT;
		
		// okay, assemble battlegroup
		if (!raid)
		{
			log.info("Spawning invasion fleet for " + faction.getDisplayName() + "; source " + originMarket.getName() + "; target " + targetMarket.getName());
			InvasionIntel intel = new InvasionIntel(faction, originMarket, targetMarket, fp, organizeTime);
			intel.init();
			activeIntel.add(intel);
			return intel;
		}
		else
		{
			// raid
			log.info("Spawning raid fleet for " + faction.getDisplayName() + "; source " + originMarket.getName() + "; target " + targetMarket.getName());
			NexRaidIntel intel = new NexRaidIntel(faction, originMarket, targetMarket, fp, organizeTime);
			intel.init();
			return intel;
		}
	}
	
	protected float getCommodityPoints(MarketAPI market, String commodity) {
		float pts = market.getCommodityData(commodity).getAvailable();
		CommoditySourceType source = market.getCommodityData(commodity).getCommodityMarketData().getMarketShareData(market).getSource();
		if (source == CommoditySourceType.GLOBAL)
			pts *= 0.75f;
		else if (source == CommoditySourceType.LOCAL)
			pts *= 3;
		
		return pts;
	}
	
	protected float getPointsPerMarketPerTick(MarketAPI market)
	{
		float ships = getCommodityPoints(market, Commodities.SHIPS);
		float supplies = getCommodityPoints(market, Commodities.SUPPLIES);
		float marines = getCommodityPoints(market, Commodities.MARINES);
		
		float stabilityMult = 0.25f + (0.75f * market.getStabilityValue()/10);
		
		float total = (ships*2 + supplies + marines) * stabilityMult * ExerelinConfig.invasionPointEconomyMult;
		
		/*
		log.info("Processing invasion points from market: " + market.getName());
		log.info("\tShips available: " + ships);
		log.info("\tSupplies available: " + supplies);
		log.info("\tMarines available: " + marines);
		log.info("\tStability mult: " + stabilityMult);
		log.info("\tTotal points: " + total);
		*/
		
		return total;
	}
	
	protected void processInvasionPoints()
	{
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		//float marineStockpile = 0;
		//log.info("Starting invasion fleet check");
		
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		
		// increment points by market
		if (spawnCounter == null) spawnCounter = new HashMap<>();
		HashMap<String, Float> pointsPerFaction = new HashMap<>();
		for (MarketAPI market : markets)
		{
			String factionId = market.getFactionId();
			if (EXCEPTION_LIST.contains(factionId)) continue;
			if (!pointsPerFaction.containsKey(factionId))
				pointsPerFaction.put(factionId, 0f);
			
			float currPoints = pointsPerFaction.get(factionId);
			float addedPoints = getPointsPerMarketPerTick(market);
			
			currPoints += addedPoints;
			pointsPerFaction.put(factionId, currPoints);
		}
		
		int playerLevel = Global.getSector().getPlayerPerson().getStats().getLevel();
		
		// pick a faction to invade someone
		List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
		for (String factionId: liveFactionIds)
		{
			if (EXCEPTION_LIST.contains(factionId)) continue;
			FactionAPI faction = sector.getFaction(factionId);
			if (faction.isNeutralFaction()) continue;
			//if (faction.isPlayerFaction()) continue;
			ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
			if (!config.playableFaction) continue;
			boolean isPirateFaction = ExerelinUtilsFaction.isPirateFaction(factionId);
			if (!allowPirates && isPirateFaction) continue;
			
			float mult = 0f;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, allowPirates, false, false);
			if (enemies.isEmpty()) continue;
			
			if (ExerelinUtilsFaction.isFactionHostileToAll(factionId))
			{
				float numWars = enemies.size();
				numWars = (float)Math.sqrt(numWars);
				mult = numWars*ONE_AGAINST_ALL_INVASION_POINT_MOD + (1 - ONE_AGAINST_ALL_INVASION_POINT_MOD);
			}
			else
			{
				for (String enemyId : enemies)
				{
					if (EXCEPTION_LIST.contains(factionId)) continue;
					if (ExerelinUtilsFaction.isFactionHostileToAll(enemyId))
					{
						float enemyWars = DiplomacyManager.getFactionsAtWarWithFaction(enemyId, 
								allowPirates, true, false).size();
						enemyWars = (float)Math.sqrt(enemyWars);
						if (enemyWars > 0 )
							mult += 1/((enemyWars*ALL_AGAINST_ONE_INVASION_POINT_MOD) + (1));
					}
					else 
					{
						mult = 1;
						break;
					}
				}
				if (mult > 1) mult = 1;
			}
			
			// increment invasion counter for faction
			if (!spawnCounter.containsKey(factionId))
				spawnCounter.put(factionId, 0f);
			// safety (faction can be live without markets if its last market decivilizes)
			if (!pointsPerFaction.containsKey(factionId))
				pointsPerFaction.put(factionId, 0f);
			
			float counter = spawnCounter.get(factionId);
			float oldCounter = counter;
			float increment = pointsPerFaction.get(factionId) + ExerelinConfig.baseInvasionPointsPerFaction;
			increment += ExerelinConfig.invasionPointsPerPlayerLevel * playerLevel;
			increment *= mult * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			increment *= config.invasionPointMult;
			
			counter += increment;
			
			float pointsRequired = ExerelinConfig.pointsRequiredForInvasionFleet;
			if (counter < pointsRequired)
			{
				spawnCounter.put(factionId, counter);
				if (counter > pointsRequired/2 && oldCounter < pointsRequired/2)
					generateInvasionFleet(faction, null, true);	 // send a couple of strike fleets to troll others
			}
			else
			{
				// okay, we can invade
				InvasionIntel intel = (InvasionIntel)generateInvasionFleet(faction, null, false);
				if (intel != null)
				{
					counter -= getInvasionPointReduction(pointsRequired, intel);
					spawnCounter.put(factionId, counter);
				}
			}
		}
	}
	
	protected void processTemplarInvasionPoints()
	{
		List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
		if (!liveFactionIds.contains("templars")) return;
		
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction("templars", 
				ExerelinConfig.allowPirateInvasions, false, false);
		if (enemies.isEmpty()) return;
		float templarDominance = DiplomacyManager.getDominanceFactor("templars");
		float perLevelPoints = Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.invasionPointsPerPlayerLevel;
		
		templarInvasionPoints += (100 + perLevelPoints) 
			* ExerelinConfig.getExerelinFactionConfig("templars").invasionPointMult * TEMPLAR_INVASION_POINT_MULT;
		templarCounterInvasionPoints += (100 + 200 * templarDominance + perLevelPoints) * TEMPLAR_INVASION_POINT_MULT;
		
		float req = ExerelinConfig.pointsRequiredForInvasionFleet;
		if (templarInvasionPoints >= req)
		{
			InvasionIntel intel = (InvasionIntel)generateInvasionFleet(Global.getSector().getFaction("templars"), null, false);
			if (intel != null) templarInvasionPoints -= getInvasionPointReduction(req, intel);
			//Global.getSector().getCampaignUI().addMessage("Launching Templar invasion fleet");
		}
		if (templarCounterInvasionPoints >= req)
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker();
			for (String factionId : enemies)
			{
				picker.add(factionId, ExerelinUtilsFaction.getFactionMarketSizeSum(factionId));
			}
			FactionAPI faction = Global.getSector().getFaction(picker.pick());
			InvasionIntel intel = (InvasionIntel)generateInvasionFleet(faction, Global.getSector().getFaction("templars"), false, TEMPLAR_COUNTER_INVASION_FLEET_MULT);
			//Global.getSector().getCampaignUI().addMessage("Launching counter-Templar invasion fleet");
			if (intel != null) templarCounterInvasionPoints -= getInvasionPointReduction(req, intel);
		}
	}
	
	protected float getInvasionPointReduction(float base, OffensiveFleetIntel intel)
	{
		return base * Math.max(intel.getFP()/BASE_INVASION_COST, 0.8f);
	}
	
	@Override
	public void advance(float amount)
	{
		if (Global.getSector().isInNewGameAdvance())
			return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
	
		if (daysElapsed < ExerelinConfig.invasionGracePeriod)
		{
			daysElapsed += days;
			return;
		}
		
		this.tracker.advance(days);
		if (!this.tracker.intervalElapsed()) {
			return;
		}
		List<RaidIntel> remove = new LinkedList();
		for (RaidIntel intel : activeIntel) {
			if (intel.isEnded() || intel.isEnding()) {
				remove.add(intel);
			}
		}
		this.activeIntel.removeAll(remove);
	
		if (this.activeIntel.size() < MAX_ONGOING_INTEL)
		{
			processInvasionPoints();
		}
		processTemplarInvasionPoints();
	}
	
	public static InvasionFleetManager getManager()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		invasionFleetManager = (InvasionFleetManager)data.get(MANAGER_MAP_KEY);
		if (invasionFleetManager != null)
			return invasionFleetManager;
		
		return null;
	}
	
	public static InvasionFleetManager create()
	{
		invasionFleetManager = getManager();
		if (invasionFleetManager != null)
			return invasionFleetManager;
		
		Map<String, Object> data = Global.getSector().getPersistentData();
		invasionFleetManager = new InvasionFleetManager();
		data.put(MANAGER_MAP_KEY, invasionFleetManager);
		return invasionFleetManager;
	}
	
	@Override
	public boolean isDone()
	{
		return false;
	}
	
	@Override
	public boolean runWhilePaused()
	{
		return false;
	}
	
	public static class InvasionFleetData
	{
		public CampaignFleetAPI fleet;
		public SectorEntityToken source;
		public SectorEntityToken target;
		public MarketAPI sourceMarket;
		public MarketAPI targetMarket;
		public float startingFleetPoints = 0.0F;
		public int marineCount = 0;
		public boolean noWait = false;
		public boolean noWander = false;
		public InvasionFleetEvent event;
	
		public InvasionFleetData(CampaignFleetAPI fleet)
		{
			this.fleet = fleet;
		}
	}
	
	public static class FleetSpawnParams
	{
		public String name = "Fleet";
		public String fleetType = "genericFleet";
		public int fp = 0;
		public float qualityOverride = -1;
		public FactionAPI faction;
		public MarketAPI originMarket;
		public MarketAPI targetMarket;
		public LocationAPI spawnLoc;
		public boolean noWait = false;
		public boolean jumpToOrigin = true;
		public boolean noWander = false; // only used by strike and patrol fleets ATM
		public int numMarines = 0;
		//public String aiClass;
	}
}