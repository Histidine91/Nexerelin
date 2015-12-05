package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.FactionCommissionMissionEvent;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class ExerelinFactionCommissionMissionEvent extends FactionCommissionMissionEvent {
	
	protected boolean ended = false;
	
	// same as vanilla one except public
	public void endEvent() {
		ended = true;
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_FACTION);
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_EVENT);
	}
	
	@Override
	public boolean isDone() {
		return ended;
	}
	
	// same as vanilla one except public
	@Override
	public SectorEntityToken findMessageSender() {
		WeightedRandomPicker<MarketAPI> military = new WeightedRandomPicker<MarketAPI>();
		WeightedRandomPicker<MarketAPI> nonMilitary = new WeightedRandomPicker<MarketAPI>();
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.getFaction() != faction) continue;
			if (market.getPrimaryEntity() == null) continue;
			
			float dist = Misc.getDistanceToPlayerLY(market.getPrimaryEntity());
			float weight = Math.max(1f, 10f - dist);
			if (market.hasCondition(Conditions.HEADQUARTERS) ||
					market.hasCondition(Conditions.REGIONAL_CAPITAL) ||
					market.hasCondition(Conditions.MILITARY_BASE)) {
				military.add(market, weight);
			} else {
				nonMilitary.add(market, weight);
			}
		}
		
		MarketAPI pick = military.pick();
		if (pick == null) pick = nonMilitary.pick();
		
		if (pick != null) return pick.getPrimaryEntity();
		return Global.getSector().getPlayerFleet();
	}
}
