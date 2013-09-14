package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import data.characters.skills.scripts.ExerelinSkillData;

public class ExerelinPlayerFunctions {

    // -- SKILL FUNCTIONS --

    public static float getPlayerStationConvoyResourceMultipler()
    {
        float multiplier = 0.8f; // Player gets a slight penalty

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getAptitudeLevel("faction") > 0)
            multiplier = multiplier + (playerStatsAPI.getAptitudeLevel("faction") * ExerelinSkillData.FACTION_APTITUDE_RESOURCE_DELIVERY_INCREASE_PERCENTAGE / 100);

        System.out.println("Player station convoy resource multipler: " + multiplier);
        return  multiplier;
    }

    public static float getPlayerFleetCostMultiplier()
    {
        float multiplier = 1.0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("fleet_deployment") > 0)
            multiplier = multiplier - (playerStatsAPI.getSkillLevel("fleet_deployment") * ExerelinSkillData.FACTION_FLEETDEPLOYMENT_EFFECT_RESOURCECOST_REDUCTION_PERCENTAGE / 100);

        System.out.println("Player fleet cost multipler: " + multiplier);
        return multiplier * 0.7f; // multiply result by 0.7 due to all other fleets costing 0.7
    }

    public static float getPlayerFactionFleetEliteShipBonusChance()
    {
        float chance = 0.0f;
        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("fleet_deployment") == 10)
            chance = chance + ExerelinSkillData.FACTION_FLEETDEPLOYMENT_PERK_ELITE_SHIP_CHANCE_BONUS_PERCENTAGE / 100;

        System.out.println("Player elite ship chance: " + chance);
        return chance;
    }
}
