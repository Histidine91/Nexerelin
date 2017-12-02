package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
	public static final float DEFENDER_STRENGTH_FP_MULT = 0.3f;
	public static final float DEFENDER_STRENGTH_MARINE_MULT = 1.15f;
	public static final float RESPAWN_FLEET_SPAWN_DISTANCE = 18000f;
	// higher = factions (who aren't otherwise at war) invade Templars/pirates less often
	public static final float ALL_AGAINST_ONE_INVASION_POINT_MOD = 0.27f;
	// higher = factions are less likely to target Templars/pirates (does nothing if Templars/pirates are their only enemies)
	public static final float ONE_AGAINST_ALL_INVASION_BE_TARGETED_MOD = 0.35f;
	// Templars/pirates get this multiplier bonus to their invasion point growth the more enemies they have
	public static final float ONE_AGAINST_ALL_INVASION_POINT_MOD = 0.215f;
	public static final float HARD_MODE_INVASION_TARGETING_CHANCE = 1.5f;
	public static final float TEMPLAR_INVASION_POINT_MULT = 1.25f;
	public static final float TEMPLAR_COUNTER_INVASION_FLEET_MULT = 1.25f;
	
	public static final float TANKER_FP_PER_FLEET_FP_PER_10K_DIST = 0.05f;
	public static final Set<String> EXCEPTION_LIST = new HashSet<>(Arrays.asList(new String[]{"templars"}));	// Templars have their own handling
	
	public static final int MAX_FLEETS = 50;
	
	public static Logger log = Global.getLogger(InvasionFleetManager.class);
	
	protected final List<InvasionFleetData> activeFleets = new LinkedList();
	protected HashMap<String, Float> spawnCounter = new HashMap<>();
	
	private final IntervalUtil tracker;
	
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
	
	public static float calculateMaxFpForFleet(MarketAPI originMarket, MarketAPI targetMarket)
	{
		// old formula
		/*
		float marketScalar = originMarket.getSize() * originMarket.getStabilityValue();
		if (originMarket.hasCondition(Conditions.MILITARY_BASE) || originMarket.hasCondition("exerelin_military_base")) {
			marketScalar += 20.0F;
		}
		if (originMarket.hasCondition(Conditions.ORBITAL_STATION)) {
			marketScalar += 10.0F;
		}
		if (originMarket.hasCondition(Conditions.SPACEPORT)) {
			marketScalar += 15.0F;
		}
		if (originMarket.hasCondition(Conditions.HEADQUARTERS)) {
			marketScalar += 15.0F;
		}
		if (originMarket.hasCondition(Conditions.REGIONAL_CAPITAL)) {
			marketScalar += 10.0F;
		}
		int maxFP = (int)MathUtils.getRandomNumberInRange(marketScalar * 0.75F, MathUtils.getRandomNumberInRange(marketScalar * 1.5F, marketScalar * 2.5F));
		return maxFP;
		*/
		 
		// new; should be in the same general ballpark for an average case
		float responseFleetSize = ResponseFleetManager.getMaxReserveSize(targetMarket, true);
		float maxFPbase = (responseFleetSize * DEFENDER_STRENGTH_FP_MULT + 8 * (2 + originMarket.getSize()));
		maxFPbase = maxFPbase * (float)(0.5 + originMarket.getStabilityValue()/10);
		maxFPbase *= 0.3f;
		//maxFPbase *= 0.95;
		
		float maxFP = maxFPbase;
		if (originMarket.hasCondition(Conditions.MILITARY_BASE)) maxFP += maxFPbase * 0.15;
		if (originMarket.hasCondition(Conditions.ORBITAL_STATION)) maxFP += maxFPbase * 0.05;
		if (originMarket.hasCondition(Conditions.SPACEPORT)) maxFP += maxFPbase * 0.05;
		if (originMarket.hasCondition(Conditions.REGIONAL_CAPITAL)) maxFP += maxFPbase * 0.05;
		if (originMarket.hasCondition(Conditions.HEADQUARTERS)) maxFP += maxFPbase * 0.1;
		
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(originMarket.getFactionId());
		if (factionConfig != null)
		{
			maxFP += maxFPbase * factionConfig.invasionFleetSizeMod;
		}
		
		maxFP = maxFP + ExerelinUtilsFleet.getPlayerLevelFPBonus();
		
		return (maxFP * (MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f)));
	}
	
	public static InvasionFleetData spawnFleet(FleetSpawnParams params)
	{
		String factionId = params.faction.getId();
		int fp = params.fp;
		float distance = ExerelinUtilsMarket.getHyperspaceDistance(params.originMarket, params.targetMarket);
		int tankerFP = (int)(fp * TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000);
		//fp -= tankerFP;
		
		FleetParams fleetParams = new FleetParams(null, params.originMarket, factionId, null, params.fleetType, 
				fp*0.85f, // combat
				fp*0.1f, // freighters
				tankerFP,		// tankers
				params.numMarines/100*2,		// personnel transports
				0,		// liners
				0,		// civilian
				fp*0.05f,	// utility
				0, params.qualityOverride, 1.25f, 1);	// quality bonus, quality override, officer num mult, officer level bonus
		
		CampaignFleetAPI fleet = ExerelinUtilsFleet.customCreateFleet(params.faction, fleetParams);
		if (fleet == null) return null;
		/*
		CampaignFleetAPI fleet = FleetFactory.createGenericFleet(factionId, params.name, params.qf, params.fp);
		
		for (int i=0; i<params.numMarines; i=i+100)
		{
			 faction.pickShipAndAddToFleet(ShipRoles.PERSONNEL_MEDIUM, 1, fleet);
		}
		*/
		fleet.getCargo().addMarines(params.numMarines);
		fleet.setName(params.name);
		fleet.setAIMode(true);
		if (params.fleetType.equals("exerelinDefenceFleet"))
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
		
		InvasionFleetData data = new InvasionFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.sourceMarket = params.originMarket;
		data.source = params.originMarket.getPrimaryEntity();
		data.targetMarket = params.targetMarket;
		data.target = params.targetMarket.getPrimaryEntity();
		data.marineCount = params.numMarines;
		data.noWait = params.noWait;
		data.noWander = params.noWander;
		
		if (params.spawnLoc == null) params.spawnLoc = params.originMarket.getContainingLocation();
		params.spawnLoc.addEntity(fleet);
		if (params.jumpToOrigin)
		{
			SectorEntityToken entity = params.originMarket.getPrimaryEntity();
			fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
		}
		
		// add AI script
		EveryFrameScript ai = null;
		if (params.fleetType.equals("exerelinInvasionFleet"))
		{
			ai = new InvasionFleetAI(fleet, data);
		}
		else if (params.fleetType.equals("exerelinRespawnFleet"))
		{
			ai = new RespawnFleetAI(fleet, data);
		}
		else if (params.fleetType.equals("exerelinDefenceFleet"))
		{
			ai = new DefenceFleetAI(fleet, data);
		}
		else if (params.fleetType.equals("exerelinInvasionSupportFleet"))
		{
			ai = new InvasionSupportFleetAI(fleet, data);
		}
		fleet.addScript(ai);
		/*
		try {
			Class<?> aiClass = Class.forName(params.aiClass);
			Constructor<?> aiConstructor = aiClass.getConstructor(fleet.getClass(), data.getClass());
			fleet.addScript((EveryFrameScript)aiConstructor.newInstance(fleet, data));
		} catch (Exception ex)
		{
			log.error(ex);
		}
		*/
		
		if (invasionFleetManager != null)
			invasionFleetManager.addActiveFleet(data);
		
		return data;
	}
	
	public static String getFleetName(String fleetType, String factionId, float fp)
	{
		String name = "Fleet";
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		
		switch (fleetType) {
			case "exerelinInvasionFleet":
			case "exerelinRespawnFleet":
				name = StringHelper.getString("exerelin_fleets", "invasionFleetName");
				if (factionConfig != null)
				{
					name = factionConfig.invasionFleetName;
				}
				if (fp <= 18) name = StringHelper.getString("exerelin_fleets", "invasionFleetPrefixSmall") + " " + name;
				else if (fp >= 54) name = StringHelper.getString("exerelin_fleets", "invasionFleetPrefixLarge") + " " + name;
				break;
			case "exerelinInvasionSupportFleet":
				name = StringHelper.getString("exerelin_fleets", "invasionSupportFleetName");
				if (factionConfig != null)
				{
					name = factionConfig.invasionSupportFleetName;
				}
				if (fp <= 15) name = StringHelper.getString("exerelin_fleets", "invasionSupportFleetPrefixSmall") + " " + name;
				else if (fp >= 45) name = StringHelper.getString("exerelin_fleets", "invasionSupportFleetPrefixLarge") + " " + name;
				break;
			case "exerelinDefenceFleet":
				name = StringHelper.getString("exerelin_fleets", "defenceFleetName");
				if (factionConfig != null)
				{
					name = factionConfig.defenceFleetName;
				}
				if (fp <= 15) name = StringHelper.getString("exerelin_fleets", "defenceFleetPrefixSmall") + " " + name;
				else if (fp >= 45) name = StringHelper.getString("exerelin_fleets", "defenceFleetPrefixLarge") + " " + name;
				break;
		}
		
		return name;
	}
	
	public static InvasionFleetData spawnRespawnFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean useOriginLocation)
	{
		float defenderStrength = InvasionRound.GetDefenderStrength(targetMarket, 1f, false);
		float responseFleetSize = ResponseFleetManager.getMaxReserveSize(targetMarket, false);
		float maxFPbase = (responseFleetSize * DEFENDER_STRENGTH_FP_MULT + 8 * (2 + originMarket.getSize()));
		float maxFP = maxFPbase + ExerelinUtilsFleet.getPlayerLevelFPBonus();
		maxFP *= MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f);
		maxFP *= 0.35;
		
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
		params.fp = (int)maxFP;
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
		params.fp = (int)maxFP;
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
		float defenderStrength = InvasionRound.GetDefenderStrength(targetMarket, 0.5f, false);
		
		int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * fpMult);
		float qf = originMarket.getShipQualityFactor();
		qf = Math.max(qf+0.2f, 0.6f);
		
		String name = getFleetName("exerelinInvasionFleet", faction.getId(), maxFP);
				
		FleetSpawnParams params = new FleetSpawnParams();
		params.name = name;
		params.fleetType = "exerelinInvasionFleet";
		params.faction = faction;
		params.fp = (int)maxFP;
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
	
	public boolean generateInvasionFleet(FactionAPI faction, FactionAPI targetFaction, boolean strikeOnly)
	{
		return generateInvasionFleet(faction, targetFaction, strikeOnly, 1);
	}
	
	/**
	 * Try to create an invasion fleet and its accompanying strike fleets (or just the strike fleets)
	 * @param faction The faction launching an invasion
	 * @param targetFaction
	 * @param strikeOnly
	 * @param sizeMult
	 * @return True if fleet was successfully created, false otherwise
	 */
	public boolean generateInvasionFleet(FactionAPI faction, FactionAPI targetFaction, boolean strikeOnly, float sizeMult)
	{
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
		String factionId = faction.getId();
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		
		// pick a source market
		for (MarketAPI market : markets) {
			if (market.hasCondition(Conditions.ABANDONED_STATION)) continue;
			if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(factionId)) continue;
			if (market.getPrimaryEntity() instanceof CampaignFleetAPI) continue;
			// because incoming invasion fleet being ganked by fresh spawns from the market is just annoying
			if (ExerelinUtilsMarket.isMarketBeingInvaded(market)) continue;
			
			if	( market.getFactionId().equals(factionId) && !market.hasCondition(Conditions.DECIVILIZED) && 
				( (market.hasCondition(Conditions.SPACEPORT)) || (market.hasCondition(Conditions.ORBITAL_STATION)) || (market.hasCondition(Conditions.MILITARY_BASE))
					|| (market.hasCondition(Conditions.REGIONAL_CAPITAL)) || (market.hasCondition(Conditions.HEADQUARTERS))
				) && market.getSize() >= 3 )
			{
				//marineStockpile = market.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
				//if (marineStockpile < MIN_MARINE_STOCKPILE_FOR_INVASION)
				//		continue;
				float weight = 1;	 //marineStockpile;
				if (market.hasCondition(Conditions.MILITARY_BASE)) {
					weight *= 1.4F;
				}
				if (market.hasCondition(Conditions.ORBITAL_STATION)) {
					weight *= 1.15F;
				}
				if (market.hasCondition(Conditions.SPACEPORT)) {
					weight *= 1.35F;
				}
				if (market.hasCondition(Conditions.HEADQUARTERS)) {
					weight *= 1.3F;
				}
				if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
					weight *= 1.1F;
				}
				weight *= 0.5f + (0.5f * market.getSize() * market.getStabilityValue());
				sourcePicker.add(market, weight);
			}
		}
		MarketAPI originMarket = sourcePicker.pick();
		if (originMarket == null) {
			return false;
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
			
			if	( marketFaction.isHostileTo(faction)) 
			{
				if (!ExerelinUtilsMarket.shouldTargetForInvasions(market, 0)) continue;
				/*
				float defenderStrength = InvasionRound.GetDefenderStrength(market);
				float estimateMarinesRequired = defenderStrength * 1.2f;
				if (estimateMarinesRequired > marineStockpile * MAX_MARINE_STOCKPILE_TO_DEPLOY)
					continue;	 // too strong for us
				*/
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
				
				if (SectorManager.getHardMode())
				{
					if (marketFactionId.equals(PlayerFactionStore.getPlayerFactionId()) 
							|| marketFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
						weight *= HARD_MODE_INVASION_TARGETING_CHANCE;
				}
				
				targetPicker.add(market, weight);
			}
		}
		MarketAPI targetMarket = targetPicker.pick();
		if (targetMarket == null) {
			return false;
		}
		//log.info("\tTarget: " + targetMarket.getName());

		// okay, assemble battlegroup
		if (!strikeOnly)
		{
			log.info("Spawning invasion fleet for " + faction.getDisplayName() + "; source " + originMarket.getName() + "; target " + targetMarket.getName());
			InvasionFleetData data = spawnInvasionFleet(faction, originMarket, targetMarket, DEFENDER_STRENGTH_MARINE_MULT, false);
			Map<String, Object> params = new HashMap<>();
			params.put("target", targetMarket);
			params.put("dp", data.startingFleetPoints);
			InvasionFleetEvent event = (InvasionFleetEvent)Global.getSector().getEventManager().startEvent(new CampaignEventTarget(originMarket), "exerelin_invasion_fleet", params);
			data.event = event;
			event.reportStart();
		}
		spawnSupportFleet(faction, originMarket, targetMarket, false, false);
		spawnSupportFleet(faction, originMarket, targetMarket, false, false);
		
		return true;
	}
	
	protected void processInvasionPoints()
	{
		SectorAPI sector = Global.getSector();
		//WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
		
		//List<FactionAPI> factions = sector.getAllFactions();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		float marineStockpile = 0;
		//log.info("Starting invasion fleet check");
		
		boolean allowPirates = ExerelinConfig.allowPirateInvasions;
		
		// pick a faction to invade someone
		
		// old random way meh
		/*
		for (FactionAPI faction: factions)
		{
			if (faction.isNeutralFaction()) continue;
			if (faction.isPlayerFaction()) continue;
			if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(faction.getId())) continue;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, ExerelinConfig.allowPirateInvasions, true);

			if (enemies.isEmpty()) continue;
			int numWars = enemies.size();
			
			float weight = 1;
			if (ExerelinUtilsFaction.isPirateOrTemplarFaction(faction.getId())) // TODO don't hardcode faction
			{
				weight = numWars*HOSTILE_TO_ALL_INVASION_POINT_MOD + (1 - HOSTILE_TO_ALL_INVASION_POINT_MOD);
			}
			else if (numWars == 1 && enemies.get(0).equals("templars"))
			{
				weight = HOSTILE_TO_ALL_INVASION_TARGET_MOD;
			}
			factionPicker.add(faction, weight);
		}
		FactionAPI invader = factionPicker.pick();
		if (invader == null) return;
		//log.info("\t" + invader.getDisplayName() + " picked to launch invasion");
		*/
		
		// new "everyone gets a chance way"
		if (spawnCounter == null) spawnCounter = new HashMap<>();
		HashMap<String, Float> pointsPerFaction = new HashMap<>();
		for (MarketAPI market : markets)
		{
			String factionId = market.getFactionId();
			if (EXCEPTION_LIST.contains(factionId)) continue;
			if (!pointsPerFaction.containsKey(factionId))
				pointsPerFaction.put(factionId, 0f);
			
			float points = pointsPerFaction.get(factionId);
			points += market.getSize() * market.getStabilityValue() * ExerelinConfig.invasionPointEconomyMult;
			pointsPerFaction.put(factionId, points);
		}
		
		int playerLevel = Global.getSector().getPlayerPerson().getStats().getLevel();
		
		List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
		for (String factionId: liveFactionIds)
		{
			if (EXCEPTION_LIST.contains(factionId)) continue;
			FactionAPI faction = sector.getFaction(factionId);
			if (faction.isNeutralFaction()) continue;
			if (faction.isPlayerFaction()) continue;
			ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
			if (config != null && !config.playableFaction) continue;
			boolean isPirateFaction = ExerelinUtilsFaction.isPirateFaction(factionId);
			if (!allowPirates && isPirateFaction) continue;
			
			float mult = 0f;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, ExerelinConfig.allowPirateInvasions, false, false);
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
						float enemyWars = DiplomacyManager.getFactionsAtWarWithFaction(enemyId, ExerelinConfig.allowPirateInvasions, true, false).size();
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
			
			// increment invasion counter
			if (!spawnCounter.containsKey(factionId))
				spawnCounter.put(factionId, 0f);
			
			float counter = spawnCounter.get(factionId);
			float oldCounter = counter;
			float increment = pointsPerFaction.get(factionId) + ExerelinConfig.baseInvasionPointsPerFaction;
			increment += ExerelinConfig.invasionPointsPerPlayerLevel * playerLevel;
			increment *= mult * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			
			if (config != null) increment *= config.invasionPointMult; 
			
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
				boolean success = generateInvasionFleet(faction, null, false);
				if (success)
				{
					counter -= pointsRequired;
					spawnCounter.put(factionId, counter);
				}
			}
		}
	}
	
	protected void processTemplarInvasionPoints()
	{
		List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
		if (!liveFactionIds.contains("templars")) return;
		
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction("templars", ExerelinConfig.allowPirateInvasions, false, false);
		if (enemies.isEmpty()) return;
		float templarDominance = DiplomacyManager.getDominanceFactor("templars");
		float perLevelPoints = Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.invasionPointsPerPlayerLevel;
		
		templarInvasionPoints += (100 + perLevelPoints) 
			* ExerelinConfig.getExerelinFactionConfig("templars").invasionPointMult * TEMPLAR_INVASION_POINT_MULT;
		templarCounterInvasionPoints += (100 + 200 * templarDominance + perLevelPoints) * TEMPLAR_INVASION_POINT_MULT;
		
		float req = ExerelinConfig.pointsRequiredForInvasionFleet;
		if (templarInvasionPoints >= req)
		{
			boolean success = generateInvasionFleet(Global.getSector().getFaction("templars"), null, false);
			if (success) templarInvasionPoints -= req;
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
			boolean success = generateInvasionFleet(faction, Global.getSector().getFaction("templars"), false, TEMPLAR_COUNTER_INVASION_FLEET_MULT);
			//Global.getSector().getCampaignUI().addMessage("Launching counter-Templar invasion fleet");
			if (success) templarCounterInvasionPoints -= req;
		}
	}
	
	public void addActiveFleet(InvasionFleetData data)
	{
		activeFleets.add(data);
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
		List<InvasionFleetData> remove = new LinkedList();
		for (InvasionFleetData data : this.activeFleets) {
			if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
				remove.add(data);
			}
		}
		this.activeFleets.removeAll(remove);
	
		if (this.activeFleets.size() < MAX_FLEETS)
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
	public void reportFleetDespawned(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param)
	{
		super.reportFleetDespawned(fleet, reason, param);
		for (InvasionFleetData data : this.activeFleets) {
			if (data.fleet == fleet)
			{
				this.activeFleets.remove(data);
				break;
			}
		}
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