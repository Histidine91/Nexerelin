package exerelin.utilities;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import exerelin.world.ExerelinSectorGen;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.orbits.EllipticalOrbit;

public class ExerelinUtilsAstro {
	
	public static float getOrbitalPeriod(float primaryRadius, float orbitRadius, float density)
	{
		primaryRadius *= 0.01;
		orbitRadius *= 1/62.5;	// realistic would be 1/50 but the planets orbit rather too slowly then
		
		float mass = (float)Math.floor(4f / 3f * Math.PI * Math.pow(primaryRadius, 3));
		mass *= density;
		float radiusCubed = (float)Math.pow(orbitRadius, 3);
		float period = (float)(2 * Math.PI * Math.sqrt(radiusCubed/mass) * 2);
		
		if (Math.random() < ExerelinSectorGen.REVERSE_ORBIT_CHANCE) period *=-1;
		
		return period;
	}
	
	public static float getOrbitalPeriod(SectorEntityToken primary, float orbitRadius)
	{
		return getOrbitalPeriod(primary.getRadius(), orbitRadius, getDensity(primary));
	}
	
	public static float getDensity(SectorEntityToken primary)
	{
		if (primary instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)primary;
			if (planet.getTypeId().equals("star_dark")) return 8;
			else if (planet.isStar()) return 0.5f;
			else if (planet.isGasGiant()) return 0.5f;
		}
		return 2;
	}
	
	public static float getRandomAngle()
	{
		return MathUtils.getRandomNumberInRange(0f, 360f);
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
	public static float setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, 
			boolean isEllipse, float ellipseAngle, float orbitPeriod)
	{
		return setOrbit(entity, primary, orbitRadius, getRandomAngle(), 
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
	 * @return The starting angle on the orbit
	 */
	public static float setOrbit(SectorEntityToken entity, SectorEntityToken primary, float orbitRadius, float angle, 
			boolean isEllipse, float ellipseAngle, float ellipseMult, float orbitPeriod)
	{
		if (isEllipse)
		{
			float semiMajor = (int)(orbitRadius * ellipseMult);
			float semiMinor = (int)(orbitRadius / ellipseMult);
			EllipticalOrbit ellipseOrbit = new EllipticalOrbit(primary, angle, semiMinor, semiMajor, ellipseAngle, orbitPeriod);
			entity.setOrbit(ellipseOrbit);
			return angle;
		}
		else
		{
			entity.setCircularOrbit(primary, angle, orbitRadius, orbitPeriod);
			return angle;
		}
	}
	
	/**
	 * Makes one entity orbit at another entity's Lagrangian points.
	 * @param orbiter The entity whose orbit is being set
	 * @param m1 Larger mass (e.g. the star)
	 * @param m2 Smaller mass (e.g. the planet)
	 * @param point 1 - 5 = L1 to L5, other values randomize between L4 and L5
	 * @param m2Angle The starting angle of {@code m2} in its orbit
	 * @param m2OrbitRadius The orbit radius of {@code m2} in its orbit around {@code m1} 
	 * @param myOrbitRadius The orbit radius of {@code orbiter} in its orbit around {@code m2} (only applies to L1 and L2)
	 * @param orbitPeriod The time {@code m2} takes to orbit {@code m1} 
	 * @param isEllipse Is this orbit elliptic?
	 * @param ellipseAngle Angle of the ellipse orbit
	 * @param ellipseMult Used to calculate the ellipse's semi-major and semi-minor axes
	 */
	public static void setLagrangeOrbit(SectorEntityToken orbiter, SectorEntityToken m1, SectorEntityToken m2, int point, 
			float m2Angle, float m2OrbitRadius, float myOrbitRadius, float orbitPeriod, boolean isEllipse, float ellipseAngle, float ellipseMult)
	{
		if (point <= 0 || point > 5)
		{
			if (Math.random() < 0.5) point = 4;
			else point = 5;
		}
		//log.info("Setting Lagrange orbit for " + orbiter.getName() + " at point " + point);
		switch (point) {
			case 1:
			case 2:
				float angle = m2Angle;
				if (point == 1) angle += 180;
				if (!isEllipse) orbiter.setCircularOrbit(m2, angle, myOrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m2, myOrbitRadius, angle, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
			case 3:
				if (!isEllipse) orbiter.setCircularOrbit(m1, m2Angle + 180, m2OrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + 180, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
			case 4:
			case 5:
				float offset = -60;
				if (point == 5) offset = 60;

				if (!isEllipse) orbiter.setCircularOrbit(m1, m2Angle + offset, m2OrbitRadius, orbitPeriod);
				else setOrbit(orbiter, m1, m2OrbitRadius, m2Angle + offset, isEllipse, ellipseAngle, ellipseMult, orbitPeriod);
				break;
		}
	}
	
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
}
