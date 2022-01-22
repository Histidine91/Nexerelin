package exerelin.utilities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.ExerelinProcGen;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.orbits.EllipticalOrbit;

public class NexUtilsAstro {
	
	/**
	 * Gets the orbital period for an object around a stellar object of the specified radius and density,
	 * at the given <code>orbitRadius</code>.
	 * @param primaryRadius Radius of the stellar object to orbit.
	 * @param orbitRadius The orbit radius (assumes a circular orbit).
	 * @param density Density of the primary.
	 * @return Orbit period in days
	 */
	public static float getOrbitalPeriod(float primaryRadius, float orbitRadius, float density)
	{
		if (density == 0) return 99999999;
		primaryRadius *= 0.01;
		orbitRadius *= 1/62.5;	// realistic would be 1/50 but the planets orbit rather too slowly then
		
		float mass = (float)Math.floor(4f / 3f * Math.PI * Math.pow(primaryRadius, 3));
		mass *= density;
		float radiusCubed = (float)Math.pow(orbitRadius, 3);
		float period = (float)(2 * Math.PI * Math.sqrt(radiusCubed/mass) * 2);
		
		return period;
	}
	
	/**
	 * Gets the orbital period for an object around the specified <code>primary</code> at the given <code>orbitRadius</code>.
	 * Density is automatically calculated with <code>getDensity(primary)</code>.
	 * @param primary The stellar object to orbit.
	 * @param orbitRadius The orbit radius (assumes a circular orbit).
	 * @return Orbit period in days
	 */
	public static float getOrbitalPeriod(SectorEntityToken primary, float orbitRadius)
	{
		return getOrbitalPeriod(primary.getRadius(), orbitRadius, getDensity(primary));
	}
	
