package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;

@Deprecated
public class Nex_HegemonyInspectionManager extends HegemonyInspectionManager {
	
	// Hegemony won't inspect if player is allied
	@Override
	protected void checkInspection() {
		
		if (!SectorManager.isFactionAlive(Factions.HEGEMONY))
			return;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!playerFactionId.equals(Factions.HEGEMONY) && AllianceManager.areFactionsAllied(playerFactionId, Factions.HEGEMONY))
			return;
		
		super.checkInspection();
	}
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		
		// kill the inspection event if source market is hostile to hegemony
		if (intel != null && !intel.isEnding() && !intel.isEnded() 
				&& intel.getCurrentStage() <= 1
				&& !intel.getFrom().getFactionId().equals(Factions.HEGEMONY)) 
		{
			intel.forceFail(true);
		}
	}
	
	// Difference from vanilla: target player faction markets, not player-owned markets
	@Override
	public void createInspection(Integer fpOverride) {
		
		MarketAPI target = null;
		float max = 0f;
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.getFaction().isPlayerFaction()) {
				float curr = getAICoreUseValue(market);
				if (curr > max) {
					target = market;
					max = curr;
				}
			}
		}
		
		if (target != null && max > 0) {
			WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(random);
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
				if (market.getFactionId().equals(Factions.HEGEMONY)) {
					if (market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY)) {
						picker.add(market, market.getSize());
					}
				}
			}	
			MarketAPI from = picker.pick();
			if (from == null) return;
			
			float fp = 50 + threshold * 0.5f;
			//fp = 500;
			if (fpOverride != null) {
				fp = fpOverride;
			}
			intel = new HegemonyInspectionIntel(from, target, fp);
			if (intel.isDone()) {
				intel = null;
				return;
			}
		} else {
			return;
		}
		
		numAttempts++;
		suspicion = 0f;
		threshold *= 2f;
		if (threshold > MAX_THRESHOLD) {
			threshold = MAX_THRESHOLD;
		}
		
		if (intel != null && NexConfig.autoResistAIInspections 
				&& intel.getOrders() != HegemonyInspectionIntel.AntiInspectionOrders.RESIST) 
		{
			intel.setOrders(HegemonyInspectionIntel.AntiInspectionOrders.RESIST);
			intel.sendUpdateIfPlayerHasIntel(null, false);
		}
	}
	
	// runcode exerelin.campaign.intel.Nex_HegemonyInspectionManager.getInstance().createInspection();
}
