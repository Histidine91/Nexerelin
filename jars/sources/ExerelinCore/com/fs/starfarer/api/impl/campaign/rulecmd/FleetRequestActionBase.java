package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.util.vector.Vector2f;

public abstract class FleetRequestActionBase extends BaseCommandPlugin {
		
		protected static final float MARINE_DEFENDER_MULT = 1.25f;
		
		protected static final Map<String, Float> INDUSTRY_WEIGHTS = new HashMap<>();
		
		protected static void put(String ind, Float weight)
		{
			INDUSTRY_WEIGHTS.put(ind, weight);
		}
		
		static {
			INDUSTRY_WEIGHTS.put(Industries.PATROLHQ, 1.5f);
			INDUSTRY_WEIGHTS.put(Industries.MILITARYBASE, 2f);
			INDUSTRY_WEIGHTS.put(Industries.HIGHCOMMAND, 2.5f);
			INDUSTRY_WEIGHTS.put(Industries.MEGAPORT, 2f);
		}
		
	
		protected float getMoneyRequiredForFleet(int fp, int numMarines)
		{
			return numMarines * ExerelinConfig.fleetRequestCostPerMarine + fp * ExerelinConfig.fleetRequestCostPerFP;
		}
		
		protected MarketAPI getSourceMarketForInvasion(FactionAPI invader, MarketAPI targetMarket)
		{
			WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
			List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
			Vector2f targetMarketLoc = targetMarket.getLocationInHyperspace();
			
			for (MarketAPI market : markets) {
				if ( market.getFaction() == invader || market.getFactionId().equals(ExerelinConstants.PLAYER_NPC_ID))
				{
					if (!market.hasSpaceport())
						continue;
					
					float dist = Misc.getDistance(market.getLocationInHyperspace(), targetMarketLoc);
					if (dist < 5000.0F) {
						dist = 5000.0F;
					}
					float weight = 20000.0F / dist;
					
					// industry modifiers to weight
					for (Map.Entry<String, Float> tmp : INDUSTRY_WEIGHTS.entrySet())
					{
						if (market.hasIndustry(tmp.getKey()))
							weight *= tmp.getValue();
					}
					
					weight *= market.getSize() * market.getStabilityValue();
					sourcePicker.add(market, weight);
				}
			}
			MarketAPI originMarket = sourcePicker.pick();
			return originMarket;
		}
	
		protected boolean payForInvasion(int fp, int marines)
		{
			float moneyRequired = getMoneyRequiredForFleet(fp, marines);
			CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
			if (playerCargo.getCredits().get() >= moneyRequired)
			{
				playerCargo.getCredits().subtract(moneyRequired);
				return true;
			}
			return false;
		}
}
