package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.ExerelinConstants;
import exerelin.campaign.battle.NexBattleAutoresolvePlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;

/**
 * Handles defence stations
 */
public class DefenceStationManager extends BaseCampaignEventListener implements EveryFrameScript
{
	public static final boolean ENABLED = true;
	
	public static Logger log = Global.getLogger(DefenceStationManager.class);
	
	public static final String MANAGER_MAP_KEY = "exerelin_defenceStationManager";
	public static final float STATION_POINTS_PER_DAY = 0.3f;
	protected static final float CONSTRUCTION_MARKET_STABILITY_DIVISOR = 5f;
	public static final float DEFENCE_FP_PENALTY_PER_STATION = 15;	// make response fleet smaller if we already have stations
	public static final int MAX_STATIONS_PER_FLEET = 1;
	// if true, stations will be a semi-permanent fixture of campaign layer
	// else they'll only appear when they're needed
	public static final boolean STATIONS_IN_CAMPAIGN_LAYER = true;
	public static final boolean FLEETS_ATTACK_STATIONS = false;
	
	public static final boolean DEBUG_MODE = false;
	
	protected Map<String, Integer> maxStations = new HashMap<>();
	protected Map<String, Float> constructionPoints = new HashMap<>();
	protected Map<String, CampaignFleetAPI> fleets = new HashMap<>();
	protected List<CampaignFleetAPI> allFleets = new LinkedList<>();
	protected Random random = new Random();
		
	protected final IntervalUtil tracker = new IntervalUtil(2, 2);
	protected final IntervalUtil trackerShort = new IntervalUtil(0.2f, 0.2f);
	
	private static DefenceStationManager defenceStationManager;
	
	public DefenceStationManager()
	{
		super(true);
		
		/*
		reserves = new HashMap<>();
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for(MarketAPI market:markets)
			reserves.put(market.getId(), getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
		*/
	}
	
	
	/**
	 * Gets the maximum number of defense stations this market can have.
	 * Results are cached; cache entry is cleared when market is captured
	 * @param market
	 * @return 0, 1 or 2
	 */
	public int getMaxStations(MarketAPI market)
	{
		if (!ENABLED) return 0;
		String id = market.getId();
		if (maxStations.containsKey(id))
			return maxStations.get(id);
		
		// no station types specified?
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		if (conf.defenceStations.isEmpty())
			return 0;
		
		// default case: planet with no (civilian) orbital station
		int sizeForOne = 7;
		int sizeForTwo = 12;
		
		// free-standing station (market does not include a planet)
		if (market.getPrimaryEntity().hasTag(Tags.STATION) && market.getPlanetEntity() == null)
		{
			sizeForOne = 5;
			sizeForTwo = 8;
		}
		// planet with station
		else if (market.hasCondition(Conditions.ORBITAL_STATION))
		{
			sizeForOne = 4;
			sizeForTwo = 7;
		}
		if (market.hasCondition(Conditions.MILITARY_BASE))
		{
			sizeForOne -= 1;
			sizeForTwo -= 2;
		}
		if (market.hasCondition(Conditions.HEADQUARTERS))
		{
			sizeForOne -= 1;
			sizeForTwo -= 2;
		}
		if (market.hasCondition(Conditions.REGIONAL_CAPITAL))
		{
			sizeForOne -= 1;
			sizeForTwo -= 1;
		}
		
		int size = market.getSize();
		int max = 0;
		if (size > sizeForTwo) max = 2;
		else if (size > sizeForOne) max = 1;
		
		if (max > MAX_STATIONS_PER_FLEET)
			max = MAX_STATIONS_PER_FLEET;
		
		maxStations.put(id, max);
		return max;
	}
	
	public void resetMaxStations(MarketAPI market)
	{
		maxStations.remove(market.getId());
	}
	
	public void resetMaxStations()
	{
		maxStations.clear();
	}
	
	/**
	 * Gets how much smaller response fleets should be due to having stations
	 * @param market
	 * @return
	 */
	public float getDefenceFleetPenaltyFromStations(MarketAPI market)
	{
		int numStations = getMaxStations(market);
		if (numStations == 0) return 0;
		float points = ExerelinConfig.getExerelinFactionConfig(market.getFactionId()).stationGenPoints;
		points *= numStations;
		//if (points != DEFENCE_FP_PENALTY_PER_STATION)
		//	log.info("Market " + market.getName() + " has response fleet penalty " + points);
		return points;
	}
	
