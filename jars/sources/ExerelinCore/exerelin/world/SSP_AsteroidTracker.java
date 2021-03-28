package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.utilities.NexUtils;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.util.vector.Vector2f;

public class SSP_AsteroidTracker implements EveryFrameScript {

    private static final float INTERVAL = 0.25f;

    private static transient final Map<SectorEntityToken, Vector2f> asteroidLocationMap = new WeakHashMap<>(5000);
    private static transient final Map<SectorEntityToken, Vector2f> asteroidVelocityMap = new WeakHashMap<>(5000);

    public static Vector2f getVelocity(SectorEntityToken asteroid) {
        Vector2f velocity = asteroidVelocityMap.get(asteroid);
        if (velocity == null) {
            velocity = new Vector2f();
        }
        return velocity;
    }

    private final IntervalUtil interval = new IntervalUtil(INTERVAL, INTERVAL);

    @Override
    public void advance(float amount) {
        NexUtils.advanceIntervalDays(interval, amount);
        if (interval.intervalElapsed()) {
            float scale = 1f / (Global.getSector().getClock().getSecondsPerDay() * INTERVAL);
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) {
                return;
            }

            LocationAPI playerLocation = playerFleet.getContainingLocation();
            if (playerLocation == null) {
                return;
            }

            List<SectorEntityToken> asteroids = playerLocation.getAsteroids();
            int size = asteroids.size();
            for (int i = 0; i < size; i++) {
                SectorEntityToken asteroid = asteroids.get(i);

                Vector2f location = asteroidLocationMap.get(asteroid);
                Vector2f velocity = asteroidVelocityMap.get(asteroid);
                if (location == null || velocity == null) {
                    location = new Vector2f(asteroid.getLocation());
                    asteroidLocationMap.put(asteroid, location);
                    velocity = new Vector2f();
                    asteroidVelocityMap.put(asteroid, velocity);
                } else {
                    Vector2f.sub(asteroid.getLocation(), location, velocity);
                    velocity.scale(scale);
                    location.set(asteroid.getLocation());
                }
            }
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

}
