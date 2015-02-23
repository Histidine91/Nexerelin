package exerelin;

import exerelin.utilities.ExerelinUtils;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.InteractionDialogImageVisual;

import org.lazywizard.lazylib.MathUtils;

import exerelin.fleets.*;
import exerelin.commandQueue.CommandAddCargo;
import exerelin.campaign.DiplomacyManager;
import exerelin.diplomacy.DiplomacyRecord;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsCargo;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMessaging;

public class StationRecord
{
    public static enum StationFleetStance
    {
        BALANCED,
        DEFENSE,
        ATTACK,
        PATROL
    }

	private SectorEntityToken stationToken;
	private CargoAPI stationCargo;
	private String planetType;
	private DiplomacyRecord owningFaction;
	private Float efficiency = 1f;

	private int numStationsTargeting;
    private Boolean isBeingBoarded;
    private long lastBoardAttemptTime;
	private StationRecord targetStationRecord;
    private StationRecord takeoverStationRecord;
	private StationRecord defendStationRecord;
    private StationRecord convoyStationRecord;
	private SectorEntityToken targetAsteroid;
	private SectorEntityToken targetGasGiant;

	// Fleets
	private WarFleet warFleet;
	private WarFleet warFleet1;
	private WarFleet warFleet2;
	private StationAttackFleet stationAttackFleet;
	private LogisticsConvoyFleet logisticsConvoyFleet;
	private AsteroidMiningFleet asteroidMiningFleet;
	private GasMiningFleet gasMiningFleet;

    // Extra fleets for player skill (kind of ugly)
    private AsteroidMiningFleet asteroidMiningFleet2;
    private GasMiningFleet gasMiningFleet2;

    private StationFleetStance stationFleetStance;

    private CargoAPI playerStorage;

	public StationRecord(SectorEntityToken token)
	{
		stationToken = token;
		stationCargo = token.getCargo();
		planetType = this.derivePlanetType(token);

        isBeingBoarded = false;
        lastBoardAttemptTime = 0;

        this.stationFleetStance = StationFleetStance.BALANCED;

        playerStorage = Global.getFactory().createCargo(true);
        playerStorage.initMothballedShips("neutral");
	}

	public DiplomacyRecord getOwner()
	{
		return owningFaction;
	}

