package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

import java.awt.*;
import java.util.List;

@SuppressWarnings("unchecked")
public class EventOutSystemReinforcements
{
	SectorAPI sectorAPI;
	StarSystemAPI starSystemAPI;

	public EventOutSystemReinforcements(SectorAPI sector, StarSystemAPI system)
	{
		sectorAPI = sector;
		starSystemAPI = system;
	}

	public void callReinforcementFleets()
	{
		// DEFAULTS
		String type = "exerelinGenericFleet";
		String factionId = "";

		// Get faction to spawn reinforcements for
		int attempts = 0;
		String[] factions = ExerelinData.getInstance().systemManager.availableFactions;
		while(attempts < 10 && factionId.equalsIgnoreCase(""))
		{
			attempts = attempts + 1;
			factionId = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().systemManager.stationManager.getFactionLeader()))
				factionId = ""; // Can't be leader
			else if(!ExerelinData.getInstance().systemManager.diplomacyManager.getRecordForFaction(factionId).hasWarTargetInSystem(false))
				factionId = ""; // Faction must be at war
			else if(ExerelinData.getInstance().systemManager.stationManager.getNumStationsOwnedByFaction(factionId) == 0)
				factionId = ""; // Faction has to own a station
		}

		if(factionId.equalsIgnoreCase(""))
			return;

		// Warn player
		if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			Global.getSector().addMessage("A number of " + ExerelinData.getInstance().getPlayerFaction() + " out system reinforcements are arriving in Exerelin!", Color.magenta);
		else
			Global.getSector().addMessage("A number of " + factionId + " out system reinforcements are arriving in Exerelin!");

		System.out.println("EVENT: Out system reinforcements for " + factionId);

		// Get a spawn location
		SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(starSystemAPI);

		// Choose a station to reinforce
		StationRecord[] records = ExerelinData.getInstance().systemManager.stationManager.getStationRecords();
		SectorEntityToken defend = null;
		SectorEntityToken attack = null;
		for(int j = 0; j < records.length; j++)
		{
			if(records[j].getOwner() != null
					&& records[j].getOwner().getFactionId().equalsIgnoreCase(factionId)
					&& records[j].getTargetStationRecord() != null
					&& records[j].getNumAttacking() > 0)
			{
				defend = records[j].getStationToken();
				attack = records[j].getTargetStationRecord().getStationToken();
				break;
			}
		}

		if(attack == null || defend == null)
			return;

		List fleets = starSystemAPI.getFleets();
		for(int i = 0; i < fleets.size()/20; i++)
		{
			CampaignFleetAPI fleet;
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				fleet = sectorAPI.createFleet(ExerelinData.getInstance().getPlayerFaction(),  type);
			else
				fleet = sectorAPI.createFleet(factionId,  type);

			fleet.setName("Reinforcement Fleet");

			starSystemAPI.spawnFleet(token, ExerelinUtils.getRandomInRange(-100,100), ExerelinUtils.getRandomInRange(-10,10), fleet);

			if(ExerelinUtils.getRandomInRange(0,1) == 0)
				fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, attack, 45);
			else
				fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defend, 45);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, token, 45);
		}
	}
}






