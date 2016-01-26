package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAI;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.RollingAverageTracker;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.util.ArrayList;
import java.util.List;

public class ExerelinPatrolFleetManager extends PatrolFleetManager {

	protected MarketAPI market;
	protected List<PatrolFleetData> activePatrols = new ArrayList<PatrolFleetData>();
	protected IntervalUtil tracker;
	protected int maxPatrols;

	protected RollingAverageTracker patrolBattlesLost;
	
	// same as vanilla
	public ExerelinPatrolFleetManager(MarketAPI market) {
		super(market);
		this.market = market;
		
		float interval = Global.getSettings().getFloat("averagePatrolSpawnInterval");
		tracker = new IntervalUtil(interval * 0.75f, interval * 1.25f);
		
		readResolve();
	}
	
	// same as vanilla  except also resets interval length
	@Override
	protected Object readResolve() {
		if (patrolBattlesLost == null) {
			float patrolStrengthCheckInterval = Global.getSettings().getFloat("economyIntervalnGameDays");
			float min = patrolStrengthCheckInterval - Math.min(patrolStrengthCheckInterval * 0.5f, 2f);
			float max = patrolStrengthCheckInterval + Math.min(patrolStrengthCheckInterval * 0.5f, 2f);
			patrolBattlesLost = new RollingAverageTracker(min, max, Misc.getEconomyRollingAverageFactor());
		}
		
		float interval = Global.getSettings().getFloat("averagePatrolSpawnInterval");
		if (tracker == null) 
			tracker = new IntervalUtil(interval * 0.75f, interval * 1.25f);
		else tracker.setInterval(interval * 0.75f, interval * 1.25f);
		
		return this;
	}
	
