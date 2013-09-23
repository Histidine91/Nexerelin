package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

import java.util.*;

public class TimeManager implements SpawnPointPlugin
{
	private static final float BASE_INTERVAL = 1.0f;
	private static final int FIRST_DAY_IN_WEEK = GregorianCalendar.SUNDAY;
	private float heartbeatInterval;
	private long lastHeartbeat;
    private long lastHourChecked;
	private GregorianCalendar calendar = new GregorianCalendar();

	public SectorManager sectorManagerRef; // REMOVE WHEN CAN USE PERSISTANT DATA

	public TimeManager()
	{
		lastHeartbeat = Global.getSector().getClock().getTimestamp();
		heartbeatInterval = (1.0f - (Global.getSector().getClock().getHour() / 24f));
	}

	private void runHourly(long hour)
	{
        if(hour == 3)
        {
            // Handle player mining
            ExerelinUtils.handlePlayerFleetMining(Global.getSector().getPlayerFleet());
        }

        SectorManager sectorManager = SectorManager.getCurrentSectorManager();

        if(hour == 6)
        {
            // Check for player betrayal
            sectorManager.getDiplomacyManager().checkBetrayal();
        }

        if(hour == 9)
        {
            // Handle player station boarding
            ExerelinUtils.handlePlayerBoarding(Global.getSector().getPlayerFleet());
        }

        if(hour == 12)
        {
            // Manage relationships
            sectorManager.getDiplomacyManager().updateRelationships();
        }

        if(hour == 18)
        {
            // Run system events
            sectorManager.runEvents();
        }

	}

	private void runDaily()
	{
        MutableCharacterStatsAPI playerStatsAPI = Global.getSector().getPlayerFleet().getCommanderStats();

        /*System.out.println("active_diplomacy: " + playerStatsAPI.getSkillLevel("active_diplomacy"));
        System.out.println("passive_diplomacy: " + playerStatsAPI.getSkillLevel("passive_diplomacy"));
        System.out.println("station_industry: " + playerStatsAPI.getSkillLevel("station_industry"));
        System.out.println("fleet_crew_training: " + playerStatsAPI.getSkillLevel("fleet_crew_training"));
        System.out.println("fleet_deployment: " + playerStatsAPI.getSkillLevel("fleet_deployment"));*/


		SectorManager sectorManager = SectorManager.getCurrentSectorManager();

		// Update stations
		sectorManager.updateStations();
	}

	private void runWeekly()
	{
		// Check player has station or station attack fleet
		SectorManager.getCurrentSectorManager().checkPlayerHasStationOrStationAttackFleet();

		// Pay wages
		SectorManager.getCurrentSectorManager().payPlayerWages();

        // Update FactionDirectors
        FactionDirector.updateAllFactionDirectors();
	}

	private void runMonthly()
	{
		// Respawn missing factions
		SectorManager.getCurrentSectorManager().respawnRandomFaction();
	}

	private void runYearly()
	{

	}

	@Override
	public void advance(SectorAPI sectorAPI, LocationAPI locationAPI)
	{
        // Do any setup steps that need to be performed
		ExerelinData.getInstance().getSectorManager().doSetupChecks();

        if(sectorAPI.getClock().getElapsedDaysSince(lastHeartbeat) < heartbeatInterval && sectorAPI.getClock().getHour() != lastHourChecked)
        {
            lastHourChecked = sectorAPI.getClock().getHour();
            runHourly(lastHourChecked);
        }

		if (sectorAPI.getClock().getElapsedDaysSince(lastHeartbeat) >= heartbeatInterval)
		{
			doIntervalChecks(sectorAPI.getClock().getTimestamp());
			checkSynched();
		}
	}

	private void doIntervalChecks(long time)
	{
		lastHeartbeat = time;
		runDaily();

		calendar.setTimeInMillis(time);

		if (calendar.get(GregorianCalendar.DAY_OF_WEEK) == FIRST_DAY_IN_WEEK)
		{
			runWeekly();
		}

		if (calendar.get(GregorianCalendar.DAY_OF_MONTH) == 1)
		{
			runMonthly();

			if (calendar.get(GregorianCalendar.DAY_OF_YEAR) == 1)
			{
				runYearly();
			}
		}
	}

	private void checkSynched()
	{
		// Compensate for day-synch code in constructor
		if (heartbeatInterval != BASE_INTERVAL)
		{
			heartbeatInterval = BASE_INTERVAL;
		}
	}
}
