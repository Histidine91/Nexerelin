package exerelin;

import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import exerelin.commandQueue.CommandQueue;
import exerelin.fleets.ExerelinFleetBase;
import exerelin.utilities.ExerelinConfig;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * OUT OF DATE, use exerelin.campaign.SectorManager instead!.
 */
@SuppressWarnings("unchecked")
public class SectorManager
{
    private SectorEventManager sectorEventManager;
	private SystemManager[] systemManagers;
	private DiplomacyManager diplomacyManager;
    private FactionDirector[] factionDirectors;

	private String[] factionsPossibleInSector;
	private boolean playerFreeTransfer;
	private boolean respawnFactions;
	private int respawnWaitDays;
	private int maxFactions;
    private int maxSystemSize;
    private boolean sectorPrePopulated = false;
    private boolean sectorPartiallyPopulated = false;

	private String playerFactionId;
    private String playerStartShipVariant;

	private boolean builtOmnifactoryAndStorage;
	private boolean buildOmnifactory;
	private boolean playerMovedToSpawnLocation;
    private boolean saboteurPerkTriggered = false;
    private boolean eliteShipPerkTriggered = false;

    private long lastFactionSpawnTime;
    private SectorEntityToken lastInteractionToken;
    private long lastInteractionTime;

    private CommandQueue commandQueue;

    private int currentUpdateStationStep = 0;

    private List<ExerelinFleetBase> playerOrderedFleets;

    private Boolean initialCheckComplete = false;

	public SectorManager()
	{
        // Empty Constructor
	}

    public void setupSectorManager(SectorAPI sector)
    {
        sectorEventManager = new SectorEventManager();

        // Setup system managers for each system in the sector
        systemManagers = new SystemManager[sector.getStarSystems().size()];
        for(int i = 0; i < sector.getStarSystems().size(); i++)
            systemManagers[i] = new SystemManager((StarSystemAPI)sector.getStarSystems().get(i));

        // Setup a diplomacy manager for the sector
        diplomacyManager = new DiplomacyManager();

        // Setup a list for referencing player-ordered fleets
        playerOrderedFleets = new ArrayList<ExerelinFleetBase>();
    }

    public void setupFactionDirectors()
    {
        // Setup a director for each faction
        factionDirectors = new FactionDirector[factionsPossibleInSector.length];
        for(int i = 0; i < factionsPossibleInSector.length; i++)
            factionDirectors[i] = new FactionDirector(factionsPossibleInSector[i], null);
    }

	public SystemManager getSystemManager(String systemName)
	{
		for(int i = 0; i < systemManagers.length; i++)
		{
			if(systemManagers[i].getStarSystemName().equalsIgnoreCase(systemName))
				return systemManagers[i];
		}

		return null;
	}

	public SystemManager getSystemManager(StarSystemAPI starSystemAPI)
	{
		return getSystemManager(starSystemAPI.getName());
	}

    public FactionDirector[] getFactionDirectors()
    {
        return this.factionDirectors;
    }

    public FactionDirector getFactionDirector(String factionId)
    {
        for(int i = 0; i < factionDirectors.length; i++)
        {
            if(factionDirectors[i].getFactionId().equalsIgnoreCase(factionId))
                return factionDirectors[i];
        }

        return null;
    }

	public DiplomacyManager getDiplomacyManager()
	{
		return diplomacyManager;
	}

    public void updateStationTargets()
    {
        // Update station targets (1 station per system per day)
        for(int j = 0; j < this.systemManagers.length; j++)
        {
            SystemStationManager systemStationManager =  this.systemManagers[j].getSystemStationManager();
            systemStationManager.deriveStationTargets();
        }
    }

	public void updateStationFleets()
	{
		// Manage stations (1 station per system per day)
		for(int j = 0; j < this.systemManagers.length; j++)
		{
			SystemStationManager systemStationManager =  this.systemManagers[j].getSystemStationManager();
			systemStationManager.updateStationFleets();
		}
	}

    public void updateAllStationResources()
    {
        SystemManager[] systemManagers = SectorManager.getCurrentSectorManager().getSystemManagers();
        for(int j = 0; j < systemManagers.length; j++)
        {
            SystemStationManager systemStationManager =  systemManagers[j].getSystemStationManager();
            for(int i = 0; i < systemStationManager.getStationRecords().length; i++)
            {
                systemStationManager.getStationRecords()[i].increaseResources();
            }
        }
    }

