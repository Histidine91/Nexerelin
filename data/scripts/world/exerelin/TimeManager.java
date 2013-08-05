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
	private GregorianCalendar calendar = new GregorianCalendar();

	public SectorManager sectorManagerRef; // REMOVE WHEN CAN USE PERSISTANT DATA

	public TimeManager()
	{
		lastHeartbeat = Global.getSector().getClock().getTimestamp();
		heartbeatInterval = (1.0f - (Global.getSector().getClock().getHour() / 24f));
	}

	private void runHourly()
	{

	}

	private void runDaily()
	{
		// Handle player mining
		ExerelinUtils.handlePlayerFleetMining(Global.getSector().getPlayerFleet());

		SectorManager sectorManager = SectorManager.getCurrentSectorManager();

		// Check for player betrayal
		sectorManager.getDiplomacyManager().checkBetrayal();

		// Manage relationships
		sectorManager.getDiplomacyManager().updateRelationships();

		// Update stations
		sectorManager.updateStations();

		// Run system events
		sectorManager.runEvents();
	}

	private void runWeekly()
	{
		// Check player has station or station attack fleet
		SectorManager.getCurrentSectorManager().checkPlayerHasStationOrStationAttackFleet();

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
		ExerelinData.getInstance().getSectorManager().doSetupChecks();
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