	public CampaignFleetAPI createFleet(MarketAPI origin)
	{
		//float qf = origin.getShipQualityFactor();
		//qf = Math.max(qf, 0.7f);
		
		String factionId = origin.getFactionId();
		String fleetFactionId = factionId;
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig fleetFactionConfig = null;
		
		if (factionConfig.factionIdForHqResponse != null && origin.hasCondition(Conditions.HEADQUARTERS))
		{
			fleetFactionId = factionConfig.factionIdForHqResponse;
			fleetFactionConfig = ExerelinConfig.getExerelinFactionConfig(fleetFactionId);
		}
		
		String name;
		
		if (fleetFactionConfig != null)
			name = fleetFactionConfig.stationName;
		else
			name = factionConfig.stationName;
		
		CampaignFleetAPI fleet = FleetFactoryV2.createEmptyFleet(factionId, FleetTypes.BATTLESTATION, origin);
		if (fleet == null) return null;
		
		fleet.setFaction(factionId, true);
		fleet.setName(name);
		fleet.setAIMode(true);
		fleet.setStationMode(true);
		fleet.setAI(null);
		fleet.setTransponderOn(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
		fleet.getMemoryWithoutUpdate().set("$nex_defstation", true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);
		
		fleet.setInteractionImage("illustrations", "orbital");
		
		return fleet;
	}
	
	public void registerFleet(CampaignFleetAPI fleet, MarketAPI origin)
	{
		fleets.put(origin.getId(), fleet);
		allFleets.add(fleet);
		setFleetsDoNotAttackStation(fleet);
	}
	
	public void spawnFleetInOrbit(CampaignFleetAPI fleet, MarketAPI origin)
	{
		SectorEntityToken entity = origin.getPrimaryEntity();
		float orbitRadius = 100;
		float orbitDays = 30;
		
		if (entity instanceof PlanetAPI) {
			orbitRadius = entity.getRadius();
			orbitDays = ((PlanetAPI)entity).getSpec().getRotation() * 4;
		}
				
		entity.getContainingLocation().addEntity(fleet);
		fleet.setCircularOrbitWithSpin(entity, ExerelinUtilsAstro.getRandomAngle(), orbitRadius, orbitDays, 20, 30);
		
		debugMessage("Spawned station fleet " + fleet.getNameWithFaction() + " at " + origin.getName());
	}
	
	/**
	 * Spawns a new station FleetMemberAPI and adds it to the specified station fleet.
	 * @param fleet
	 * @param origin
	 */
	public void addStationToFleet(CampaignFleetAPI fleet, MarketAPI origin)
	{
		String factionId = origin.getFactionId();
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig fleetFactionConfig = factionConfig;
		
		if (factionConfig.factionIdForHqResponse != null && origin.hasCondition(Conditions.HEADQUARTERS))
		{
			factionId = factionConfig.factionIdForHqResponse;
			fleetFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionConfig.factionIdForHqResponse);
		}
		
		String variantId = ExerelinUtils.getRandomListElement(fleetFactionConfig.defenceStations);
		if (variantId == null || variantId.isEmpty())
			return;
		
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
		member.setShipName(Global.getSector().getFaction(factionId).pickRandomShipName());
		fleet.getFleetData().addFleetMember(member);
		
		// CR
		member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		
		// officer
		int level = (origin.getSize() + 2) * 2;
		level = (int)(level * origin.getStats().getDynamic().getValue(Stats.OFFICER_LEVEL_MULT));
		PersonAPI commander = OfficerManagerEvent.createOfficer(
				Global.getSector().getFaction(factionId), level, true);
		FleetFactoryV2.addCommanderSkills(commander, fleet, random);
		commander.setRankId(Ranks.SPACE_CAPTAIN);
		
		member.setCaptain(commander);
		fleet.getFleetData().addOfficer(commander);
		if (fleet.getFleetData().getNumMembers() == 1)
			fleet.setCommander(commander);
		
		debugMessage("Added station " + member.getVariant().getFullDesignationWithHullName() 
				+ " to fleet " + fleet.getNameWithFaction() + " at " + origin.getName());
	}
	
	/**
	 * Gets the station construction points for the specified market.
	 * @param market
	 * @return
	 */
	public float getConstructionPoints(MarketAPI market)
	{
		if (!constructionPoints.containsKey(market.getId()))
			constructionPoints.put(market.getId(), 0f);
		
		return constructionPoints.get(market.getId());
	}
	
	/**
	 * Gets the station fleet for the specified market.
	 * @param market
	 * @return
	 */
	public CampaignFleetAPI getFleet(MarketAPI market)
	{
		CampaignFleetAPI fleet = null;
		if (fleets.containsKey(market.getId()))
			fleet = fleets.get(market.getId());
		
		// is this safety needed?
		if (fleet != null && !fleet.isAlive())
		{
			reportFleetDespawned(fleet, FleetDespawnReason.NO_MEMBERS, null);
			return null;
		}
		return fleet;
	}
	
