package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

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
        if(hour == 6)
        {
            // Handle player mining
            ExerelinUtils.handlePlayerFleetMining(Global.getSector().getPlayerFleet());
        }

        if(hour == 9)
        {
            // Check for player betrayal
            SectorManager.getCurrentSectorManager().getDiplomacyManager().checkBetrayal();
        }

        if(hour == 12)
        {
            // Handle player station boarding
            ExerelinUtils.handlePlayerBoarding(Global.getSector().getPlayerFleet());
        }

        if(hour == 15)
        {
            // Manage relationships
            Thread updateRelationshipThread = new Thread("updateRelationshipThread"){
                public void run()
                {
                    SectorManager.getCurrentSectorManager().getDiplomacyManager().updateRelationships();
                }
            };

            updateRelationshipThread.start();
        }

        if(hour == 18)
        {
            // Run system events
            SectorManager.getCurrentSectorManager().runEvents();
        }
	}

	private void runDaily()
	{
        Thread stationTargetThread = new Thread("stationTargetThread"){
            public void run()
            {
                // Update station targets
                SectorManager.getCurrentSectorManager().updateStationTargets();
            }
        };

        stationTargetThread.start();

        SectorManager.getCurrentSectorManager().updateStations();
	}

	private void runWeekly()
	{
        Thread weeklyThread = new Thread("weeklyThread"){
            public void run()
            {
                // Check player has station or station attack fleet
                SectorManager.getCurrentSectorManager().checkPlayerHasStationOrStationAttackFleet();

                // Update FactionDirectors
                FactionDirector.updateAllFactionDirectors();

                // Increase station resources
                SectorManager.getCurrentSectorManager().updateStationResources();
            }
        };

        weeklyThread.start();

        // Pay wages
        SectorManager.getCurrentSectorManager().payPlayerWages();
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
