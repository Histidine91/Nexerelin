package data.scripts.exerelin.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.ExerelinUtils;
import data.scripts.world.exerelin.ExerelinUtilsPlayer;
import data.scripts.world.exerelin.SystemManager;
import data.scripts.world.exerelin.utilities.ExerelinConfig;

import java.util.List;

@SuppressWarnings("unchecked")
public class AsteroidMiningFleet extends ExerelinFleetBase
{
	SectorEntityToken targetAsteroid;
    SectorEntityToken anchor;
	Boolean returningHome = false;
	float fleetCargoCapacity = 0;
	int miningPower = 0;
	boolean validFleet = false;
    long lastTimeCheck;

	public AsteroidMiningFleet()
	{

	}

    public CampaignFleetAPI createFleet(String faction, SectorEntityToken anchor)
    {
        this.fleet = Global.getSector().createFleet(faction, "exerelinAsteroidMiningFleet");

        fleet.setName(ExerelinConfig.getExerelinFactionConfig(faction).asteroidMiningFleetName);
        fleet.getCommander().setPersonality("cautious");
        fleet.setPreferredResupplyLocation(anchor);
        this.anchor = anchor;

        returningHome = false;
        fleetCargoCapacity = getFleetCargoCapacity(fleet);
        validFleet = true;
        miningPower = ExerelinUtils.getMiningPower(fleet);
        setFleetAssignments();
        lastTimeCheck = Global.getSector().getClock().getTimestamp();

        ((StarSystemAPI)anchor.getContainingLocation()).spawnFleet(anchor, 0, 0, fleet);

        return fleet;
    }

	public void setTargetAsteroid(SectorEntityToken asteroid)
	{
		targetAsteroid = asteroid;
	    setFleetAssignments();
	}

	public void setFleetAssignments()
	{
		fleet.clearAssignments();

        float resourceMultiplier = 1.0f;
        if(fleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
            resourceMultiplier = ExerelinUtilsPlayer.getPlayerStationResourceLimitMultiplier();

		if(targetAsteroid != null && validFleet && miningPower != 0 && this.anchor.getCargo().getSupplies() < 8000*resourceMultiplier)
		{
			if(!returningHome)
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetAsteroid, 1000, createTestTargetScript(this.fleet));
			else
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, this.anchor, 1000, createTestTargetScript(this.fleet));
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.anchor, 1000);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.anchor, 1000);
		}
	}

	private Script createTestTargetScript(CampaignFleetAPI inFleet) {
        final CampaignFleetAPI fleet = inFleet;

		return new Script() {
			public void run() {
                if(ExerelinData.getInstance().getSectorManager() == null)
                    return; //TODO - Remove when scripts do not run before after game load

				if(!returningHome && fleet.getCargo().getQuantity(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource) < fleetCargoCapacity)
				{
					// Mine more supplies
                    if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) > 1)
                    {
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
                        fleet.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource, ExerelinConfig.miningAmountPerDayPerMiner * 2);
                    }
				}
				else if(!returningHome)
				{
					// Head for home
					returningHome = true;
					validFleet = ExerelinUtils.isValidMiningFleet(fleet);
					miningPower = ExerelinUtils.getMiningPower(fleet);
				}
				else if (fleet.getCargo().getQuantity(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource) > 0)
				{
					// Reached home so unload
                    if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) > 1)
                    {
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
                        fleet.getCargo().removeItems(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource, ExerelinConfig.miningAmountPerDayPerMiner * 4);
                        anchor.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource, ExerelinConfig.miningAmountPerDayPerMiner * 4 * SystemManager.getSystemManagerForAPI((StarSystemAPI) fleet.getContainingLocation()).getSystemStationManager().getStationRecordForToken(anchor).getEfficiency(false));
                    }
				}
				else
				{
					// Head out to mine again
					returningHome = false;
					fleetCargoCapacity = getFleetCargoCapacity(fleet);
					validFleet = ExerelinUtils.isValidMiningFleet(fleet);
					miningPower = ExerelinUtils.getMiningPower(fleet);
				}

				setFleetAssignments();
			}
		};
	}

	private float getFleetCargoCapacity(CampaignFleetAPI fleet)
	{
		float capacity = 0;
		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			capacity = capacity + fmAPI.getCargoCapacity();
		}
		return (int)(capacity*1.3);
	}
}