	// same as vanilla except sizing checks faction config
	@Override
	public void advance(float amount) {
		//if (true) return;
		float days = Global.getSector().getClock().convertToDays(amount);
		
		patrolBattlesLost.advance(days);
		
		float losses = patrolBattlesLost.getAverage();
		
		//tracker.advance(days);
		//log.info("Average patrol losses for market [" + market.getName() + "]: " + losses);
		float lossMod = (float)Math.min(losses/2, 4);
		tracker.advance(days * Math.max(1f, lossMod));
		if (!tracker.intervalElapsed()) return;
		
		if (market.hasCondition(Conditions.DECIVILIZED)) return;
		
		String factionId = market.getFactionId();
		float sizeMult = 1;
		
		if (market.getFaction().getCustom().optBoolean(Factions.CUSTOM_NO_PATROLS)) 
		{
			ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
			if (factionConfig == null || !factionConfig.spawnPatrols) return;

			sizeMult = factionConfig.patrolSizeMult;
			if (sizeMult <= 0) return;
		}
		
		// player currently invading this market; don't spawn patrols from it
		if (market.getId().equals(Global.getSector().getCharacterData().getMemoryWithoutUpdate().getString("$invasionTarget")))
		{
			//Global.getSector().getCampaignUI().addMessage(Global.getSector().getCharacterData().getMemoryWithoutUpdate().getString("$invasionTarget"));
			return;
		}
		
		
		List<PatrolFleetData> remove = new ArrayList<PatrolFleetData>();
		for (PatrolFleetData data : activePatrols) {
			if (data.fleet.getContainingLocation() == null ||
				!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) {
				remove.add(data);
				log.info("Cleaning up orphaned patrol [" + data.fleet.getNameWithFaction() + "] for market [" + market.getName() + "]");
			}
		}
		activePatrols.removeAll(remove);
		
		//maxPatrols = Math.max(1, market.getSize() - 3) + (int) (market.getStabilityValue() * 0.5f);
		//float losses = patrolBattlesLost.getAverage();
		
		maxPatrols = (int) (Math.max(1, market.getSize() - 3) * (market.getStabilityValue() / 10f)) + 
					(int) Math.max(0, Math.min(losses, 5));
		if (maxPatrols < 1) maxPatrols = 1;
		
		boolean hasStationOrSpaceport = market.hasCondition(Conditions.ORBITAL_STATION) || market.hasCondition(Conditions.SPACEPORT);
		if (market.hasCondition(Conditions.MILITARY_BASE)) {
			maxPatrols += 1;
			if (hasStationOrSpaceport) maxPatrols++;
		}
		if (hasStationOrSpaceport) maxPatrols++;
		
		
		log.debug("");
		log.debug("Checking whether to spawn patrol for market [" + market.getName() + "]");
		if (activePatrols.size() < maxPatrols) {
			log.info(activePatrols.size() + " out of a maximum " + maxPatrols + " patrols in play for market [" + market.getName() + "]");

			WeightedRandomPicker<PatrolType> picker = new WeightedRandomPicker<PatrolType>();
			picker.add(PatrolType.FAST, 
					Math.max(1, maxPatrols - getCount(PatrolType.COMBAT, PatrolType.HEAVY)));
			picker.add(PatrolType.COMBAT, 
					Math.max(1, maxPatrols - getCount(PatrolType.FAST, PatrolType.HEAVY) + market.getSize()) + losses * 0.5f);
			
			if (market.getSize() >= 5) {
				picker.add(PatrolType.HEAVY, 
						Math.max(1, maxPatrols - getCount(PatrolType.FAST, PatrolType.COMBAT) + market.getSize() - 5) + losses);
			}
			
			
			PatrolType type = picker.pick();
			
			float combat = 0f;
			float tanker = 0f;
			float freighter = 0f;
			String fleetType = FleetTypes.PATROL_SMALL;
			switch (type) {
			case FAST:
				fleetType = FleetTypes.PATROL_SMALL;
				combat = Math.round(3f + (float) Math.random() * 2f);
				combat += Math.min(5f, losses * 2f);
				break;
			case COMBAT:
				fleetType = FleetTypes.PATROL_MEDIUM;
				combat = Math.round(6f + (float) Math.random() * 3f);
				combat += Math.min(15f, losses * 4f);
				
				tanker = Math.round((float) Math.random());
				break;
			case HEAVY:
				fleetType = FleetTypes.PATROL_LARGE;
				combat = Math.round(10f + (float) Math.random() * 5f);
				combat += Math.min(25f, losses * 6f);
				
				tanker = 2f;
				freighter = 2f;
				break;
			}
			combat *= 1f + (market.getStabilityValue() / 20f);
			combat *= sizeMult;			
			
			//combat += Math.min(30f, losses * 3f);

			CampaignFleetAPI fleet = FleetFactoryV2.createFleet(new FleetParams(
					null,
					market, 
					market.getFactionId(),
					null, // fleet's faction, if different from above, which is also used for source market picking
					fleetType,
					combat, // combatPts
					freighter, // freighterPts 
					tanker, // tankerPts
					0f, // transportPts
					0f, // linerPts
					0f, // civilianPts 
					0f, // utilityPts
					0f, // qualityBonus
					-1f, // qualityOverride
					1f + Math.min(1f, losses / 12.5f),	//1f + Math.min(1f, losses / 10f), // officer num mult
					0 + (int) (losses * 0.75f)	// 0 + (int) losses // officer level bonus
					));
			if (fleet == null) return;
			
			SectorEntityToken entity = market.getPrimaryEntity();
			entity.getContainingLocation().addEntity(fleet);
			fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
			
			PatrolFleetData data = new PatrolFleetData(fleet, type);
			data.startingFleetPoints = fleet.getFleetPoints();
			data.sourceMarket = market;
			activePatrols.add(data);
			
			PatrolAssignmentAI ai = new PatrolAssignmentAI(fleet, data);
			fleet.addScript(ai);
			
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

//			if ((type == PatrolType.FAST && (float) Math.random() > 0.25f) ||
//					(type == PatrolType.COMBAT && (float) Math.random() > 0.5f)) {
			
			if (type == PatrolType.FAST || type == PatrolType.COMBAT) {
				fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR, true);
			}
			
			fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
			
			switch (type) {
			case FAST:
				fleet.getCommander().setRankId(Ranks.SPACE_LIEUTENANT);
				break;
			case COMBAT:
				fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
				break;
			case HEAVY:
				fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
				break;
			}
			
			log.info("Spawned patrol fleet [" + fleet.getNameWithFaction() + "] from market " + market.getName());
		} else {
			log.debug("Maximum number of " + maxPatrols + " patrols already in play for market [" + market.getName() + "]");
		}
	}
	
	// same as vanilla
	protected int getCount(PatrolType ... types) {
		int count = 0;
		for (PatrolType type : types) {
			for (PatrolFleetData data : activePatrols) {
				if (data.type == type) count++;
			}
		}
		return count;
	}
	
	// same as vanilla
	@Override
	public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
		super.reportFleetDespawned(fleet, reason, param);
		
		for (PatrolFleetData data : activePatrols) {
			if (data.fleet == fleet) {
				activePatrols.remove(data);
				break;
			}
		}
	}

	// same as vanilla
	@Override
	public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		
		boolean playerWon = battle.isPlayerSide(battle.getSideFor(primaryWinner));
		boolean playerLost = battle.isPlayerSide(battle.getOtherSideFor(primaryWinner));
		if (primaryWinner.isInOrNearSystem(market.getStarSystem())) {
			// losing to pirates doesn't trigger patrol strength going up; don't want pirates wiped out
			if (primaryWinner.getFaction().getId().equals(Factions.PIRATES)) return;
			if (primaryWinner.getFaction().getId().equals(Factions.LUDDIC_PATH)) return;
			
			for (CampaignFleetAPI loser : battle.getOtherSideSnapshotFor(primaryWinner)) {
				if (loser.getFaction() == market.getFaction()) {
					if (playerWon) {
						patrolBattlesLost.add(1);
					} else {
						//patrolBattlesLost.add(1);
					}
				} else if (primaryWinner.getFaction() == market.getFaction()) {
					// winning vs pirates doesn't trigger strength getting smaller, might happen too easily				
					if (loser.getFaction().getId().equals(Factions.PIRATES)) return;
					if (loser.getFaction().getId().equals(Factions.LUDDIC_PATH)) return;
					if (playerLost) {
						patrolBattlesLost.sub(1);
					} else {
						//patrolBattlesLost.sub(1);
					}
				}
			}
		}
	}
}