	public List<CampaignFleetAPI> getFleetsCopy() {
		return new ArrayList<>(allFleets);
	}
	
	public void updateStationConstruction(float days)
	{
		// prevents NPE of unknown origin
		if (Global.getSector() == null || Global.getSector().getEconomy() == null)
			return;
		
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : markets)
		{
			int maxStations = getMaxStations(market);
			if (maxStations <= 0) continue;
			
			CampaignFleetAPI currFleet = getFleet(market);
			int currFleetSize = currFleet != null ? currFleet.getFleetData().getNumMembers() : 0;
			if (currFleetSize >= maxStations) continue;			
			
			float currPoints = getConstructionPoints(market);
			
			int marketSize = market.getSize();
			if (market.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize++;
			
			float baseIncrement = marketSize * (0.5f + (market.getStabilityValue()/CONSTRUCTION_MARKET_STABILITY_DIVISOR));
			float increment = baseIncrement;
			//if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.HEADQUARTERS)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.MILITARY_BASE)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.AUTOFAC_HEAVY_INDUSTRY)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.ORBITAL_STATION)) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.SPACEPORT)) increment += baseIncrement * 0.1f;
			
			/*
			ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
			if (factionConfig != null)
			{
				increment += baseIncrement * factionConfig.responseFleetSizeMod;
			}
			*/
			
			increment = increment * STATION_POINTS_PER_DAY * days;
			float newPoints = currPoints + increment;
			if (newPoints > 100)
			{
				newPoints -= 100;
				CampaignFleetAPI curr = getFleet(market);
				if (curr == null)
				{
					curr = createFleet(market);
					registerFleet(curr, market);
					if (STATIONS_IN_CAMPAIGN_LAYER)
						spawnFleetInOrbit(curr, market);
				}
				addStationToFleet(curr, market);
			}
			
			constructionPoints.put(market.getId(), newPoints);
		}
	}
	
	public Random getRandom() 
	{
		return random;
	}
	
	@Override
	public void advance(float amount)
	{
		if (!ENABLED) return;
		float days = Global.getSector().getClock().convertToDays(amount);	
		
		/*
		if (!STATIONS_IN_CAMPAIGN_LAYER)
		{
			for (CampaignFleetAPI fleet : allFleets)
			{
				if (fleet.getBattle() != null)
					fleet.advance(amount);
			}
		}
		*/
		
		trackerShort.advance(days);
		if (trackerShort.intervalElapsed())
		{
			for (CampaignFleetAPI station : allFleets)
			{
				if (STATIONS_IN_CAMPAIGN_LAYER && station.getBattle() != null)
				{
					String marketId = station.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
					CampaignFleetAPI other = station.getBattle().getOtherSideFor(station).get(0);
					if (other != null)
					{
						EncounterOption eo = other.getAI().pickEncounterOption(null, station);
						if (eo == EncounterOption.DISENGAGE || eo == EncounterOption.HOLD_VS_STRONGER) continue;
						
						MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
						// disabled for debugging
						ResponseFleetManager.requestResponseFleet(market, other, market.getPrimaryEntity());
					}
				}
				for (FleetMemberAPI member : station.getFleetData().getMembersListCopy())
				{
					member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
				}
			}
		}
			
		tracker.advance(days);
		if (tracker.intervalElapsed()) {
			updateStationConstruction(tracker.getIntervalDuration());
		}
	}
	
