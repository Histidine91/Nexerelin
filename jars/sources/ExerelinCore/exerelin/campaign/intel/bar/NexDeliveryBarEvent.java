package exerelin.campaign.intel.bar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEvent;
import static com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEvent.KEY_FAILED_RECENTLY;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.List;

public class NexDeliveryBarEvent extends DeliveryBarEvent {
		
	public void recalcScore(DestinationData data, boolean forPrice) {
		float score = 0;
		float distScore = forPrice ? data.distLY : 20 - Math.abs(data.distLY - 20);
		//Global.getLogger(this.getClass()).info("Distance to " + data.dest.getName() 
		//		+ " is " + data.distLY + "; score: " + distScore);
		score += distScore;

		if (data.fromHasPA) score += 10f;
		if (data.fromHasCells) score += 5f;
		if (data.hasPA) score += 10f;
		if (data.hasCells) score += 5f;

		data.score = score;
	}
	
	// Same as vanilla, but with modified score, and don't impose minimum distance
	// Also weight is only score^2 instead of score^3
	// And don't go to derelict/Remnant planets
	@Override
	protected void computeData(MarketAPI market) {
		
		data = null;
		destination = null;;
		reward = 0;
		duration = 0;
		faction = null;
		quantity = 0;
		commodity = null;
		
		List<CommodityOnMarketAPI> commodities = new ArrayList<CommodityOnMarketAPI>();
		for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
			if (com.isNonEcon()) continue;
			if (com.isMeta()) continue;
			if (com.isPersonnel()) continue;
			if (com.isIllegal()) continue;
			
			if (com.getAvailable() <= 0) continue;
			if (com.getMaxSupply() <= 0) continue;
			
			commodities.add(com);
		}
		
		List<DestinationData> potential = new ArrayList<DestinationData>();
		
		float maxScore = 0;
		float maxDist = 0;
		for (MarketAPI other : Global.getSector().getEconomy().getMarketsCopy()) {
			if (other == market) continue;
			if (other.isHidden()) continue;
			if (other.getFactionId().equals(Factions.DERELICT)) continue;
			
			if (other.getEconGroup() == null && market.getEconGroup() != null) continue;
			if (other.getEconGroup() != null && !other.getEconGroup().equals(market.getEconGroup())) continue;
			
			if (other.getStarSystem() == null) continue;
			
			//WeightedRandomPicker<T>
			for (CommodityOnMarketAPI com : commodities) {
				//CommodityOnMarketAPI otherCom = other.getCommodityData(com.getId());
				CommodityOnMarketAPI otherCom = other.getCommodityData(com.getDemandClass());
				if (otherCom.getMaxDemand() <= 0) continue;
				
				DestinationData data = new DestinationData(market, other, com, otherCom);
				// MODIFIED
				recalcScore(data, false);
				
				if (data.illegal) continue;
				if (data.score > maxScore) {
					maxScore = data.score;
				}
				if (data.distLY > maxDist) {
					maxDist = data.distLY;
				}
				potential.add(data);
			}
		}
		if (maxDist > 10) maxDist = 10;
		
		WeightedRandomPicker<DestinationData> picker = new WeightedRandomPicker<DestinationData>(random);
		for (int i = 0; i < potential.size(); i++) {
			DestinationData d = potential.get(i);
			// MODIFIED
			if (d.score > maxScore * 0.5f) {
				picker.add(d, d.score * d.score);
			}
		}
		
//		Collections.sort(potential, new Comparator<DestinationData>() {
//			public int compare(DestinationData o1, DestinationData o2) {
//				return (int) Math.signum(o2.score - o1.score);
//			}
//		});
//		
//		
//		WeightedRandomPicker<DestinationData> picker = new WeightedRandomPicker<DestinationData>(random);
//		for (int i = 0; i < potential.size() && i < 5; i++) {
//			DestinationData d = potential.get(i);
//			picker.add(d, d.score * d.score * d.score);
//		}
		
		DestinationData pick = picker.pick();
		
		if (pick == null) return;
		
		destination = pick.dest;
		duration = pick.distLY * 5 + 50;
		duration = (int)duration / 10 * 10;
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		quantity = (int) cargo.getMaxCapacity();
		// don't want mission at market to update quantity as player changes their fleet up
		if (playerCargoCap == 0) {
			playerCargoCap = quantity;
		} else {
			quantity = playerCargoCap;
		}
		
		if (pick.com.isFuel()) quantity = (int) cargo.getMaxFuel();
		
		quantity *= 0.5f + 0.25f * random.nextFloat();
		
		// somewhat less of the more valuable stuff
		quantity *= Math.min(1f, 200f / pick.comFrom.getCommodity().getBasePrice());
		
		int limit = (int) (pick.comFrom.getAvailable() * pick.comFrom.getCommodity().getEconUnit());
		limit *= 0.75f + 0.5f * random.nextFloat();
		//if (quantity > 5000) quantity = 5000;
		if (quantity > limit) quantity = limit;
		
		
		if (quantity > 10000) quantity = quantity / 1000 * 1000;
		else if (quantity > 100) quantity = quantity / 10 * 10;
		else if (quantity > 10) quantity = quantity / 10 * 10;
		
		if (quantity < 10) quantity = 10;
		
		
		//float base = pick.com.getMarket().getSupplyPrice(pick.com.getId(), 1, true);
		float base = pick.comFrom.getMarket().getSupplyPrice(pick.comFrom.getId(), 1, true);
		
		if (quantity * base < 4000) {
			base = Math.min(100, 4000 / quantity);
		}
		
//		float minBase = 100;
//		if (quantity > 500) {
//			minBase = 50;
//		}
		float minBase = 100f - 50f * Math.min(1f, quantity / 500f);
		minBase = (base + minBase) * 0.75f;
		
		if (base < minBase) base = minBase;
		
		//float extra = 2000;
		
		// MODIFIED
		recalcScore(pick, true);
		
		float mult = pick.score / 30f;
		//if (market.isPlayerOwned() && mult > 2f) mult = 2f;
		//if (market.isPlayerOwned()) mult *= 0.75f;
		
		if (mult < 0.75f) mult = 0.75f;
		//if (mult > 2) mult = 2;
		reward = (int) (base * mult * quantity);
		
//		float minPerUnit = 50;
//		if (reward / quantity < minPerUnit) {
//			reward = (int) (minPerUnit * quantity);
//		}
		
		reward = reward / 1000 * 1000;
		if (reward < 4000) reward = 4000;
		
		
		if (Global.getSector().getMemoryWithoutUpdate().getBoolean(KEY_FAILED_RECENTLY)) {
			escrow = (int) (quantity * pick.comFrom.getCommodity().getBasePrice());
		}
		
		
		if (market.getFaction() == pick.dest.getFaction()) {
			faction = market.getFaction();
		} else {
			faction = Global.getSector().getFaction(Factions.INDEPENDENT);
			if (faction == null) faction = market.getFaction();
		}
		
		commodity = pick.comFrom.getId();
		
		data = pick;
	}
}
