package exerelin.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exerelin.ExerelinData;
import exerelin.ExerelinUtils;
import exerelin.ExerelinUtilsPlayer;
import exerelin.SystemManager;
import exerelin.utilities.ExerelinConfig;

import java.util.List;

@SuppressWarnings("unchecked")
public class GasMiningFleet extends ExerelinFleetBase
{
	SectorEntityToken targetPlanet;
    SectorEntityToken anchor;

	Boolean returningHome = false;
	float fleetFuelCapacity = 0;
	int miningPower = 0;
	boolean validFleet = false;
    long lastTimeCheck;

	public GasMiningFleet(String faction, SectorEntityToken anchor, SectorEntityToken targetPlanet)
	{
        this.anchor = anchor;
        this.targetPlanet = targetPlanet;

        // Create fleet
        fleet = Global.getSector().createFleet(faction, "exerelinGasMiningFleet");
        fleet.setName(ExerelinConfig.getExerelinFactionConfig(faction).gasMiningFleetName);
        fleet.getCommander().setPersonality("cautious");
        fleet.setPreferredResupplyLocation(anchor);

        returningHome = false;
        fleetFuelCapacity = getFleetFuelCapacity(fleet);
        validFleet = true;
        miningPower = ExerelinUtils.getMiningPower(fleet);
        setFleetAssignments();
        lastTimeCheck = Global.getSector().getClock().getTimestamp();

        ((StarSystemAPI)anchor.getContainingLocation()).spawnFleet(anchor, 0, 0, fleet);
	}

	public void setTargetPlanet(SectorEntityToken planet)
	{
		if(planet != null && targetPlanet != null && targetPlanet.getFullName().equalsIgnoreCase(planet.getFullName()))
			return; // No change

		targetPlanet = planet;
		setFleetAssignments();
	}

	public void setFleetAssignments()
	{
		fleet.clearAssignments();

        float resourceMultiplier = 1.0f;
        if(fleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
            resourceMultiplier = ExerelinUtilsPlayer.getPlayerStationResourceLimitMultiplier();

		if(targetPlanet != null && validFleet && miningPower != 0 && anchor.getCargo().getFuel() < 2000*resourceMultiplier)
		{
			if(!returningHome)
				//fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, getLocation().createToken(targetPlanet.getLocation().getX() + ExerelinUtils.getRandomInRange(-100,100), targetPlanet.getLocation().getY() + ExerelinUtils.getRandomInRange(-100,100)), 1000, createTestTargetScript());
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetPlanet, 1000, createTestTargetScript());
			else
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, anchor, 1000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
		}
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {

                if(ExerelinData.getInstance().getSectorManager() == null)
                    return; //TODO - Remove when scripts do not run before after game load

				if(!returningHome && fleet.getCargo().getFuel() < fleetFuelCapacity)
				{
					// Mine more gas
                    if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) > 1)
                    {
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
                        fleet.getCargo().addFuel(miningPower * 100);
                    }
				}
				else if(!returningHome)
				{
					// Head for home
					returningHome = true;
					validFleet = ExerelinUtils.isValidMiningFleet(fleet);
					miningPower = ExerelinUtils.getMiningPower(fleet);
				}
				else if (fleet.getCargo().getFuel() > 0)
				{
					// Reached home so unload
                    if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) > 1)
                    {
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
                        fleet.getCargo().removeFuel(200);
                        anchor.getCargo().addFuel(200 * SystemManager.getSystemManagerForAPI((StarSystemAPI) fleet.getContainingLocation()).getSystemStationManager().getStationRecordForToken(anchor).getEfficiency(false));
                    }
				}
				else
				{
					// Head out to mine again
					returningHome = false;
					fleetFuelCapacity = getFleetFuelCapacity(fleet);
					validFleet = ExerelinUtils.isValidMiningFleet(fleet);
					miningPower = ExerelinUtils.getMiningPower(fleet);
				}

				setFleetAssignments();
			}
		};
	}

	private float getFleetFuelCapacity(CampaignFleetAPI fleet)
	{
		float capacity = 0;
		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			capacity = capacity + fmAPI.getFuelCapacity();
		}
		return (int)(capacity*1.3);
	}
}






