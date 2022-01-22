package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.cb.BaseCustomBountyCreator;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBStats;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetQuality;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetSize;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.OfficerNum;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.OfficerQuality;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.utilities.StringHelper;

public class Nex_CBHegInspector extends BaseCustomBountyCreator {
	
	public static boolean DEBUG_MODE = false;
	public static float EVENT_FREQ = 0.5f;
	public static float PAYOUT_MULT = 1.6f;
	
	@Override
	public float getFrequency(HubMissionWithBarEvent mission, int difficulty) {
		FactionAPI faction = mission.getPerson().getFaction();
		if (!DEBUG_MODE && faction.isAtWorst(Factions.HEGEMONY, RepLevel.NEUTRAL)) return 0f;
		if (DiplomacyTraits.hasTrait(faction.getId(), TraitIds.DISLIKES_AI) || DiplomacyTraits.hasTrait(faction.getId(), TraitIds.HATES_AI))
			return 0;
		
		float mult = 1;
		if (DiplomacyTraits.hasTrait(faction.getId(), TraitIds.LIKES_AI)) mult = 1.5f;
		return super.getFrequency(mission, difficulty) * EVENT_FREQ * mult;
	}
	
	public String getBountyNamePostfix(HubMissionWithBarEvent mission, CustomBountyData data) {
		return " - " + StringHelper.getString("nex_bounties", "bountyPostfix_hegInspector");
	}
	
	@Override
	public CustomBountyData createBounty(MarketAPI createdAt, HubMissionWithBarEvent mission, int difficulty, Object bountyStage) {
				
		CustomBountyData data = new CustomBountyData();
		data.difficulty = difficulty;
		
		mission.requireMarketFaction(Factions.HEGEMONY);
		mission.preferMarketIsMilitary();
		
		MarketAPI market = mission.pickMarket();
		if (market == null) return null;
		
		data.market = market;
		data.system = market.getStarSystem();
	
		FleetSize size = FleetSize.LARGE;
		FleetQuality quality = FleetQuality.DEFAULT;
		String type = FleetTypes.INSPECTION_FLEET;
		OfficerQuality oQuality = OfficerQuality.DEFAULT;
		OfficerNum oNum = OfficerNum.DEFAULT;
		
		if (difficulty <= 6) {
			// base values
		} else if (difficulty == 7) {
			size = FleetSize.VERY_LARGE;
			quality = FleetQuality.HIGHER;
		} else if (difficulty == 8) {
			size = FleetSize.VERY_LARGE;
			quality = FleetQuality.HIGHER;
			oQuality = OfficerQuality.HIGHER;
			oNum = OfficerNum.MORE;
		} else if (difficulty == 9) {
			size = FleetSize.HUGE;
			quality = FleetQuality.HIGHER;
			oQuality = OfficerQuality.HIGHER;
			oNum = OfficerNum.MORE;
		} else {// if (difficulty == 10) {
			size = FleetSize.HUGE;
			quality = FleetQuality.SMOD_1;
			oQuality = OfficerQuality.HIGHER;
			oNum = OfficerNum.MORE;
		}
		
		beginFleet(mission, data);
		mission.triggerCreateFleet(size, quality, Factions.HEGEMONY, type, data.system);
		mission.triggerSetFleetOfficers(oNum, oQuality);
		mission.triggerAutoAdjustFleetSize(size, size.next());
		mission.triggerPickLocationAroundEntity(market.getPrimaryEntity(), 100f);
		mission.triggerSpawnFleetAtPickedLocation(null, null);
		mission.triggerOrderFleetPatrol(market.getPrimaryEntity());
		mission.triggerSetFleetMemoryValue(MemFlags.MEMORY_KEY_SOURCE_MARKET, market.getId());
		data.fleet = createFleet(mission, data);
				
		if (data.fleet == null) return null;
		
		setRepChangesBasedOnDifficulty(data, difficulty);
		data.baseReward = CBStats.getBaseBounty(difficulty, PAYOUT_MULT, mission);
		
		
		return data;
	}
	
	@Override
	public float getBountyDays() {
		return super.getBountyDays()/2;
	}
	
	@Override
	public int getMaxDifficulty() {
		return super.getMaxDifficulty();
	}

	@Override
	public int getMinDifficulty() {
		return DEBUG_MODE ? 0 : 6;
	}
}






