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
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.ResponseFleetManager;
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
		
			fleet = ExerelinUtilsFleet.customCreateFleet(market.getFaction(), fleetParams);
			
			if (fleet == null)
				return false;
			
			fleet.setName(InvasionFleetManager.getFleetName("exerelinDefenceFleet", market.getFactionId(), fp));
		}
		else
		{
			float fp = ResponseFleetManager.getReserveSize(market);
		
			if (fp < ResponseFleetManager.MIN_FP_TO_SPAWN)
				return false;
		
			fleet = ResponseFleetManager.getManager().getResponseFleet(market, (int)fp);
		}
		if (fleet == null) return false;
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
		
		mem.set(defenderMemFlag, fleet, 3);
		mem.set("$hasDefenders", true, 0);
		
		return true;
	}
}
