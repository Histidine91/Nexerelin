package data.scripts.world.exerelin;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.BaseSpawnPoint;
import java.util.List;

@SuppressWarnings("unchecked")
public class AsteroidMiningFleetSpawnPoint extends BaseSpawnPoint
{
	CampaignFleetAPI theFleet;
	String owningFactionId;
	SectorEntityToken targetAsteroid;
	Boolean returningHome = false;
	float fleetCargoCapacity = 0;

	public AsteroidMiningFleetSpawnPoint(SectorAPI sector, LocationAPI location,
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

	public void setTargetAsteroid(SectorEntityToken asteroid)
	{
		//if(asteroid != null && targetAsteroid != null && targetAsteroid.getFullName().equalsIgnoreCase(asteroid.getFullName()))
		//	return; // No change

		targetAsteroid = asteroid;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinAsteroidMiningFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(targetAsteroid == null)
			return null;

		// Create fleet
		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);
		fleet.setName("Asteroid Mining Fleet");
		theFleet = fleet;
		getLocation().spawnFleet(getAnchor(), 0, 0, fleet);

		fleet.setPreferredResupplyLocation(getAnchor());

		returningHome = false;
		fleetCargoCapacity = getFleetCargoCapacity(fleet);
		setFleetAssignments(fleet);

		this.getFleets().add(fleet);
		return fleet;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(targetAsteroid != null && ExerelinUtils.isValidMiningFleet(fleet))
		{
			if(!returningHome)
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetAsteroid, 1000, createTestTargetScript());
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
				if(!returningHome && theFleet.getCargo().getSupplies() < fleetCargoCapacity)
				{
					// Mine more supplies
					theFleet.getCargo().addSupplies(1);
				}
				else if(!returningHome)
				{
					// Head for home
					returningHome = true;
				}
				else if (theFleet.getCargo().getSupplies() > 20)
				{
					// Reached home so unload
					theFleet.getCargo().removeSupplies(1);
					getAnchor().getCargo().addSupplies(2);
				}
				else
				{
					// Head out to mine again
					returningHome = false;
					fleetCargoCapacity = getFleetCargoCapacity(theFleet);
				}

				setFleetAssignments(theFleet);
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