	/**
	 * Creates initial station fleets for each market (instead of having to wait for them to build up).
	 */
	public void seedFleets()
	{
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : markets)
		{
			int maxStations = getMaxStations(market);
			if (maxStations <= 0) continue;
			
			CampaignFleetAPI curr = createFleet(market);
			registerFleet(curr, market);
			if (STATIONS_IN_CAMPAIGN_LAYER)
				spawnFleetInOrbit(curr, market);
			
			for (int i=0; i<maxStations; i++)
				addStationToFleet(curr, market);
		}
	}
	
	public boolean isRegisteredDefenceStation(FleetMemberAPI member)
	{
		for (CampaignFleetAPI entry : allFleets)
		{
			if (entry.getFleetData().getMembersListCopy().contains(member))
				return true;
		}
		return false;
	}
	
	/**
	 * Sets all fleets in the sector to not attack the specified station fleet
	 * @param station
	 */
	public void setFleetsDoNotAttackStation(CampaignFleetAPI station)
	{
		if (FLEETS_ATTACK_STATIONS) return;
		List<CampaignFleetAPI> fleetsInSector = ExerelinUtilsFleet.getAllFleetsInSector();
		for (CampaignFleetAPI fleet : fleetsInSector)
		{
			if (fleet.getAI() == null) continue;
			fleet.getAI().doNotAttack(station, 99999);
		}
	}
	
	/**
	 * Sets this fleet to not attack any registered station fleets
	 * @param fleet
	 */
	public void setFleetDoNotAttackStations(CampaignFleetAPI fleet)
	{
		if (FLEETS_ATTACK_STATIONS) return;
		for (CampaignFleetAPI station : allFleets)
		{
			if (fleet.getAI() == null) continue;
			fleet.getAI().doNotAttack(station, 99999);
		}
	}
	
	/**
	 * Makes all fleets not attack registered station fleets
	 */
	public void setFleetsDoNotAttackStations()
	{
		if (FLEETS_ATTACK_STATIONS) return;
		List<CampaignFleetAPI> fleetsInSector = ExerelinUtilsFleet.getAllFleetsInSector();
		for (CampaignFleetAPI fleet : fleetsInSector)
		{
			setFleetDoNotAttackStations(fleet);
		}
	}
	
	/**
	 * Clears all fleets not attacking registered station fleets.
	 * (i.e. they can now attack stations)
	 */
	public void clearFleetsDoNotAttackStations()
	{
		if (FLEETS_ATTACK_STATIONS) return;
		List<CampaignFleetAPI> fleetsInSector = ExerelinUtilsFleet.getAllFleetsInSector();
		for (CampaignFleetAPI fleet : fleetsInSector)
		{
			for (CampaignFleetAPI station : allFleets)
			{
				if (fleet.getAI() == null) continue;
				fleet.getAI().doNotAttack(station, 0);
				fleet.getAI().advance(1);
			}
		}
	}
	
	@Override
	public boolean isDone()
	{
		return false;
	}
	
	// fleets don't pick fights with stations
	@Override
	public void reportFleetSpawned(CampaignFleetAPI fleet) {
		setFleetDoNotAttackStations(fleet);
	}
	
	@Override
	public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
		if (!allFleets.contains(fleet))
			return;
		//if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE && reason != FleetDespawnReason.NO_MEMBERS)
		//	return;
			
		debugMessage("Despawning defence station " + fleet.getNameWithFaction() + ": "  + reason.toString());
		allFleets.remove(fleet);
		String marketId = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
		fleets.remove(marketId);
	}
	
	// restore health of station modules after a battle (since they can't be remembered)
	@Override
	public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (!ENABLED) return;
		if (!battle.isDone()) return;
		List<CampaignFleetAPI> bothSides = battle.getSnapshotBothSides();
		for (CampaignFleetAPI fleet : bothSides)
		{
			if (!fleet.isStationMode()) continue;
			for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
			{
				NexBattleAutoresolvePlugin.removeStationFromModuleHullCache(member);
				for (int i = 0; i < member.getVariant().getModuleSlots().size(); i++)
				{
					if (member.getStatus().isDetached(i)) continue;
					member.getStatus().setHullFraction(i, 1);
				}
			}
		}		
	}
	
	@Override
	public boolean runWhilePaused()
	{
		return false;
	}
	
	public static void debugMessage(String msg)
	{
		log.info(msg);
		if (DEBUG_MODE)
			Global.getSector().getCampaignUI().addMessage(msg);
	}
	
	public static DefenceStationManager getManager()
	{
		// don't use this; the static var gets dissociated from the one in the sector after game save
		//if (defenceStationManager != null)
		//	return defenceStationManager;
		
		Map<String, Object> data = Global.getSector().getPersistentData();
		defenceStationManager = (DefenceStationManager)data.get(MANAGER_MAP_KEY);
		if (defenceStationManager != null)
			return defenceStationManager;
		return null;
	}
	
	public static boolean hasStation(List<CampaignFleetAPI> fleets)
	{
		for (CampaignFleetAPI fleet : fleets)
		{
			if (fleet.isStationMode()) return true;
		}
		return false;
	}
	
	public static boolean hasStation(BattleAPI battle, BattleAPI.BattleSide side)
	{
		return hasStation(battle.getSide(side));
	}
	
	public static DefenceStationManager create()
	{
		DefenceStationManager saved = getManager();
		if (saved != null) return saved;
		
		Map<String, Object> data = Global.getSector().getPersistentData();
		defenceStationManager = new DefenceStationManager();
		data.put(MANAGER_MAP_KEY, defenceStationManager);
		return defenceStationManager;
	}
}