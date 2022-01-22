package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFleet;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * Reserve fleet supporting stations in combat.
 */
public class ResponseFleetManager
{
	public static final String MEMORY_KEY_FLEET = "$nex_responseFleet";
	public static final float RESPONSE_FLEET_TTL = 21;
	
	public static Logger log = Global.getLogger(ResponseFleetManager.class);
		
	/**
	 * Generates a response fleet from the specified market. Does not add it to a battle on its own.
	 * @param market
	 * @return
	 */
	public static CampaignFleetAPI generateResponseFleet(MarketAPI market)
	{
		//float qf = origin.getShipQualityFactor();
		//qf = Math.max(qf, 0.7f);
		if (market.isHidden()) return null;
		
		String factionId = market.getFactionId();
		String fleetFactionId = factionId;
		NexFactionConfig factionConfig = NexConfig.getFactionConfig(factionId);
		NexFactionConfig fleetFactionConfig = null;
		
		if (factionConfig.factionIdForHqResponse != null && market.hasIndustry(Industries.HIGHCOMMAND))
		{
			fleetFactionId = factionConfig.factionIdForHqResponse;
			fleetFactionConfig = NexConfig.getFactionConfig(fleetFactionId);
		}
		
		String name = "";
		
		if (fleetFactionConfig != null)
			name = fleetFactionConfig.responseFleetName;
		else
			name = factionConfig.responseFleetName;
				
		int points = getPatrolCombatFP(market);
		if (points <= 0) return null;
		
		FleetParamsV3 fleetParams = new FleetParamsV3(market, "exerelinResponseFleet", 
				points, // combat
				0,	//maxFP*0.1f, // freighters
				0,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	//maxFP*0.1f,	// utility
				0.15f);	// quality mod
		
		CampaignFleetAPI fleet = NexUtilsFleet.customCreateFleet(Global.getSector().getFaction(fleetFactionId), fleetParams);
		if (fleet == null) return null;
		
		fleet.setFaction(factionId, true);
		fleet.setName(name);
		fleet.setAIMode(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
		if (market.getFaction().isHostileTo(Factions.PLAYER))
		{
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true, 5);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, 5);
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true, 5);
		}
		
		return fleet;
	}
	
	public static int getResponseFleetLevel(MarketAPI market) {
		int level = 0;
		for (Industry ind : market.getIndustries()) {
			if (ind.isDisrupted()) continue;
			if (ind.isBuilding()) continue;
			
			Set<String> tags = ind.getSpec().getTags();
			if (tags.contains(Industries.TAG_COMMAND)) {
				level = Math.max(level, 3);
			} else if (tags.contains(Industries.TAG_MILITARY)) {
				level = Math.max(level, 2);
			} else if (tags.contains(Industries.TAG_PATROL)) {
				level = Math.max(level, 1);
			}
		}
		return level;
	}
	
	// Same values as vanilla Military Base, but the random value is replaced with 1 (i.e. maximum size patrol)
	public static int getPatrolCombatFP(MarketAPI market) {
		float combat = 0;
		int level = getResponseFleetLevel(market);
		
		switch (level) {
		case 1:
			combat = Math.round(3f + 1 * 2f) * 5f;
			break;
		case 2:
			combat = Math.round(6f + 1 * 3f) * 5f;
			break;
		case 3:
			combat = Math.round(10f + 1 * 5f) * 5f;
			break;
		}
		combat *= 1 + NexConfig.getFactionConfig(market.getFactionId()).responseFleetSizeMod;
		combat *= NexConfig.responseFleetSizeMult;
		
		return (int) Math.round(combat);
	}
}