	public void setOwner(String newOwnerFactionId, Boolean displayMessage, Boolean updateRelationship)
	{
		if(newOwnerFactionId == null)
		{
			// Set station back to abandoned
			stationToken.setFaction("abandonded");
			owningFaction = null;
			stationCargo.setFreeTransfer(false);
			return;
		}
		String originalOwnerId = "";
		if(owningFaction != null)
			originalOwnerId = owningFaction.getFactionId();

		if(displayMessage)
		{
			if(newOwnerFactionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				ExerelinUtilsMessaging.addMessage(stationToken.getFullName() + " taken over by " + Global.getSector().getFaction(newOwnerFactionId).getDisplayName(), Color.magenta);
			else if(this.getOwner() != null && this.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				ExerelinUtilsMessaging.addMessage(stationToken.getFullName() + " taken over by " + Global.getSector().getFaction(newOwnerFactionId).getDisplayName(), Color.magenta);
			else
				ExerelinUtilsMessaging.addMessage(stationToken.getFullName() + " taken over by " + Global.getSector().getFaction(newOwnerFactionId).getDisplayName());
		}

		stationToken.setFaction(newOwnerFactionId);
        stationToken.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).stationInteriorIllustrationKeys[ExerelinUtils.getRandomInRange(0, ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).stationInteriorIllustrationKeys.length - 1)], 640, 400));

        if(ExerelinUtilsFaction.doesFactionOwnSystem(newOwnerFactionId, (StarSystemAPI)this.getStationToken().getContainingLocation()))
        {
            // Check if we should switch background image to faction specific one
            if(ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).changeSystemSpecsOnSystemLockdown)
            {
                /*StarSystemAPI system = (StarSystemAPI)this.getStationToken().getContainingLocation();
                system.setBackgroundTextureFilename(ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).preferredBackgroundImagePath);
                system.removeEntity(system.getStar());
                system.initStar(ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).preferredStarType, 700f);
                system.setLightColor(Color.decode(ExerelinConfig.getExerelinFactionConfig(newOwnerFactionId).preferredStarLight));*/
            }

            if(newOwnerFactionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                ExerelinUtilsMessaging.addMessage(((StarSystemAPI)this.getStationToken().getContainingLocation()).getName() + " conquered by " + Global.getSector().getFaction(newOwnerFactionId).getDisplayName() + "!", Color.magenta);
            else
                ExerelinUtilsMessaging.addMessage(((StarSystemAPI)this.getStationToken().getContainingLocation()).getName() + " conquered by " + Global.getSector().getFaction(newOwnerFactionId).getDisplayName() + "!");
        }
        else
        {
            // Check to see if we need to switch background image back
            if(!originalOwnerId.equalsIgnoreCase("") && ((StarSystemAPI)this.getStationToken().getContainingLocation()).getBackgroundTextureFilename().equalsIgnoreCase(ExerelinConfig.getExerelinFactionConfig(originalOwnerId).preferredBackgroundImagePath))
            {
                /*StarSystemAPI system = (StarSystemAPI)this.getStationToken().getContainingLocation();
                SystemManager systemManager = SystemManager.getSystemManagerForAPI(system);
                system.setBackgroundTextureFilename(systemManager.getOriginalBackgroundImage());
                system.removeEntity(system.getStar());
                system.initStar(systemManager.getOriginalStarSpec(), 700f);
                system.setLightColor(systemManager.getOriginalLightColor());*/
            }
        }

        this.stationFleetStance = StationFleetStance.BALANCED;

        //TODO rename station when possible

		owningFaction = SectorManager.getCurrentSectorManager().getDiplomacyManager().getRecordForFaction(newOwnerFactionId);

		if(newOwnerFactionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
			stationToken.getCargo().setFreeTransfer(ExerelinConfig.playerFactionFreeTransfer);
		else
			stationToken.getCargo().setFreeTransfer(false);

		// Update relationship
		if(!originalOwnerId.equalsIgnoreCase("") && updateRelationship)
			SectorManager.getCurrentSectorManager().getDiplomacyManager().updateRelationshipOnEvent(originalOwnerId, newOwnerFactionId, "LostStation");

        // Reset fleets
        this.updateFleetLists();

        // Get station targets for new faction
        this.deriveTargets();

        // Update FactionDirectors for changed sector
        FactionDirector.updateAllFactionDirectors();
	}

	public void setEfficiency(float value)
	{
		efficiency = value;
	}

	public SectorEntityToken getStationToken()
	{
		return stationToken;
	}

	public int getNumAttacking()
	{
		return numStationsTargeting;
	}

	public StationRecord getTargetStationRecord()
	{
		return targetStationRecord;
	}

	public void startTargeting()
	{
		numStationsTargeting++;

        updateStance();
	}

	public void stopTargeting()
	{
		numStationsTargeting--;
		if(numStationsTargeting <= 0)
		{
			numStationsTargeting = 0;
		}

        updateStance();
	}

    private void updateStance()
    {
        if(this.getOwner() != null && !this.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
        {
            if(!DiplomacyManager.isFactionAtWar(this.getOwner().getFactionId(), true))
            {
                if(this.stationFleetStance != StationFleetStance.BALANCED)
                    setStationFleetStance(StationFleetStance.BALANCED);
            }
            else
            {
                if(numStationsTargeting == 1 && this.stationFleetStance != StationFleetStance.BALANCED)
                    setStationFleetStance(StationFleetStance.BALANCED);

                if(numStationsTargeting >= 2 && this.stationFleetStance != StationFleetStance.DEFENSE)
                    setStationFleetStance(StationFleetStance.DEFENSE);

                if(numStationsTargeting == 0 && this.stationFleetStance != StationFleetStance.ATTACK && this.stationFleetStance != StationFleetStance.PATROL)
                {
                    if(ExerelinUtils.getRandomInRange(0,1) > 0)
                        setStationFleetStance(StationFleetStance.ATTACK);
                    else
                        setStationFleetStance(StationFleetStance.PATROL);
                }
            }
        }
    }

    public void deriveTargets()
    {
        if(this.owningFaction == null)
            return;

        deriveClosestEnemyTarget();
        deriveStationToAssist();
        deriveClosestAsteroid();
        deriveClosestGasGiant();
    }

	public void updateFleets()
	{
        // Remove dead/traitor fleets from counts
        updateFleetLists();

        // Update any of our fleets with any changed targets
        updateFleetTargets();

        if(checkIsBeingBoarded())
            return; // Don't spawn any fleets if being boarded

        if(this.convoyStationRecord != null
                && (logisticsConvoyFleet == null || !logisticsConvoyFleet.fleet.isAlive())
                && (this.getStationToken().getCargo().getSupplies() > 3200 || this.getStationToken().getCargo().getFuel() > 800 || this.getStationToken().getCargo().getMarines() > 400 || this.getStationToken().getCargo().getCrew(CargoAPI.CrewXPLevel.REGULAR) > 800)
                && ExerelinUtils.getRandomInRange(0, 2) == 0)
        {
            logisticsConvoyFleet = new LogisticsConvoyFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.convoyStationRecord.getStationToken());
        }

        float resourceMultiplier = 1.0f;
        if(this.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            resourceMultiplier = ExerelinUtilsPlayer.getPlayerStationResourceLimitMultiplier();

		if(stationCargo.getSupplies() < (6400*resourceMultiplier))
        {
			if((asteroidMiningFleet == null || !asteroidMiningFleet.fleet.isAlive())
                    && this.owningFaction != null)
            {
                asteroidMiningFleet = new AsteroidMiningFleet(this.owningFaction.getFactionId(), this.stationToken, this.targetAsteroid);
            }
            if(ExerelinUtilsPlayer.getPlayerDeployExtraMiningFleets() || this.getEfficiency(true) > 1.8f)
            {
                if(asteroidMiningFleet2 == null || !asteroidMiningFleet2.fleet.isAlive())
                {
                    asteroidMiningFleet2 = new AsteroidMiningFleet(this.owningFaction.getFactionId(), this.stationToken, this.targetAsteroid);
                }
            }
        }

		if(stationCargo.getFuel() < (1600*resourceMultiplier))
        {
            if((gasMiningFleet == null || !gasMiningFleet.fleet.isAlive())
                    && this.owningFaction != null)
            {
                gasMiningFleet = new GasMiningFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.targetGasGiant);
            }
            if(ExerelinUtilsPlayer.getPlayerDeployExtraMiningFleets() || this.getEfficiency(true) > 1.8f)
            {
                if((gasMiningFleet2 == null || !gasMiningFleet2.fleet.isAlive())
                        && this.owningFaction != null)
                {
                    gasMiningFleet2 = new GasMiningFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.targetGasGiant);
                }
            }
        }

        // Get resupply station for attacking fleets
        SectorEntityToken resupplyStation = null;
        if(FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getTargetResupplySystem() == (StarSystemAPI)this.getStationToken().getContainingLocation())
            resupplyStation = this.getStationToken();
        else
            resupplyStation = FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getTargetResupplyEntityToken();

        // Build 1 war fleet for defense
        if(this.owningFaction != null && (warFleet == null || !warFleet.fleet.isAlive()))
        {
            warFleet = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.DEFENSE, true);

            if(warFleet.fleet == null)
                warFleet = null;

            return;
        }

        if(this.stationFleetStance == StationFleetStance.BALANCED)
        {
            if(this.owningFaction != null && (warFleet1 == null || !warFleet1.fleet.isAlive()) && this.targetStationRecord != null && this.targetStationRecord.getOwner() != null)
            {
                warFleet1 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.targetStationRecord.getStationToken(), null, resupplyStation, WarFleet.FleetStance.ATTACK, true);

                if(warFleet1.fleet == null)
                    warFleet1 = null;

                return;
            }
            if(this.owningFaction != null && (warFleet2 == null || !warFleet2.fleet.isAlive()) && DiplomacyManager.isFactionAtWar(this.owningFaction.getFactionId(), true))
            {
                if(this.defendStationRecord != null)
                    warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, this.defendStationRecord.getStationToken(), null, WarFleet.FleetStance.PATROL, true);
                else
                    warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.PATROL, true);

                if(warFleet2.fleet == null)
                    warFleet2 = null;

                return;
            }
        }

        if(this.stationFleetStance == StationFleetStance.DEFENSE)
        {
            if(this.owningFaction != null && (warFleet1 == null || !warFleet1.fleet.isAlive()))
            {
                warFleet1 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.DEFENSE, true);

                if(warFleet1.fleet == null)
                    warFleet1 = null;

                return;
            }
            if(this.owningFaction != null && (warFleet2 == null || !warFleet2.fleet.isAlive()))
            {
                warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.DEFENSE, true);

                if(warFleet2.fleet == null)
                    warFleet2 = null;

                return;
            }
        }

        if(this.stationFleetStance == StationFleetStance.ATTACK && this.targetStationRecord != null && this.targetStationRecord.getOwner() != null)
        {
            if(this.owningFaction != null && (warFleet1 == null || !warFleet1.fleet.isAlive()))
            {
                warFleet1 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.targetStationRecord.getStationToken(), null, resupplyStation, WarFleet.FleetStance.ATTACK, true);

                if(warFleet1.fleet == null)
                    warFleet1 = null;

                return;
            }
            if(this.owningFaction != null && (warFleet2 == null || !warFleet2.fleet.isAlive())&& this.targetStationRecord != null && this.targetStationRecord.getOwner() != null)
            {
                warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), this.targetStationRecord.getStationToken(), null, resupplyStation, WarFleet.FleetStance.ATTACK, true);

                if(warFleet2.fleet == null)
                    warFleet2 = null;

                return;
            }
        }

        if(this.stationFleetStance == StationFleetStance.PATROL)
        {
            if(this.owningFaction != null && (warFleet1 == null || !warFleet1.fleet.isAlive()) && DiplomacyManager.isFactionAtWar(this.owningFaction.getFactionId(), true))
            {
                if(this.defendStationRecord != null)
                    warFleet1 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, this.defendStationRecord.getStationToken(), null, WarFleet.FleetStance.PATROL, true);
                else
                    warFleet1 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.PATROL, true);

                if(warFleet1.fleet == null)
                    warFleet1 = null;

                return;
            }
            if(this.owningFaction != null && (warFleet2 == null || !warFleet2.fleet.isAlive()) && DiplomacyManager.isFactionAtWar(this.owningFaction.getFactionId(), true))
            {
                if(this.defendStationRecord != null)
                    warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, this.defendStationRecord.getStationToken(), null, WarFleet.FleetStance.PATROL, true);
                else
                    warFleet2 = new WarFleet(this.owningFaction.getFactionId(), this.getStationToken(), null, null, null, WarFleet.FleetStance.PATROL, true);

                if(warFleet2.fleet == null)
                    warFleet2 = null;

                return;
            }
        }

        if(this.owningFaction != null && (stationAttackFleet == null || !stationAttackFleet.fleet.isAlive())
                && takeoverStationRecord != null
                && this.stationFleetStance != StationFleetStance.DEFENSE && this.stationFleetStance != StationFleetStance.PATROL)
        {
            stationAttackFleet = new StationAttackFleet(this.owningFaction.getFactionId(), this.getStationToken(), takeoverStationRecord.getStationToken(), resupplyStation, true);

            if(stationAttackFleet.fleet == null)
                stationAttackFleet = null;

            return;
        }
	}

	// Increase resources in station based off efficiency
	public void increaseResources()
	{
        if(this.getOwner() == null)
            return;

        if(checkIsBeingBoarded())
            return; // Don't increase resources if being boarded

        float resourceMultiplier = 1.0f;
        //if(this.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            //resourceMultiplier = ExerelinUtilsPlayer.getPlayerStationResourceLimitMultiplier();

		/*if(stationCargo.getFuel() < 1600*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "fuel", CargoAPI.CargoItemType.RESOURCES, 100 * efficiency)); // Halved due to mining exerelin.fleets
		if(stationCargo.getSupplies() < 6400*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "supplies", CargoAPI.CargoItemType.RESOURCES, 400 * efficiency)); // Halved due to mining exerelin.fleets*/
		if(stationCargo.getMarines() < 800*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "marines", CargoAPI.CargoItemType.RESOURCES, (int) (200 * efficiency)));
		if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) < 1600*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "regular_crew", CargoAPI.CargoItemType.RESOURCES, (int) (400 * efficiency)));

        if(stationCargo.getCrew(CargoAPI.CrewXPLevel.GREEN) < 950)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "green_crew", CargoAPI.CargoItemType.RESOURCES, 20));
        if(stationCargo.getCrew(CargoAPI.CrewXPLevel.VETERAN) < 950)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "veteran_crew", CargoAPI.CargoItemType.RESOURCES, 20));
        if(stationCargo.getCrew(CargoAPI.CrewXPLevel.ELITE) < 950)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "elite_crew", CargoAPI.CargoItemType.RESOURCES, 20));

		if(planetType.equalsIgnoreCase("gas") && stationCargo.getFuel() < 3200*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "fuel", CargoAPI.CargoItemType.RESOURCES, 200 * efficiency)); // Halved due to mining exerelin.fleets
		if(planetType.equalsIgnoreCase("moon") && stationCargo.getSupplies() < 12800*resourceMultiplier)
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "supplies", CargoAPI.CargoItemType.RESOURCES, 800 * efficiency)); // Halved due to mining exerelin.fleets
		if(planetType.equalsIgnoreCase("planet"))
		{
            if(stationCargo.getMarines() < 1600*resourceMultiplier)
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "marines", CargoAPI.CargoItemType.RESOURCES, (int) (200 * efficiency)));
            if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) < 3200*resourceMultiplier)
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "regular_crew", CargoAPI.CargoItemType.RESOURCES, (int) (400 * efficiency)));
		}

		if(efficiency > 0.6f)
		{
            ExerelinUtilsCargo.addFactionVariantsToCargo(stationCargo, owningFaction.getFactionId(), 2);
            ExerelinUtilsCargo.addFactionWeaponsToCargo(stationCargo, owningFaction.getFactionId(), 2, 2);
		}

        if(efficiency > 0.8f)
        {
            if(ExerelinUtils.getRandomInRange(0, 50) == 0)
            {
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "saboteur", CargoAPI.CargoItemType.RESOURCES, 1));
            }

            if(ExerelinUtils.getRandomInRange(0, 50) == 0)
            {
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddCargo(stationCargo, "agent", CargoAPI.CargoItemType.RESOURCES, 1));
            }
        }

        // Update efficiency
        float baseEfficiency = 1.0f;
        //if(this.getOwner().getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            //baseEfficiency = ExerelinUtilsPlayer.getPlayerStationBaseEfficiency();

        if(efficiency < baseEfficiency)
            efficiency = efficiency + Math.min(0.1f, baseEfficiency - efficiency);
        else if(efficiency > baseEfficiency)
            efficiency = efficiency - Math.min(0.1f, efficiency - baseEfficiency);
	}

	// Clear cargo and ships from station
	public void clearCargo()
	{
		ExerelinUtils.removeRandomWeaponStacksFromCargo(stationCargo,  9999);
		ExerelinUtils.removeRandomShipsFromCargo(stationCargo,  9999);
        stationCargo.clear();
	}


	private void deriveClosestEnemyTarget()
	{
		SectorEntityToken bestTarget = null;
        StationRecord bestTargetRecord = null;

        SystemStationManager targetSystemStationManager;
        bestTarget = ExerelinUtils.getClosestEnemyStation(this.stationToken.getFaction().getId(), (StarSystemAPI)this.stationToken.getContainingLocation(), this.stationToken);

        if(bestTarget == null)
        {
            FactionDirector factionDirector = FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId());
            if(factionDirector.getTargetSystem() != null && factionDirector.getTargetSectorEntityToken() != null)
            {
                targetSystemStationManager = SystemManager.getSystemManagerForAPI(factionDirector.getTargetSystem()).getSystemStationManager();
                bestTarget = factionDirector.getTargetSectorEntityToken();
                bestTargetRecord = targetSystemStationManager.getStationRecordForToken(bestTarget);
            }
        }
        else
        {
            targetSystemStationManager = SystemManager.getSystemManagerForAPI((StarSystemAPI)this.stationToken.getContainingLocation()).getSystemStationManager();
            bestTargetRecord = targetSystemStationManager.getStationRecordForToken(bestTarget);
        }

		if(targetStationRecord != null)
			targetStationRecord.stopTargeting();
		if(bestTargetRecord != null && bestTargetRecord.getOwner() != null)
            bestTargetRecord.startTargeting();

		targetStationRecord = bestTargetRecord;
        takeoverStationRecord = bestTargetRecord;

        if(targetStationRecord != null && targetStationRecord.getOwner() == null)
            targetStationRecord = null;
	}

	private void deriveStationToAssist()
	{
		StationRecord assistStation = null;
        Boolean defendingSelf = false;

		// Check if we are under attack first
		if(getNumAttacking() > 0)
		{
			this.defendStationRecord = this;
            defendingSelf = true;
		}

        // Support a station in this system under attack
		for(int i = 0; i < SystemManager.getSystemManagerForAPI((StarSystemAPI)this.stationToken.getContainingLocation()).getSystemStationManager().getStationRecords().length; i++)
		{
			StationRecord possibleAssist = SystemManager.getSystemManagerForAPI((StarSystemAPI)this.stationToken.getContainingLocation()).getSystemStationManager().getStationRecords()[i];

			if(possibleAssist.getOwner() == null)
				continue;

			if(possibleAssist.getOwner().getFactionId().equalsIgnoreCase(this.getOwner().getFactionId()) || possibleAssist.getOwner().getGameRelationship(this.getOwner().getFactionId()) >= 1)
			{
				// Check severity of attack
				if((assistStation != null && possibleAssist.numStationsTargeting > assistStation.getNumAttacking()) || (assistStation == null && possibleAssist.numStationsTargeting > 0))
					assistStation = possibleAssist;
			}
		}

		//if(assistStation != null)
			//System.out.println(this.getStationToken().getFullName() + " is assisting " + assistStation.getStationToken().getFullName() + " which is targeted " + assistStation.getNumAttacking());

        // Support a station in another system if it is under attack
        if(assistStation == null)
        {
            StarSystemAPI starSystemAPI = FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getSupportSystem();
            if(starSystemAPI != null)
            {
                SystemStationManager systemStationManager = SystemManager.getSystemManagerForAPI(starSystemAPI).getSystemStationManager();
                SectorEntityToken assistToken = FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getSupportSectorEntityToken();
                assistStation = systemStationManager.getStationRecordForToken(assistToken);
            }
        }

        if(assistStation != null)
        {
            this.convoyStationRecord = assistStation;
            if(!defendingSelf)
                this.defendStationRecord = assistStation;
            return;
        }
        else if(!defendingSelf)
            this.defendStationRecord = null;

        // No stations under attack, so assist a random faction station in this system
        StationRecord[] stationRecords = SystemManager.getSystemManagerForAPI((StarSystemAPI)this.stationToken.getContainingLocation()).getSystemStationManager().getStationRecords();
        Collections.shuffle(Arrays.asList(stationRecords));

        for(int i = 0; i < stationRecords.length; i++)
        {
            StationRecord possibleAssist = stationRecords[i];

            if(possibleAssist.getOwner() == null)
                continue;

            if(possibleAssist.getOwner().getFactionId().equalsIgnoreCase(this.getOwner().getFactionId()))
            {
                assistStation = possibleAssist;
                break;
            }
        }


        this.convoyStationRecord = assistStation;
	}

	private void deriveClosestAsteroid()
	{
		List asteroids = ((StarSystemAPI)this.stationToken.getContainingLocation()).getAsteroids();
		SectorEntityToken closestAsteroid = null;
		float closestDistance = 999999999f;

		if(targetAsteroid != null && ExerelinUtils.getRandomInRange(0,2) != 0)
			return; // Don't recalc every time

		// Only check every 4th asteroid as they are fairly close normally
		for(int i = 0; i < asteroids.size(); i = i + 4)
		{
			SectorEntityToken asteroid = (SectorEntityToken)asteroids.get(i);

			Float distance = MathUtils.getDistanceSquared(this.stationToken, asteroid) ;
			if(distance < closestDistance)
			{
				closestAsteroid = asteroid;
				closestDistance = distance;
			}
		}

		targetAsteroid = closestAsteroid;
	}

	private void deriveClosestGasGiant()
	{
		List planets = ((StarSystemAPI)this.stationToken.getContainingLocation()).getPlanets();
		SectorEntityToken closestPlanet = null;
		float closestDistance = 999999999f;

		if(targetGasGiant != null && ExerelinUtils.getRandomInRange(0,4) != 0)
			return; // Don't recalc each time

		for(int i = 0; i < planets.size(); i++)
		{
			SectorEntityToken planet = (SectorEntityToken)planets.get(i);

			if(!derivePlanetType(planet).equalsIgnoreCase("gas"))
				continue;

			Float distance = MathUtils.getDistanceSquared(this.stationToken,  planet) ;
			if(distance < closestDistance)
			{
				closestPlanet = planet;
				closestDistance = distance;
			}
		}
		targetGasGiant = closestPlanet;
	}

	public void updateFleetTargets()
	{
        SectorEntityToken resupplyStation = null;
        if(FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getTargetSystem() == (StarSystemAPI)this.getStationToken().getContainingLocation())
            resupplyStation = this.getStationToken();
        else
            resupplyStation = FactionDirector.getFactionDirectorForFactionId(this.owningFaction.getFactionId()).getTargetResupplyEntityToken();

        SectorEntityToken targetToken = null;
        if(this.targetStationRecord != null)
            targetToken = this.targetStationRecord.getStationToken();

        SectorEntityToken defendToken = null;
        if(this.defendStationRecord != null)
            defendToken = this.defendStationRecord.getStationToken();

        SectorEntityToken takeoverToken = null;
        if(this.takeoverStationRecord != null)
            takeoverToken = this.takeoverStationRecord.getStationToken();

        SectorEntityToken convoyToken = null;
        if(this.convoyStationRecord != null)
            convoyToken = this.convoyStationRecord.getStationToken();

        if(stationAttackFleet != null)
		    stationAttackFleet.setTarget(takeoverToken, resupplyStation);

        if(warFleet1 != null)
            warFleet1.setTarget(targetToken, defendToken, resupplyStation);

        if(warFleet2 != null)
            warFleet2.setTarget(targetToken, defendToken, resupplyStation);

        if(logisticsConvoyFleet != null)
		    logisticsConvoyFleet.setTarget(convoyToken);

        if(gasMiningFleet != null)
		    gasMiningFleet.setTargetPlanet(targetGasGiant);

        if(gasMiningFleet2 != null)
            gasMiningFleet2.setTargetPlanet(targetGasGiant);

        if(asteroidMiningFleet != null)
            asteroidMiningFleet.setTargetAsteroid(targetAsteroid);

        if(asteroidMiningFleet2 != null)
            asteroidMiningFleet2.setTargetAsteroid(targetAsteroid);
	}

    public void updateFleetLists()
    {
        if(!exerelinFleetReferenceOK(stationAttackFleet))
            stationAttackFleet = null;

        if(!exerelinFleetReferenceOK(warFleet))
            warFleet = null;

        if(!exerelinFleetReferenceOK(warFleet1))
            warFleet1 = null;

        if(!exerelinFleetReferenceOK(warFleet2))
            warFleet2 = null;

        if(!exerelinFleetReferenceOK(logisticsConvoyFleet))
            logisticsConvoyFleet = null;

        if(!exerelinFleetReferenceOK(asteroidMiningFleet))
            asteroidMiningFleet = null;

        if(!exerelinFleetReferenceOK(asteroidMiningFleet2))
            asteroidMiningFleet2 = null;

        if(!exerelinFleetReferenceOK(gasMiningFleet))
            gasMiningFleet = null;

        if(!exerelinFleetReferenceOK(gasMiningFleet2))
            gasMiningFleet2 = null;
    }

    private boolean exerelinFleetReferenceOK(ExerelinFleetBase exerelinFleetBase)
    {
        if((exerelinFleetBase != null && !exerelinFleetBase.fleet.isAlive())
                || (exerelinFleetBase != null && exerelinFleetBase.fleet != null && !exerelinFleetBase.fleet.getFaction().getId().equalsIgnoreCase(this.stationToken.getFaction().getId())))
            return false;
        else
            return true;
    }

	private String derivePlanetType(SectorEntityToken token)
	{
		String name = token.getFullName();
		if(name.contains("Gaseous") && !(name.contains(" I") || name.contains(" II") || name.contains(" III")))
			return "gas";
		if(name.contains(" I") || name.contains(" II") || name.contains(" III"))
			return "moon";
		else
			return "planet";
	}

    // Set the station being boarded
    public void setIsBeingBoarded(Boolean beingBoarded)
    {
        isBeingBoarded = beingBoarded;
        lastBoardAttemptTime = Global.getSector().getClock().getTimestamp();
    }

    // Check if station has been boarded in last 3 days
    private Boolean checkIsBeingBoarded()
    {
        if (!isBeingBoarded)
            return false;
        else if(isBeingBoarded && Global.getSector().getClock().getElapsedDaysSince(lastBoardAttemptTime) > 3)
        {
            isBeingBoarded = false;
            return false;
        }
        else
        {
            return true;
        }
    }

    public float getEfficiency(Boolean highOnly)
    {
        if(efficiency < 1 && highOnly)
            return 1f;
        else
            return efficiency;
    }

    public StationFleetStance getStationFleetStance()
    {
        return this.stationFleetStance;
    }

    public void setStationFleetStance(StationFleetStance stance)
    {
        this.stationFleetStance = stance;

        this.updateFleetLists();

        if(stance == StationFleetStance.BALANCED)
        {
            if(warFleet1 != null)
                warFleet1.setStance(WarFleet.FleetStance.ATTACK);
            if(warFleet2 != null)
                warFleet2.setStance(WarFleet.FleetStance.PATROL);
        }
        if(stance == StationFleetStance.DEFENSE)
        {
            if(warFleet1 != null)
                warFleet1.setStance(WarFleet.FleetStance.DEFENSE);
            if(warFleet2 != null)
                warFleet2.setStance(WarFleet.FleetStance.DEFENSE);
        }
        if(stance == StationFleetStance.ATTACK)
        {
            if(warFleet1 != null)
                warFleet1.setStance(WarFleet.FleetStance.ATTACK);
            if(warFleet2 != null)
                warFleet2.setStance(WarFleet.FleetStance.ATTACK);
        }
        if(stance == StationFleetStance.PATROL)
        {
            if(warFleet1 != null)
                warFleet1.setStance(WarFleet.FleetStance.PATROL);
            if(warFleet2 != null)
                warFleet2.setStance(WarFleet.FleetStance.PATROL);
        }

        if(warFleet1 != null)
            warFleet1.setFleetAssignments();
        if(warFleet2 != null)
            warFleet2.setFleetAssignments();
    }

    public SectorEntityToken getTargetAsteroid()
    {
        return this.targetAsteroid;
    }

    public SectorEntityToken getTargetGasGiant()
    {
        return this.targetGasGiant;
    }

    public void capInventoryAmounts()
    {
        int numWeaponStacks = stationToken.getCargo().getWeapons().size();
        if(numWeaponStacks > 40)
            ExerelinUtils.removeRandomWeaponStacksFromCargo(stationToken.getCargo(), numWeaponStacks - (numWeaponStacks - 35));

        int numShips = stationToken.getCargo().getMothballedShips().getMembersListCopy().size();
        if(numShips > 25)
            ExerelinUtils.removeRandomShipsFromCargo(stationToken.getCargo(), numShips - (45 - numShips));
    }

    public CargoAPI getPlayerStorage()
    {
        return this.playerStorage;
    }

    public void setLastBoardAttemptTime(long timestamp)
    {
        this.lastBoardAttemptTime = timestamp;
    }
}
