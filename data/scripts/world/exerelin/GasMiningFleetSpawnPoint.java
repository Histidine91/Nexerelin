package data.scripts.world.exerelin;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.BaseSpawnPoint;

import java.util.List;

@SuppressWarnings("unchecked")
public class GasMiningFleetSpawnPoint extends BaseSpawnPoint
{
	CampaignFleetAPI theFleet;
	String owningFactionId;
	SectorEntityToken targetPlanet;
	Boolean returningHome = false;
	float fleetFuelCapacity = 0;
	int miningPower = 0;
	boolean validFleet = false;

	public GasMiningFleetSpawnPoint(SectorAPI sector, LocationAPI location,
									  float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	public void setFaction(String factionId)
	{
		owningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	public void setTargetPlanet(SectorEntityToken planet)
	{
		if(planet != null && targetPlanet != null && targetPlanet.getFullName().equalsIgnoreCase(planet.getFullName()))
			return; // No change

		targetPlanet = planet;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinGasMiningFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(targetPlanet == null)
			return null;

		// Create fleet
		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);
		fleet.setName("Gas Mining Fleet");
		theFleet = fleet;
		getLocation().spawnFleet(getAnchor(), 0,0, fleet);

		fleet.setPreferredResupplyLocation(getAnchor());

		returningHome = false;
		fleetFuelCapacity = getFleetFuelCapacity(fleet);
		validFleet = true;
		miningPower = ExerelinUtils.getMiningPower(fleet);
		setFleetAssignments(fleet);

		this.getFleets().add(fleet);
		return fleet;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();

		if(targetPlanet != null && miningPower != 0)
		{
			if(!returningHome)
				//fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, getLocation().createToken(targetPlanet.getLocation().getX() + ExerelinUtils.getRandomInRange(-100,100), targetPlanet.getLocation().getY() + ExerelinUtils.getRandomInRange(-100,100)), 1000, createTestTargetScript());
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetPlanet, 1000, createTestTargetScript());
			else
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, getAnchor(), 1000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
		}
		else
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 1000);
		}
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(!returningHome && theFleet.getCargo().getFuel() < fleetFuelCapacity)
				{
					// Mine more gas
					theFleet.getCargo().addFuel(miningPower);
				}
				else if(!returningHome)
				{
					// Head for home
					returningHome = true;
					validFleet = ExerelinUtils.isValidMiningFleet(theFleet);
					miningPower = ExerelinUtils.getMiningPower(theFleet);
				}
				else if (theFleet.getCargo().getFuel() > 0)
				{
					// Reached home so unload
					theFleet.getCargo().removeFuel(1);
					getAnchor().getCargo().addFuel(ExerelinUtils.getRandomInRange(0,1));
				}
				else
				{
					// Head out to mine again
					returningHome = false;
					fleetFuelCapacity = getFleetFuelCapacity(theFleet);
					validFleet = ExerelinUtils.isValidMiningFleet(theFleet);
					miningPower = ExerelinUtils.getMiningPower(theFleet);
				}

				setFleetAssignments(theFleet);
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






