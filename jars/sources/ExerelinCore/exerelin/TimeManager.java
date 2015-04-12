package exerelin;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import exerelin.utilities.ExerelinConfig;

import java.util.*;

@Deprecated
public class TimeManager implements EveryFrameScript
{
	private static final float BASE_INTERVAL = 1.0f;
	private static final int FIRST_DAY_IN_WEEK = GregorianCalendar.SUNDAY;
	private float heartbeatInterval;
	private long lastHeartbeat;
    private long lastHourChecked;
	private GregorianCalendar calendar = new GregorianCalendar();

	public TimeManager()
	{
		lastHeartbeat = Global.getSector().getClock().getTimestamp();
		heartbeatInterval = (1.0f - (Global.getSector().getClock().getHour() / 24f));
	}

	private void runHourly(long hour)
	{
        if(hour == 1)
            //SectorManager.getCurrentSectorManager().updatePlayerCommandedFleets();

        if(hour == 3)
        {
            // Handle player station boarding
            //ExerelinUtils.handlePlayerBoarding(Global.getSector().getPlayerFleet());
        }

        if(hour == 6)
        {
            // Handle player mining
            //ExerelinUtils.handlePlayerFleetMining(Global.getSector().getPlayerFleet());
        }

        if(hour == 9)
        {
            // Check for player betrayal
            if(ExerelinConfig.enableThreading)
            {
                Thread checkBetrayalThread = new Thread("checkBetrayalThread"){
                    public void run()
                    {
                        SectorManager.getCurrentSectorManager().getDiplomacyManager().checkBetrayal();
                    }
                };

                //checkBetrayalThread.start();
            }
            //else
                //SectorManager.getCurrentSectorManager().getDiplomacyManager().checkBetrayal();

        }

        if(hour == 11)
        {
            // Update station resources part 1
            //SectorManager.getCurrentSectorManager().updateStationResources(28);
        }

        if(hour == 15)
        {
            // Manage relationships
            if(ExerelinConfig.enableThreading)
            {
                Thread updateRelationshipThread = new Thread("updateRelationshipThread"){
                    public void run()
                    {
                        //SectorManager.getCurrentSectorManager().getDiplomacyManager().updateRelationships();
                    }
                };

                updateRelationshipThread.start();
            }
            //else
                //SectorManager.getCurrentSectorManager().getDiplomacyManager().updateRelationships();
        }

        if(hour == 18)
        {
            // Run sector and system events
            //SectorManager.getCurrentSectorManager().runEvents();
        }

        if(hour == 23)
        {
            // Update station resources part 2
            //SectorManager.getCurrentSectorManager().updateStationResources(28);
        }
	}

	private void runDaily()
	{
        if(ExerelinConfig.enableThreading)
        {
            Thread stationTargetThread = new Thread("stationTargetThread"){
                public void run()
                {
                    // Update station targets
                    //SectorManager.getCurrentSectorManager().updateStationTargets();
                }
            };

            //stationTargetThread.start();
        }
        //else
            //SectorManager.getCurrentSectorManager().updateStationTargets();

        //SectorManager.getCurrentSectorManager().updateStationFleets();
	}

	private void runWeekly()
	{
        if(ExerelinConfig.enableThreading)
        {
            Thread weeklyThread = new Thread("weeklyThread"){
                public void run()
                {
                    // Update FactionDirectors
                    //FactionDirector.updateAllFactionDirectors();

                    // Check player has station or station attack fleet
                    //SectorManager.getCurrentSectorManager().checkPlayerHasLost();
                    //SectorManager.getCurrentSectorManager().checkPlayerHasWon();
                }
            };

            //weeklyThread.start();
        }
        else
        {
            // Update FactionDirectors
            //FactionDirector.updateAllFactionDirectors();

            // Check player has station or station attack fleet
            //SectorManager.getCurrentSectorManager().checkPlayerHasLost();
            //SectorManager.getCurrentSectorManager().checkPlayerHasWon();
        }

        // Pay wages
        //SectorManager.getCurrentSectorManager().payPlayerWages();
	}

	private void runMonthly()
	{

	}

	private void runYearly()
	{

	}

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return false;
    }

	@Override
	public void advance(float amount)
	{
        if(SectorManager.getCurrentSectorManager() == null)
            return; //OnGameLoad doesn't seem to run before EveryFrameScript.advance()

        // Do any every frame checks that need to be performed
		//SectorManager.getCurrentSectorManager().doEveryFrameChecks();

        SectorAPI sector = Global.getSector();

        if(sector.getClock().getElapsedDaysSince(lastHeartbeat) < heartbeatInterval && sector.getClock().getHour() != lastHourChecked)
        {
            lastHourChecked = sector.getClock().getHour();
            runHourly(lastHourChecked);
        }

		if (sector.getClock().getElapsedDaysSince(lastHeartbeat) >= heartbeatInterval)
		{
			doIntervalChecks(sector.getClock().getTimestamp());
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
