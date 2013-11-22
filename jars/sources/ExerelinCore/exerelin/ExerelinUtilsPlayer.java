package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import exerelin.skills.scripts.ExerelinSkillData;

public class ExerelinUtilsPlayer {

    // -- SKILL FUNCTIONS -- //

    public static float getPlayerStationBaseEfficiency()
    {
        float baseEfficiency = 0.95f; // Player gets a slight penalty

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getAptitudeLevel("faction") > 0)
            baseEfficiency = baseEfficiency + (playerStatsAPI.getAptitudeLevel("faction") * ExerelinSkillData.FACTION_APTITUDE_STATION_EFFICIENCY_INCREASE_PERCENTAGE / 100);

        if(playerStatsAPI.getSkillLevel("station_industry") > 0)
            baseEfficiency = baseEfficiency + (playerStatsAPI.getAptitudeLevel("station_industry") * (ExerelinSkillData.FACTION_STATIONINDUSTRY_EFFECT_STATION_EFFICIENCY_BONUS_PERCENTAGE / 100));

        return  baseEfficiency;
    }

    public static float getPlayerFleetCostMultiplier()
    {
        float multiplier = 1.0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("fleet_deployment") > 0)
            multiplier = multiplier - (playerStatsAPI.getSkillLevel("fleet_deployment") * (ExerelinSkillData.FACTION_FLEETDEPLOYMENT_EFFECT_RESOURCECOST_REDUCTION_PERCENTAGE / 100));

        return multiplier;
    }

    public static float getPlayerFactionFleetEliteShipBonusChance()
    {
        float chance = 0.0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("fleet_deployment") >= 10)
            chance = chance + (ExerelinSkillData.FACTION_FLEETDEPLOYMENT_PERK_ELITE_SHIP_CHANCE_BONUS_PERCENTAGE / 100);

        return chance;
    }

    public static float getPlayerStationResourceLimitMultiplier()
    {
        float multiplier = 0.95f; // Slight penalty for players faction

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("station_industry") >= 5)
            multiplier = multiplier + (ExerelinSkillData.FACTION_STATIONINDUSTRY_PERK_STATION_RESOURCE_CAP_BONUS_PERCENTAGE / 100);

        return multiplier;
    }

    public static boolean getPlayerDeployExtraMiningFleets()
    {
        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        return (playerStatsAPI.getSkillLevel("station_industry") >= 10);
    }

    public static float getPlayerFactionFleetCrewExperienceBonus()
    {
        float bonus = 0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("fleet_crew_training") > 0)
            bonus = playerStatsAPI.getSkillLevel("fleet_crew_training") * (ExerelinSkillData.FACTION_FLEETCREWTRAINING_EFFECT_EXPERIENCE_BONUS_PERCENTAGE / 100);

        return bonus;
    }

    public static float getPlayerDiplomacyObjectCreationBonus()
    {
        float bonus = 0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("active_diplomacy") > 0)
            bonus = playerStatsAPI.getSkillLevel("active_diplomacy") * (ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_EFFECT_ITEM_AVAILABILITY_BONUS_PERECENTAGE / 100);

        return bonus;
    }

    public static boolean getPlayerSabateurAvailability()
    {
        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        return (playerStatsAPI.getSkillLevel("active_diplomacy") >= 3);
    }

    public static float getPlayerDiplomacyObjectReuseChance()
    {
        float chance = 0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("active_diplomacy") >= 10)
            chance = ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_PERK_ITEM_REUSE_CHANCE / 100;

        return chance;
    }

    public static float getPlayerDiplomacyRelationshipBonus()
    {
        float bonus = 0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("passive_diplomacy") > 0)
            bonus = playerStatsAPI.getSkillLevel("passive_diplomacy") * (ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_EFFECT_ITEM_AVAILABILITY_BONUS_PERECENTAGE / 100);

        return bonus;
    }

    public static float getPlayerDiplomacyBetrayalReducedChance()
    {
        float chance = 0f;

        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();
        if(playerStatsAPI.getSkillLevel("passive_diplomacy") >= 10)
            chance = ExerelinSkillData.FACTION_PASSIVEDIPLOMACY_PERK_ALLIANCE_BETRAYAL_REDUCTION_PERCENTAGE / 100;

        return chance;
    }
}
