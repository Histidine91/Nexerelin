package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.fleets.DS_FleetInjector;
import exerelin.campaign.fleets.DefenceStationManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.List;
import java.util.Map;

public class Nex_PrepareResponseFleet extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean isRaid = params.get(0).getBoolean(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		MemoryAPI mem = memoryMap.get(MemKeys.MARKET);
		String defenderMemFlag = isRaid ? "$nex_raidResponseFleet" : "$nex_invasionResponseFleet";
		String defenderDefeatedMemFlag = isRaid ? "$nex_raidResponseDefeated" : "$nex_invasionResponseDefeated";
		
		// already generated, let it keep for a while
		if (mem.contains(defenderMemFlag))
		{
			mem.set("$hasDefenders", true, 0);
			return true;
		}
		// recently defeated the defending fleet, don't spawn a new one for a while
		if (mem.contains(defenderDefeatedMemFlag))
		{
			mem.set("$hasDefenders", false, 0);
			return false;
		}
		// backstab sneak attack on non-hostile faction! no opposition
		if (market.getFaction().isAtWorst(Factions.PLAYER, RepLevel.SUSPICIOUS))
		{
			mem.set("$hasDefenders", false, 0);
			return false;
		}
		
		CampaignFleetAPI fleet = prepFleet(market, isRaid);
		
		// station can substitute for our regular defence fleet if we don't have one
		// (if the fleet does exist, station will join battle through the normal channels instead)
		if (fleet == null && !isRaid) {
			CampaignFleetAPI station = DefenceStationManager.getManager().getFleet(market);
			if (station != null && station.getBattle() == null)
				fleet = station;
		}		
		if (fleet == null) {
			mem.set("$hasDefenders", false, 0);
			return false;
		}
		
		mem.set(defenderMemFlag, fleet, 3);
		mem.set("$hasDefenders", true, 0);
		
		return true;
	}
	
	public static CampaignFleetAPI prepFleet(MarketAPI market, boolean isRaid)
	{
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		CampaignFleetAPI fleet = null;
		int playerFleetSize = ExerelinUtilsFleet.getFleetGenPoints(playerFleet);
		
		if (isRaid)
		{
			float fp = playerFleetSize * 1.25f;
			float maxFP = (float)Math.pow(market.getSize(), 2) * 1.25f;
			if (fp > maxFP) fp = maxFP;
			fp += ExerelinUtilsFleet.getPlayerLevelFPBonus() * 0.5f;
			
			FleetParams fleetParams = new FleetParams(null, market, market.getFactionId(), null, "exerelinDefenceFleet", 
				fp,		// combat
				0,		// freighters
				0,		// tankers
				0,		// personnel transports
				0,		// liners
				0,		// civilian
				0,		// utility
				0, 0, 1, 0);	// quality bonus, quality override, officer num mult, officer level bonus
			fleetParams.random = ResponseFleetManager.getManager().getRandom();
		
			fleet = ExerelinUtilsFleet.customCreateFleet(market.getFaction(), fleetParams);
			
			if (fleet == null)
				return null;
			
			fleet.setName(InvasionFleetManager.getFleetName("exerelinDefenceFleet", market.getFactionId(), fp));
		}
		else
		{
			float fp = ResponseFleetManager.getReserveSize(market);
			//float stationFpMod = DefenceStationManager.getManager().getDefenceFleetPenaltyFromStations(market);
		
			if ((fp) < ResponseFleetManager.MIN_FP_TO_SPAWN)
				return null;
			
			fleet = ResponseFleetManager.getManager().getResponseFleet(market, (int)(fp));
			if (fleet != null)
			{
				fleet.getMemoryWithoutUpdate().set("$nex_response_fp_cost", fp);
			}
		}
		if (fleet == null) return null;
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
		
		if (ExerelinModPlugin.HAVE_DYNASECTOR)
		{
			DS_FleetInjector.prepFleet(fleet);
			DS_FleetInjector.injectFleet(fleet);
			fleet.getMemoryWithoutUpdate().set("$dynasectorInjected", true);
		}
		
		return fleet;
	}
}
