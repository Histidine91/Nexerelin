package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

import java.awt.*;

public class EventTradeGuildConversion extends EventBase
{

	public EventTradeGuildConversion()
	{
		setType(this.getClass().getName());
	}

	public void callTradersForLastFaction(StarSystemAPI starSystemAPI)
	{
		// DEFAULTS
		FactionAPI traderFAPI = Global.getSector().getFaction("tradeguild");
		String tradingWithFaction = "";

		// Reset trader relationships with each faction
		String[] factions = ExerelinData.getInstance().getSectorManager().getFactionsPossibleInSector();
		for(int i = 0; i < factions.length; i = i + 1)
			traderFAPI.setRelationship(factions[i], 0);

		// Get a last faction and declare friend with them
		tradingWithFaction = ExerelinData.getInstance().getSectorManager().getSystemManager(starSystemAPI).getSystemStationManager().getFactionLoser();
		if(tradingWithFaction == null)
			return;

		traderFAPI.setRelationship(tradingWithFaction, 1);

		// Warn player
		if(tradingWithFaction.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			Global.getSector().addMessage("The Trade Guild have convinced a number of supply ships to join them and reroute to " + ExerelinData.getInstance().getPlayerFaction() + " stations!", Color.magenta);
		else
			Global.getSector().addMessage("The Trade Guild have convinced a number of supply ships to join them and reroute to " + tradingWithFaction + " stations!");

		System.out.println("EVENT: TradeGuild supporting " + tradingWithFaction);

		java.util.List fleets = starSystemAPI.getFleets();

		for(int i = 0; i < fleets.size(); i++)
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);

			if(fleet.getFaction().getId().equalsIgnoreCase(tradingWithFaction) || fleet.getFaction().getId().equalsIgnoreCase(traderFAPI.getId()))
				continue;

			// Supply fleets have a 50% chance to deflect
			if(ExerelinUtils.getRandomInRange(0, 1) == 0)
			{
				String fleetFullName = fleet.getFullName();

				if(!fleetFullName.contains("Supply"))
					continue; // Skip non-supply fleets

				String originalFaction = fleet.getFaction().getId();
				fleet.setFaction("tradeguild");

				String fleetName = originalFaction + " Defected Supply Fleet";

				fleet.setName(fleetName);

				CargoAPI fleetCargo = fleet.getCargo();

				fleetCargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
				fleetCargo.addCrew(CargoAPI.CrewXPLevel.ELITE, 1);
				fleetCargo.addCrew(CargoAPI.CrewXPLevel.VETERAN, 1);
				fleetCargo.addCrew(CargoAPI.CrewXPLevel.GREEN, 1);
				fleetCargo.addMarines(100);
				fleetCargo.addFuel(200);
				fleetCargo.addSupplies(800);

				fleet.clearAssignments();
				SectorEntityToken station = ExerelinUtils.getRandomStationForFaction(tradingWithFaction, starSystemAPI, Global.getSector());

				if(station != null)
					fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, station, 200);

				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, ExerelinUtils.getRandomOffMapPoint(starSystemAPI), 200);
			}
		}
	}
}