    public void updateStationResources(int stepSize)
    {
        if(currentUpdateStationStep == stepSize)
            currentUpdateStationStep = 0;

        // Upate 1/28th of stations per call
        int numSystems = SectorManager.getCurrentSectorManager().getSystemManagers().length;
        for(int i = 0; i < numSystems; i++)
        {
            SystemStationManager systemStationManager = SectorManager.getCurrentSectorManager().getSystemManagers()[i].getSystemStationManager();
            int numStations = systemStationManager.getStationRecords().length;

            double numberToUpdate = numStations/(double)stepSize;
            int startPoint = systemStationManager.getNextIncreaseResourceStationRecord();

            numberToUpdate = numberToUpdate*((double)(currentUpdateStationStep+1));
            numberToUpdate = numberToUpdate - startPoint;

            //System.out.println("numStations: " + numStations + ", stepSize: " + stepSize + ", Number to update: " + numberToUpdate + ", startpoint: " + startPoint);

            if(numberToUpdate < 1)
                numberToUpdate = 0;

            int numToUpdate = (int)numberToUpdate;

            for(int j = 0; j < numToUpdate; j++)
                systemStationManager.updateStationResources();
        }

        currentUpdateStationStep++;
    }

	public void runEvents()
	{
        // Manage sector events
        sectorEventManager.runEvents();

		// Manage system events
		for(int j = 0; j < this.systemManagers.length; j++)
		{
			this.systemManagers[j].getSystemEventManager().runEvents();
		}
	}

    public void checkPlayerHasWon()
    {
        // Check if player is playing as their own faction
        if(SectorManager.getCurrentSectorManager().isPlayerInPlayerFaction())
            return;

        String[] factionsInSector = SectorManager.getCurrentSectorManager().getFactionsInSector();

        if(factionsInSector.length == 1 && factionsInSector[0].equalsIgnoreCase(this.playerFactionId))
        {
            // Player has won
            Global.getSector().getCampaignUI().addMessage("Your faction has conquered sector Exerelin!");
            Global.getSector().getCampaignUI().addMessage("You have won!");
        }
    }

