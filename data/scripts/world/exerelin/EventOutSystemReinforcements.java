package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

import java.awt.*;
import java.util.List;

@SuppressWarnings("unchecked")
public class EventOutSystemReinforcements extends EventBase
{

	public EventOutSystemReinforcements()
	{
		setType(this.getClass().getName());
	}

	public void callReinforcementFleets(StarSystemAPI starSystemAPI)
	{
		// DEFAULTS
		String type = "exerelinGenericFleet";
		String factionId = "";

		// Get faction to spawn reinforcements for
		int attempts = 0;
		String[] factions = ExerelinData.getInstance().getSectorManager().getFactionsPossibleInSector();
		while(attempts < 10 && factionId.equalsIgnoreCase(""))
		{
			attempts = attempts + 1;
			factionId = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLeader()))
				factionId = ""; // Can't be leader
			else if(!ExerelinData.getInstance().getSectorManager().getDiplomacyManager().getRecordForFaction(factionId).hasWarTargetInSystem(starSystemAPI, false))
				factionId = ""; // Faction must be at war with faction in system
			else if(ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getNumStationsOwnedByFaction(factionId) == 0)
				factionId = ""; // Faction has to own a station
		}

		if(factionId.equalsIgnoreCase(""))
			return;

		// Get a spawn location
		SectorEntityToken token = ExerelinUtils.getRandomOffMapPoint(starSystemAPI);

		// Choose a station to reinforce
		StationRecord[] records = SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemStationManager().getStationRecords();
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

        // Warn player
        if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
            Global.getSector().addMessage("A number of " + ExerelinData.getInstance().getPlayerFaction() + " out system reinforcements are arriving in Exerelin!", Color.magenta);
        else
            Global.getSector().addMessage("A number of " + factionId + " out system reinforcements are arriving in Exerelin!");

        System.out.println("EVENT: Out system reinforcements for " + factionId);

		List fleets = starSystemAPI.getFleets();
		for(int i = 0; i < Math.max(fleets.size()/20, 1); i++)
		{
			CampaignFleetAPI fleet;
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				fleet = Global.getSector().createFleet(ExerelinData.getInstance().getPlayerFaction(),  type);
			else
				fleet = Global.getSector().createFleet(factionId,  type);

            // If faction is last, add an elite ship to fleet (if possbile)
            if(factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getLosingFaction()))
                ExerelinUtils.addEliteShipToFleet(fleet);


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






