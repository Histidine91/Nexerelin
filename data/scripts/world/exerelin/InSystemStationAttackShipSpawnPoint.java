package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import data.scripts.world.exerelin.diplomacy.DiplomacyRecord;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFleet;

import java.awt.*;

@SuppressWarnings("unchecked")
public class InSystemStationAttackShipSpawnPoint extends BaseSpawnPoint
{
	StationRecord stationTarget;
	String fleetOwningFactionId;
	CampaignFleetAPI theFleet;
	boolean boarding = false;
    long lastTimeCheck;

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
        boarding = false;
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

	    DiplomacyRecord diplomacyRecord = SectorManager.getCurrentSectorManager().getDiplomacyManager().getRecordForFaction(fleetOwningFactionId);
	    if (diplomacyRecord.isAtWar())
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 3, 5, fleetOwningFactionId, getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, fleetOwningFactionId, getSector());

		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, 1, 0.8f, false, ExerelinUtils.getCrewXPLevelForFaction(this.fleetOwningFactionId)))
		{
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.2f, 0.8f, ExerelinUtils.getCrewXPLevelForFaction(this.fleetOwningFactionId));
            ExerelinUtilsFleet.sortByHullSize(fleet);

			theFleet = fleet;

            if(((StarSystemAPI)stationTarget.getStationToken().getContainingLocation()).getName().equalsIgnoreCase(((StarSystemAPI)getAnchor().getContainingLocation()).getName()) || FactionDirector.getFactionDirectorForFactionId(this.fleetOwningFactionId).getTargetResupplyEntityToken() == null)
                fleet.setPreferredResupplyLocation(getAnchor());
            else
                fleet.setPreferredResupplyLocation(FactionDirector.getFactionDirectorForFactionId(this.fleetOwningFactionId).getTargetResupplyEntityToken());

            fleet.setName("Boarding Fleet");

			setFleetAssignments(fleet);

            this.getFleets().add(fleet);

            //getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(getAnchor(), 0, 0, fleet));

			//return fleet;
            return null;
		}
		else
			return null;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(stationTarget != null && ExerelinUtils.isValidBoardingFleet(fleet, true))
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3, createArrivedScript());
            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, stationTarget.getStationToken(), 10);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, stationTarget.getStationToken(), 10);

            if(((StarSystemAPI)stationTarget.getStationToken().getContainingLocation()).getName().equalsIgnoreCase(((StarSystemAPI)getAnchor().getContainingLocation()).getName()) || FactionDirector.getFactionDirectorForFactionId(fleet.getFaction().getId()).getTargetResupplyEntityToken() == null)
                fleet.setPreferredResupplyLocation(getAnchor());
            else
                fleet.setPreferredResupplyLocation(FactionDirector.getFactionDirectorForFactionId(fleet.getFaction().getId()).getTargetResupplyEntityToken());
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
                        boarding = false;
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
						return;
					}
					else if(!boarding && stationTarget.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					{
						System.out.println("Player owned " + stationTarget.getStationToken().getFullName() + " being boarded by " + Global.getSector().getFaction(fleetOwningFactionId).getDisplayName());
						Global.getSector().addMessage(stationTarget.getStationToken().getFullName() + " is being boarded by " + Global.getSector().getFaction(fleetOwningFactionId).getDisplayName(), Color.magenta);
					}
				}

                if(!boarding)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    boarding = true;
                }

                if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) >= 1)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    if(ExerelinUtils.boardStationAttempt(theFleet, stationTarget.getStationToken(), false, true))
                        boarding = false;
                    else
                        setFleetAssignments(theFleet);
                }
                else
                    setFleetAssignments(theFleet);

			}
		};
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(stationTarget != null && stationTarget.getOwner() != null && stationTarget.getOwner().getFactionId().equalsIgnoreCase(theFleet.getFaction().getId()))
				{
					// If we already own it deliver resources (as if we took it over), defend and despawn
					CargoAPI cargo = stationTarget.getStationToken().getCargo();
					cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR,  80);
					cargo.addFuel(80);
					cargo.addMarines(40);
					cargo.addSupplies(320);
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidBoardingFlagships(), true, false);
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidTroopTransportShips(), false, false);
                    ExerelinUtils.resetFleetCargoToDefaults(theFleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleetOwningFactionId));
				}
				else if(stationTarget == null || (stationTarget.getOwner() != null && stationTarget.getOwner().getGameRelationship(theFleet.getFaction().getId()) >= 0))
				{
					// If neutral/ally owns station, go home
					theFleet.clearAssignments();
                    boarding = false;
					theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
				}
				else
				{
					// Else, take over station
					stationTarget.setOwner(theFleet.getFaction().getId(), true, true);
					stationTarget.clearCargo();
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidTroopTransportShips(), false, false);
                    ExerelinUtils.resetFleetCargoToDefaults(theFleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(theFleet.getFaction().getId()));
				}
			}
		};
	}
}






