package exerelin;

import java.util.*;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class TestFleetSpawner implements EveryFrameScript
{
    private long lastTimeCheck;
    private static final int SPAWN_INTERVAL_DAYS = 1;

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
        if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) > SPAWN_INTERVAL_DAYS)
        {
            // Get all systems in sector
            List systems = Global.getSector().getStarSystems();
            int rand = this.getRandomInRange(0, systems.size() - 1);
            StarSystemAPI systemAPI = (StarSystemAPI)systems.get(rand);

            // Select a random planet
            List planets = systemAPI.getPlanets();
            rand = this.getRandomInRange(0, planets.size() - 1);
            PlanetAPI planetAPI = (PlanetAPI)planets.get(rand);

            Global.getSector().createFleet("player", "shuttle");

        }
    }

    private int getRandomInRange(int min, int max)
    {
        return min + (int)(Math.random() * ((max - min) + 1)); // hate java
    }
}
