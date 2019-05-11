package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;

@Deprecated
public class Nex_OpenMarketPlugin extends OpenMarketPlugin {
	
	// same as vanilla except sells more weapons/fighters
	// TODO: not same anymore
	/*
	public void updateCargoPrePlayerInteraction() {
		float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
		addAndRemoveStockpiledResources(seconds, false, true, true);
		sinceLastCargoUpdate = 0f;

		if (okToUpdateShipsAndWeapons()) {
			sinceSWUpdate = 0f;
	
			pruneWeapons(0f);
			addWeapons(7, 10, 0, market.getFactionId());
			addFighters(3, 5, 0, market.getFactionId());
			
			
			getCargo().getMothballedShips().clear();
			
			addShips(market.getFactionId(),
					20f, // combat
					20f, // freighter 
					0f, // tanker
					10f, // transport
					10f, // liner
					5f, // utilityPts
					null, // qualityOverride
					0f, // qualityMod
					FactionAPI.ShipPickMode.IMPORTED,
					null);
			
			addShips(market.getFactionId(),
					30f, // combat
					0f, // freighter 
					0f, // tanker
					0f, // transport
					0f, // liner
					0f, // utilityPts
					null, // qualityOverride
					-0.5f, // qualityMod
					null,
					null);
			
			float tankers = 20f;
			CommodityOnMarketAPI com = market.getCommodityData(Commodities.FUEL);
			tankers += com.getMaxSupply() * 3f;
			if (tankers > 40) tankers = 40;
			//tankers = 40;
			addShips(market.getFactionId(),
					0f, // combat
					0f, // freighter 
					tankers, // tanker
					0, // transport
					0f, // liner
					0f, // utilityPts
					null, // qualityOverride
					0f, // qualityMod
					FactionAPI.ShipPickMode.PRIORITY_THEN_ALL,
					null);
			
			
			addHullMods(1, 1 + itemGenRandom.nextInt(3));
		}
		
		getCargo().sort();
	}
	*/
}
