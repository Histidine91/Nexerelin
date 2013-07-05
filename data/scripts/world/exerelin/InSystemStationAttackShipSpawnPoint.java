package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.BaseSpawnPoint;

import java.awt.*;
import java.util.*;

@SuppressWarnings("unchecked")
public class InSystemStationAttackShipSpawnPoint extends BaseSpawnPoint
{
	StationRecord stationTarget;
	String fleetOwningFactionId;
	CampaignFleetAPI theFleet;
	boolean boarding = false;

	public InSystemStationAttackShipSpawnPoint(SectorAPI sector, LocationAPI location,
											   float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	public void setTarget(StationRecord target)
	{
		if(target != null && stationTarget != null && stationTarget.getStationToken().getFullName().equalsIgnoreCase(target.getStationToken().getFullName()))
			return;

		stationTarget = target;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	public void setFaction(String factionId)
	{
		fleetOwningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	@Override
	public CampaignFleetAPI spawnFleet()
	{
		String type = "exerelinInSystemStationAttackFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(stationTarget == null)
			return null;

		boarding = false;

		CampaignFleetAPI fleet = getSector().createFleet(fleetOwningFactionId, type);

		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, 1))
		{
			getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
			theFleet = fleet;
			fleet.setPreferredResupplyLocation(getAnchor());

			setFleetAssignments(fleet);

			this.getFleets().add(fleet);
			return fleet;
		}
		else
			return null;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(stationTarget != null)
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3, createArrivedScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, stationTarget.getStationToken(), 10);
		}
		else
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(stationTarget != null && stationTarget.getOwner() != null)
				{
					if(stationTarget.getOwner().getFactionId().equalsIgnoreCase(fleetOwningFactionId))
					{
						// If we own station then just go there
						return; // Will run arrived script
					}
					else if(stationTarget.getOwner().getGameRelationship(fleetOwningFactionId) >= 0)
					{
						// If neutral/ally owns station, head home (home station may reassign a target later)
						theFleet.clearAssignments();
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
						return;
					}
					else if(!boarding && stationTarget.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					{
						boarding = true;
						System.out.println("Player owned " + stationTarget.getStationToken().getFullName() + " being boarded by " + fleetOwningFactionId);
						Global.getSector().addMessage(stationTarget.getStationToken().getFullName() + " is being boarded by " + fleetOwningFactionId, Color.magenta);
					}
				}

				if(!boarding || ExerelinUtils.getRandomInRange(0, 7000) > 0)
				{
					boarding = true;
					setFleetAssignments(theFleet);
				}
				else
				{
					return; // Will run arrived script
				}
			}
		};
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(stationTarget.getOwner() != null && stationTarget.getOwner().getFactionId().equalsIgnoreCase(fleetOwningFactionId))
				{
					// If we already own it deliver resources and despawn
					CargoAPI cargo = stationTarget.getStationToken().getCargo();
					cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR,  80);
					cargo.addFuel(80);
					cargo.addMarines(40);
					cargo.addSupplies(360);
				}
				else if(stationTarget.getOwner() != null && stationTarget.getOwner().getGameRelationship(fleetOwningFactionId) >= 0)
				{
					// If neutral/ally owns station, mill around until new orders
					theFleet.clearAssignments();
					theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
				}
				else
				{
					// Take over station
					stationTarget.setOwner(theFleet.getFaction().getId(), true, true);
					stationTarget.clearCargo();
				}
			}
		};
	}
}