	/**
	 * Gets a "density" value for a stellar object to estimate its mass. 
	 * Returns 0.5 for stars and gas giants, 8 for neutron star and Nexerelin's dark star, 16 for black holes, and 0 for nebula centers. Returns 2 for everything else.
	 * @param primary The orbit focus of the object whose orbit we want to set.
	 * @return Estimated primary density.
	 */
	public static float getDensity(SectorEntityToken primary)
	{
		if (primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)primary;
			if (planet.getSpec().isNebulaCenter()) return 0;
			if (planet.getSpec().isBlackHole()) return 16;
			if (planet.getTypeId().equals("star_dark") || planet.getSpec().isPulsar()) return 8;
			else if (planet.isStar()) return 0.5f;
			else if (planet.isGasGiant()) return 0.5f;
		}
		return 2;
	}
	
	/**
	 * Returns a random float between 0.0 and 360.0
	 * @return random angle
	 */
	public static float getRandomAngle()
	{
		return MathUtils.getRandomNumberInRange(0f, 360f);
	}
	
	/**
	 * Returns a random float between 0.0 and 360.0
	 * @param rand
	 * @return random angle
	 */
	public static float getRandomAngle(Random rand)
	{
		return rand.nextFloat() * 360f;
	}
	
	/**
	 * @param entity Token whose orbit is to be set.
	 * @param primary Token to orbit around.
	 * @param orbitRadius
	 * @param isEllipse
	 * @param ellipseAngle
	 * @param orbitPeriod
	 * @return The starting angle on the orbit
	 */
	public static void setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, 
			boolean isEllipse, float ellipseAngle, float orbitPeriod)
	{
		setOrbit(entity, primary, orbitRadius, getRandomAngle(), 
				isEllipse, ellipseAngle, MathUtils.getRandomNumberInRange(1f, 1.2f), orbitPeriod);
	}
	
	/**
	 * @param entity Token whose orbit is to be set.
	 * @param primary Token to orbit around.
	 * @param orbitRadius
	 * @param angle The angle (in degrees) that the orbit will begin at.
	 *						0 degrees = right - this is not relative to
	 *						{@code ellipseAngle}.
	 * @param isEllipse
	 * @param ellipseAngle
	 * @param ellipseMult Multiplies radius to get semi-major axis,
	 *						divides radius to get semi-minor axis.
	 * @param orbitPeriod
	 * @param type 1 == pointing down, 2 == spin, anything else = normal (only works for non-elliptical orbits)
	 * @param spinTime
	 */
	public static void setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, float angle, 
			boolean isEllipse, float ellipseAngle, float ellipseMult, float orbitPeriod, int type, float spinTime)
	{
		angle = MathUtils.clampAngle(angle);
		if (isEllipse)
		{
			float semiMajor = (int)(orbitRadius * ellipseMult);
			float semiMinor = (int)(orbitRadius / ellipseMult);
			EllipticalOrbit ellipseOrbit = new EllipticalOrbit(primary, angle, semiMinor, semiMajor, ellipseAngle, orbitPeriod);
			entity.setOrbit(ellipseOrbit);
		}
		else
		{
			if (type == 1)
				entity.setCircularOrbitPointingDown(primary, angle, orbitRadius, orbitPeriod);
			else if (type == 2)
				entity.setCircularOrbitWithSpin(primary, angle, orbitRadius, orbitPeriod, spinTime, spinTime);
			else
				entity.setCircularOrbit(primary, angle, orbitRadius, orbitPeriod);
		}
	}
	
	public static void setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, float angle, 
			boolean isEllipse, float ellipseAngle, float ellipseMult, float orbitPeriod)
	{
		setOrbit(entity, primary, orbitRadius, angle, isEllipse, ellipseAngle, ellipseMult, orbitPeriod, 0, 0);
	}
	
	/**
	 * Makes one entity orbit at another entity's Lagrangian points.
	 * @param orbiter The entity whose orbit is being set
	 * @param m1 Larger mass (e.g. the star)
	 * @param m2 Smaller mass (e.g. the planet)
	 * @param point 1 - 5 = L1 to L5
	 * @param m2Angle The starting angle of {@code m2} in its orbit
	 * @param m2OrbitRadius The orbit radius of {@code m2} in its orbit around {@code m1} 
	 * @param myOrbitRadius The orbit radius of {@code orbiter} in its orbit around {@code m2} (only applies to L1 and L2)
	 * @param orbitPeriod The time {@code m2} takes to orbit {@code m1} 
	 * @param isEllipse Is this orbit elliptic?
	 * @param ellipseAngle Angle of the ellipse orbit
	 * @param ellipseMult Used to calculate the ellipse's semi-major and semi-minor axes
	 * @param type 1 == pointing down, 2 == spin, anything else = normal (only works for non-elliptical orbits)
	 * @param spinTime
	 */
	public static void setLagrangeOrbit(SectorEntityToken orbiter, SectorEntityToken m1, SectorEntityToken m2, int point, 
			float m2Angle, float m2OrbitRadius, float myOrbitRadius, float orbitPeriod, boolean isEllipse, 
			float ellipseAngle, float ellipseMult, int type, float spinTime)
	{
		if (point <= 0 || point > 5)
		{
			throw new IllegalArgumentException("Point must be in range 1-5");
		}
		
		switch (point) {
			case 1:
			case 2:
				float angle = m2Angle;
				if (point == 1) angle += 180;
				setOrbit(orbiter, m2, myOrbitRadius, angle, isEllipse, ellipseAngle, ellipseMult, orbitPeriod, type, spinTime);
				break;
			case 3:
				setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + 180, isEllipse, ellipseAngle, ellipseMult, orbitPeriod, type, spinTime);
				break;
			case 4:
			case 5:
				float offset = -60;
				if (point == 5) offset = 60;

				setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + offset, isEllipse, ellipseAngle, ellipseMult, orbitPeriod, type, spinTime);
				break;
		}
	}
	public static void setLagrangeOrbit(SectorEntityToken orbiter, SectorEntityToken m1, SectorEntityToken m2, int point, 
			float m2Angle, float m2OrbitRadius, float myOrbitRadius, float orbitPeriod, boolean isEllipse, 
			float ellipseAngle, float ellipseMult)
	{
		setLagrangeOrbit(orbiter, m1, m2, point, m2Angle, m2OrbitRadius, myOrbitRadius, 
				orbitPeriod, isEllipse, ellipseAngle, ellipseMult, 0, 0);
	}
	
	public static float getCurrentOrbitAngle(SectorEntityToken primary, SectorEntityToken orbiter)
	{
		return Misc.getAngleInDegrees(orbiter.getLocation(), primary.getLocation());
	}
	
	public static float getCurrentOrbitRadius(SectorEntityToken primary, SectorEntityToken orbiter)
	{
		return Misc.getDistance(primary.getLocation(), orbiter.getLocation());
	}
	
	/**
	 * Adds an asteroid belt and the background ring bands
	 * @param system
	 * @param planet
	 * @param numAsteroids
	 * @param orbitRadius
	 * @param width
	 * @param minOrbitDays
	 * @param maxOrbitDays
	 */
	public static void addAsteroidBelt(LocationAPI system, SectorEntityToken planet, int numAsteroids, float orbitRadius, float width, float minOrbitDays, float maxOrbitDays)
	{
		// since we can't easily store belts' orbital periods at present, make sure asteroids all orbit in the same direction
		if (minOrbitDays < 0) minOrbitDays *= -1;
		if (maxOrbitDays < 0) maxOrbitDays *= -1;
		
		system.addAsteroidBelt(planet, numAsteroids, orbitRadius, width, minOrbitDays, maxOrbitDays);
		system.addRingBand(planet, "misc", "rings1", 256f, 2, Color.white, 256f, orbitRadius + width/4, (minOrbitDays + maxOrbitDays)/2);
		system.addRingBand(planet, "misc", "rings1", 256f, 2, Color.white, 256f, orbitRadius - width/4, (minOrbitDays + maxOrbitDays)/2);
	}
	
	/**
	 * Automatically calculates ring orbit period from orbit focus and distance
	 * @param system
	 * @param focus
	 * @param category
	 * @param key
	 * @param bandWidthInTexture
	 * @param bandIndex
	 * @param color
	 * @param bandWidthInEngine
	 * @param middleRadius
	 * @param orbitPeriodMult Multiplies autocalculated orbit period
	 * @param useTerrain if true, use Terrain.RING terrain
	 * @return
	 */
	public static SectorEntityToken addRingBand(StarSystemAPI system, SectorEntityToken focus, String category, String key, 
			float bandWidthInTexture, int bandIndex, Color color, float bandWidthInEngine, float middleRadius, float orbitPeriodMult, boolean useTerrain) 
	{
		float orbitPeriod = getOrbitalPeriod(focus, middleRadius);
		if (orbitPeriod < 0) orbitPeriod *= -1;	// else different subrings might orbit in different directions
		if (useTerrain)
			return system.addRingBand(focus, category, key, bandWidthInTexture, bandIndex, color, bandWidthInEngine, middleRadius, 
					orbitPeriod * orbitPeriodMult, Terrain.RING, null);
		else
			return system.addRingBand(focus, category, key, bandWidthInTexture, bandIndex, color, bandWidthInEngine, middleRadius, 
					orbitPeriod * orbitPeriodMult);
	}
	
	public static SectorEntityToken addRingBand(StarSystemAPI system, SectorEntityToken focus, String category, String key, 
			float bandWidthInTexture, int bandIndex, Color color, float bandWidthInEngine, float middleRadius, float orbitPeriodMult) 
	{
		return addRingBand(system, focus, category, key, bandWidthInTexture, bandIndex, color, bandWidthInEngine, middleRadius, orbitPeriodMult, false);
	}
	
	public static boolean isCoreSystem(StarSystemAPI system) {
		return !system.isProcgen() || system.hasTag(ExerelinProcGen.RANDOM_CORE_SYSTEM_TAG);
	}
	
	/**
	 * Does this star system have a comm relay, or a stable location to put one?
	 * @param system
	 * @return
	 */
	public static boolean canHaveCommRelay(StarSystemAPI system) {		
		if (!system.getEntitiesWithTag(Tags.COMM_RELAY).isEmpty()) return true;
		if (!system.getEntitiesWithTag(Tags.STABLE_LOCATION).isEmpty()) return true;
		
		return false;
	}
	
	public static List<SectorEntityToken> getCapturableEntitiesAroundPlanet(SectorEntityToken primary) {
		List<SectorEntityToken> results = new ArrayList<>();
		for (SectorEntityToken token : primary.getContainingLocation().getAllEntities()) 
		{
			if (token.getCustomEntitySpec() == null) continue;
			if (token.getMarket() != null) continue;
			if (token.hasTag(Tags.OBJECTIVE)) continue;
			if (token instanceof CampaignFleetAPI) continue;
			if (token.getOrbit() == null || token.getOrbit().getFocus() != primary)
				continue;
			if (token.getFaction() != primary.getFaction())
				continue;
			results.add(token);
		}
		
		return results;
	}
	
	public static String getLocationName(LocationAPI loc, boolean includeTypeIfStar) {
		String locName = loc.getName();
		if (loc instanceof StarSystemAPI)
		{
			if (includeTypeIfStar)
				locName = ((StarSystemAPI)loc).getNameWithTypeShort();
			else
				locName = ((StarSystemAPI)loc).getNameWithTypeIfNebula();
		}
		return locName;
	}
}