	public void checkPlayerHasLost()
	{
        // Check if player is playing as their own faction
        if(SectorManager.getCurrentSectorManager().isPlayerInPlayerFaction())
           return;

        // Check if player fleet is a boarding fleet
        if(ExerelinUtils.isValidBoardingFleet(Global.getSector().getPlayerFleet(),true))
            return;

		// Check that player has a station or a station attack fleet
		for(int k = 0; k < this.systemManagers.length; k++)
		{
			StarSystemAPI system = this.systemManagers[k].getStarSystemAPI();

			String[] factionsInSystem = SectorManager.getCurrentSectorManager().getFactionsInSector();
			if(factionsInSystem.length == 0)
				continue;

			for(int j = 0; j < factionsInSystem.length; j = j + 1)
			{
				if(factionsInSystem[j].equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
					return;
			}

			List fleets = system.getFleets();
			for(int i = 0; i < fleets.size(); i = i + 1)
			{
				CampaignFleetAPI fleet = (CampaignFleetAPI)fleets.get(i);
				if(fleet.getFaction().getId() == this.playerFactionId && ExerelinUtils.isValidBoardingFleet(fleet, true))
					return;
			}
		}

        // Player doesn't have a boarding fleet, their faction doesn't and they have no station
        // So lose condition
        Global.getSector().getCampaignUI().addMessage("Your faction has been scattered to the depths of space...");
        Global.getSector().getCampaignUI().addMessage("You have lost...");
	}

	public void payPlayerWages()
	{
		// Pay wages if needed
		if(ExerelinConfig.playerBaseSalary > 0 && !this.getPlayerFactionId().equalsIgnoreCase("player"))
		{
			if(this.getDiplomacyManager().getRecordForFaction(this.playerFactionId).isAtWar())
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(ExerelinConfig.playerBaseSalary*2);
			else
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(ExerelinConfig.playerBaseSalary);
		}
	}

	public void doEveryFrameChecks()
    {
        SectorAPI sector = Global.getSector();

		// Build OmniFactory and Storage if not been built and players faction has a station
		if(!this.getPlayerFactionId().equalsIgnoreCase("player") && !this.builtOmnifactoryAndStorage && !sector.getPlayerFleet().isInHyperspace())
		{
			if(SectorManager.getCurrentSectorManager().isFactionInSector(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
			{
                StarSystemAPI system = null;
                for(int i = 0; i < Global.getSector().getStarSystems().size(); i++)
                {
                    system = (StarSystemAPI)Global.getSector().getStarSystems().get(i);
                    if(SystemManager.getSystemManagerForAPI(system).isFactionInSystem(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                        break;
                }

				SectorEntityToken playerStation = ExerelinUtils.getRandomStationInSystemForFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId(), system);
                SectorEntityToken planet = playerStation.getOrbit().getFocus();

                if(!this.playerFreeTransfer)
                    new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "storage").spawnFleet();

                if(ExerelinConfig.randomOmnifactoryLocation)
                {
                    // Choose a random location for the omnifactory
                    system = (StarSystemAPI)Global.getSector().getStarSystems().get(ExerelinUtils.getRandomInRange(0, Global.getSector().getStarSystems().size() - 1));
                    planet = (SectorEntityToken)system.getPlanets().get(ExerelinUtils.getRandomInRange(0, system.getPlanets().size() - 1));
                }

                if(this.buildOmnifactory)
                    new OutSystemStationConstructionFleet(sector,  system,  system, "neutral", planet, "omnifac").spawnFleet();

                this.builtOmnifactoryAndStorage = true;
			}
		}

		// Set player fleet to appropriate faction
		if(!this.getPlayerFactionId().equalsIgnoreCase("player") && sector.getPlayerFleet().getFaction().getId().equalsIgnoreCase("player"))
		{
            System.out.println("Re-assigning player");
			sector.getPlayerFleet().setFaction(this.getPlayerFactionId());

			if(playerMovedToSpawnLocation)
			{
				// Not initial fleet so set player fleet to have their starting frigate
                sector.getPlayerFleet().getFleetData().removeFleetMember(((FleetMemberAPI)sector.getPlayerFleet().getFleetData().getMembersListCopy().get(0)));
                sector.getPlayerFleet().getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, this.playerStartShipVariant));

                // Move them to one of their stations
                SectorEntityToken station = null;
                if(this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem() != null)
                    station = ExerelinUtils.getRandomStationInSystemForFaction(this.getPlayerFactionId(), this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem());

                if(station != null)
                {
                    //this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem().addEntity(sector.getPlayerFleet());
                    //sector.setCurrentLocation(this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem());
                    //sector.getPlayerFleet().setLocation(station.getLocation().getX(),  station.getLocation().getY());
                    //sector.doHyperspaceTransition(sector.getPlayerFleet(), null, new JumpPointAPI.JumpDestination(station, ""));
                }

                System.out.println("Reset player fleet");
			}
            else
            {
                // Initial fleet in sector so move to one of players factions stations
                SectorEntityToken station = null;
                if(this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem() != null)
                    station = ExerelinUtils.getRandomStationInSystemForFaction(this.getPlayerFactionId(), this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem());

                if(station != null)
                {
                    //this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem().addEntity(sector.getPlayerFleet());
                    sector.getHyperspace().addEntity(sector.getPlayerFleet());
                    //sector.setCurrentLocation(this.getFactionDirector(this.getPlayerFactionId()).getHomeSystem());
                    //sector.getPlayerFleet().setLocation(station.getLocation().getX(),  station.getLocation().getY());
                    //sector.doHyperspaceTransition(sector.getPlayerFleet(), null, new JumpPointAPI.JumpDestination(station, ""));
                }

                playerMovedToSpawnLocation = true;
                System.out.println("Set player initial fleet");

                // Start of game, player fleet is last to be spawned so set last faction spawn time as this
                this.lastFactionSpawnTime = Global.getSector().getClock().getTimestamp();
            }
            ExerelinUtils.resetFleetCargoToDefaults(Global.getSector().getPlayerFleet(), 0.1f, 0.0f, CargoAPI.CrewXPLevel.GREEN);
		}

        if(!initialCheckComplete)
        {
            // Reset cargo
            ExerelinUtils.resetFleetCargoToDefaults(Global.getSector().getPlayerFleet(), 0.1f, 0.0f, CargoAPI.CrewXPLevel.GREEN);

            // Show welcome dialog
            //Global.getSector().getCampaignUI().showInteractionDialog(new ExerelinWelcomeDialogPlugin(), null);

            initialCheckComplete = true;
        }

        // Player clicked target so save for usage
        // Don't reset for asteroids
        if(Global.getSector().getPlayerFleet().getInteractionTarget() != null
                && (this.lastInteractionToken == null || !Global.getSector().getPlayerFleet().getInteractionTarget().getFullName().equalsIgnoreCase(this.lastInteractionToken.getFullName())))
        {
            this.setLastInteractionToken(Global.getSector().getPlayerFleet().getInteractionTarget());
        }
        else if(this.lastInteractionToken != null
                && MathUtils.getDistance(Global.getSector().getPlayerFleet().getLocation(), this.lastInteractionToken.getLocation()) > 1
                && !(this.lastInteractionToken instanceof AsteroidAPI))
        {
            this.lastInteractionToken = null;
        }

        // If we have a saved target and the players fleet is close enough, stick player fleet to target
        // Don't issue move override for asteroids
        if(this.lastInteractionToken != null
                && MathUtils.getDistance(Global.getSector().getPlayerFleet().getLocation(), this.lastInteractionToken.getLocation()) < 2
                && !(this.lastInteractionToken instanceof AsteroidAPI))
        {
            Global.getSector().getPlayerFleet().setMoveDestination(lastInteractionToken.getLocation().getX(), lastInteractionToken.getLocation().getY());
        }
	}

	public boolean getRespawnFactions()
	{
		return respawnFactions;
	}

	public int getMaxSystemSize()
	{
		return maxSystemSize;
	}

	public String[] getFactionsPossibleInSector()
	{
		return factionsPossibleInSector;
	}

	public int getMaxFactions()
	{
		return maxFactions;
	}

	public void setPlayerFreeTransfer(Boolean inPlayerFreeTransfer)
	{
		playerFreeTransfer = inPlayerFreeTransfer;
	}

	public void setRespawnFactions(Boolean inRespawnFactions)
	{
		respawnFactions = inRespawnFactions;
	}

	public void setMaxFactions(int inMaxFactions)
	{
		maxFactions = inMaxFactions;
	}

	public void setPlayerFactionId(String inPlayerFactionId)
	{
		playerFactionId = inPlayerFactionId;
	}

	public void setFactionsPossibleInSector(String[] inFactionsPossibleInSector)
	{
		factionsPossibleInSector = inFactionsPossibleInSector;
	}

	public void setRespawnWaitDays(int inRespawnWaitDays)
	{
		respawnWaitDays = inRespawnWaitDays;
	}

    public int getRespawnWaitDays()
    {
        return this.respawnWaitDays;
    }

    public long getLastFactionSpawnTime()
    {
        return lastFactionSpawnTime;
    }

    public void setLastFactionSpawnTime(long timestamp)
    {
        lastFactionSpawnTime = timestamp;
    }

	public void setBuildOmnifactory(Boolean inBuildOmnifactory)
	{
		buildOmnifactory = inBuildOmnifactory;
	}

	public void setMaxSystemSize(int inMaxSystemSize)
	{
		maxSystemSize = inMaxSystemSize;
	}

	public static SectorManager getCurrentSectorManager()
	{
		return (SectorManager)Global.getSector().getPersistentData().get("SectorManager");
	}

	public SystemManager[] getSystemManagers()
	{
		return this.systemManagers;
	}

	public int getNumSystems()
	{
		return this.systemManagers.length;
	}

	public Boolean isFactionInSector(String factionId)
	{
		for(int i = 0; i < this.systemManagers.length; i++)
		{
			if(this.systemManagers[i].isFactionInSystem(factionId))
				return true;
		}

		return false;
	}


	public String getLeadingFaction()
	{
        float tempLeaderOwnership = 0.0f;
        String tempLeaderId = "";

        for(int i = 0; i < this.getFactionsInSector().length; i++)
        {
            String factionId = this.getFactionsInSector()[i];

            if(this.isFactionInSector(factionId))
            {
                if(this.getSectorOwnership(factionId) > tempLeaderOwnership)
                {
                    tempLeaderId = factionId;
                    tempLeaderOwnership = this.getSectorOwnership(factionId);
                }
            }
        }

        return tempLeaderId;
	}

	public String getLosingFaction()
	{
        float tempLoserOwnership = 1200.0f;
        String tempLoserId = "";

        for(int i = 0; i < this.getFactionsInSector().length; i++)
        {
            String factionId = this.getFactionsInSector()[i];
            //System.out.println("Checking : " + factionId + ", inSector: " + this.isFactionInSector(factionId) + ", ownership: " + this.getSectorOwnership(factionId));

            if(this.isFactionInSector(factionId))
            {
                if(this.getSectorOwnership(factionId) < tempLoserOwnership)
                {
                    tempLoserId = factionId;
                    tempLoserOwnership = this.getSectorOwnership(factionId);
                }
            }
        }

        return tempLoserId;
	}

	public float getSectorOwnership(String factionId)
	{
		float ownership = 0f;
		for(int i = 0; i < this.systemManagers.length; i++)
		{
			ownership = ownership + systemManagers[i].getSystemOwnership(factionId);
		}

		return ownership/this.systemManagers.length;
	}

	public String[] getFactionsInSector()
	{
		ArrayList foundFactions = new ArrayList(factionsPossibleInSector.length);

		for(int i = 0; i < this.systemManagers.length; i++)
		{
			String[] factionsInSystem = this.systemManagers[i].getFactionsInSystem();
			for(int j = 0; j < factionsInSystem.length; j++)
			{
				if(!foundFactions.contains(factionsInSystem[j]))
					foundFactions.add(factionsInSystem[j]);
			}
		}

		return (String[])foundFactions.toArray( new String[foundFactions.size()] );
	}



    public void setPlayerStartShipVariant(String variantId)
    {
        this.playerStartShipVariant = variantId;
    }

    public void setCommandQueue(CommandQueue commandQueue)
    {
        this.commandQueue = commandQueue;
    }

    public CommandQueue getCommandQueue()
    {
        return this.commandQueue;
    }

    public SectorEntityToken getLastInteractionToken()
    {
        return lastInteractionToken;
    }

    public void setLastInteractionToken(SectorEntityToken token)
    {
        this.lastInteractionToken = token;
        this.lastInteractionTime = Global.getSector().getClock().getTimestamp();
    }

    public long getLastInteractionTime()
    {
        return  lastInteractionTime;
    }

    public String getPlayerFactionId()
    {
        return playerFactionId;
    }

    public SectorEventManager getSectorEventManager()
    {
        return this.sectorEventManager;
    }

    public void addPlayerCommandedFleet(ExerelinFleetBase exerelinFleetBase)
    {
        this.playerOrderedFleets.add(exerelinFleetBase);
    }

    public List<ExerelinFleetBase> getPlayerOrderedFleets()
    {
        return this.playerOrderedFleets;
    }

    public void updatePlayerCommandedFleets()
    {
        ExerelinFleetBase toRemove = null;
        for(ExerelinFleetBase exerelinFleetBase : this.playerOrderedFleets)
        {
            if(exerelinFleetBase.fleet == null || !exerelinFleetBase.fleet.isAlive())
            {
                toRemove = exerelinFleetBase;
                break;
            }
        }

        if(toRemove != null)
            this.playerOrderedFleets.remove(toRemove);
    }

    public boolean getEliteShipPerkTriggered()
    {
        return eliteShipPerkTriggered;
    }

    public boolean getSaboteurPerkTriggered()
    {
        return saboteurPerkTriggered;
    }

    public void setEliteShipPerkTriggered(boolean triggered)
    {
        this.eliteShipPerkTriggered = triggered;
    }

    public void setSaboteurPerkTriggered(boolean triggered)
    {
        this.saboteurPerkTriggered = triggered;
    }

    public boolean isSectorPrePopulated()
    {
        return sectorPrePopulated;
    }

    public void setSectorPrePopulated(boolean value)
    {
        sectorPrePopulated = value;
    }

    public boolean isSectorPartiallyPopulated()
    {
        if(!sectorPrePopulated)
            return false;
        else
            return sectorPartiallyPopulated;
    }

    public void setSectorPartiallyPopulated(boolean value)
    {
        sectorPartiallyPopulated = value;
    }

    public boolean isPlayerInPlayerFaction()
    {
        return this.playerFactionId.equalsIgnoreCase("player");
    }
}
