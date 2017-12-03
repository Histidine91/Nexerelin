package exerelin.world.landmarks;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import java.util.List;
import java.util.Random;

public abstract class LandmarkDef {
	
	protected Random random;
	
	public abstract boolean isApplicableToEntity(SectorEntityToken entity);
	
	public abstract List<SectorEntityToken> getRandomLocations();
	
	/**
	 * Does this landmark hang around in space instead of orbiting at a particular entity?
	 * @return
	 * @deprecated I don't actually have a use for the getter right now
	 */
	@Deprecated
	public abstract boolean isFreeFloating();
	
	/**
	 * How many of this landmark should exist in the Sector?
	 * @return
	 */
	public abstract int getCount();
	
	public abstract Random getRandom();
	public abstract void setRandom(Random random);
	
	/**
	 * Creates the landmark at the specified entity.
	 * This is the method that should do the actual spawning work.
	 * @param entity The entity around which the landmark should spawn.
	 */
	public abstract void createAt(SectorEntityToken entity);

	/**
	 * Creates all instances of this landmark that should exist.
	 */
	public abstract void createAll();
}
