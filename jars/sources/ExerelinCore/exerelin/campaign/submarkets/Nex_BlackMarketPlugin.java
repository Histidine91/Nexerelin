package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;

@Deprecated
public class Nex_BlackMarketPlugin extends BlackMarketPlugin {
	
	// same as vanilla except sells more weapons/fighters
	// TODO: not same anymore
	/*
	@Override
	public void updateCargoPrePlayerInteraction() {
		float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
		addAndRemoveStockpiledResources(seconds, false, true, true);
		sinceLastCargoUpdate = 0f;

		
		if (okToUpdateShipsAndWeapons()) {
			sinceSWUpdate = 0f;
			float stability = market.getStabilityValue();
			
			pruneWeapons(0f);
			WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<String>();
			factionPicker.add(market.getFactionId(), 15f - stability);
			factionPicker.add(Factions.INDEPENDENT, 4f);
			factionPicker.add(submarket.getFaction().getId(), 6f);
			addWeapons(9, 13, 3, factionPicker);
			addFighters(4, 7, 3, factionPicker);
			
			float sMult = Math.max(0.1f, 1f - stability / 10f);
			getCargo().getMothballedShips().clear();
			float pOther = 0.1f;
			addShips(market.getFactionId(),
					40f * sMult, // combat
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // freighter 
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // tanker
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // transport
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // liner
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // utilityPts
					null,
					0f, // qualityMod
					null,
					null);
			FactionDoctrineAPI doctrineOverride = submarket.getFaction().getDoctrine().clone();
			doctrineOverride.setWarships(3);
			doctrineOverride.setPhaseShips(2);
			doctrineOverride.setCarriers(2);
			doctrineOverride.setCombatFreighterProbability(1f);
			doctrineOverride.setShipSize(5);
			addShips(submarket.getFaction().getId(),
					70f, // combat
					10f, // freighter 
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // tanker
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // transport
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // liner
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // utilityPts
					//0.8f,
					Math.min(1f, Misc.getShipQuality(market, market.getFactionId()) + 0.5f),
					0f, // qualityMod
					null,
					doctrineOverride);
			addShips(Factions.INDEPENDENT,
					15f + 15f * sMult, // combat
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // freighter 
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // tanker
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // transport
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // liner
					itemGenRandom.nextFloat() > pOther ? 0f : 10f, // utilityPts
					//0.8f,
					Math.min(1f, Misc.getShipQuality(market, market.getFactionId()) + 0.5f),
					0f, // qualityMod
					null,
					null);
			
			addHullMods(4, 1 + itemGenRandom.nextInt(3));
		}
		
		getCargo().sort();
	}
	*/
}
