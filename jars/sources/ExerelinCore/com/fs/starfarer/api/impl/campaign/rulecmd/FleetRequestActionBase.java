package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public abstract class FleetRequestActionBase extends BaseCommandPlugin {
        
        protected static final float MARINE_DEFENDER_MULT = 1.25f; 
    
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
                if ( market.getFaction() == invader || market.getFactionId().equals("player_npc"))
                {
                    float dist = Misc.getDistance(market.getLocationInHyperspace(), targetMarketLoc);
                    if (dist < 5000.0F) {
                        dist = 5000.0F;
                    }
                    float weight = 20000.0F / dist;
                    if (market.hasCondition("military_base")) {
                        weight *= 2.0F;
                    }
                    if (market.hasCondition("orbital_station")) {
                        weight *= 1.25F;
                    }
                    if (market.hasCondition("spaceport")) {
                        weight *= 1.5F;
                    }
                    if (market.hasCondition("headquarters")) {
                        weight *= 1.5F;
                    }
                    if (market.hasCondition("regional_capital")) {
                        weight *= 1.25F;
                    }
                    weight *= market.getSize() * market.getStabilityValue();
                    sourcePicker.add(market, weight);
                }
            }
            MarketAPI originMarket = (MarketAPI)sourcePicker.pick();
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
