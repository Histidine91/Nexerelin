package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class IndustryClassGen {
	
	// all Starsector industry IDs that this class handles
	// e.g. Farming class handles farming and aquaculture industries
	protected final Set<String> industryIds;
		
	public IndustryClassGen(Collection<String> industryIds)
	{
		this.industryIds = new HashSet<>(industryIds);
	}
	
	public IndustryClassGen(String... industryIds)
	{
		this.industryIds = new HashSet<>(Arrays.asList(industryIds));
	}
	
	/**
	 * Gets the priority for this industry to be added as an economic industry.
	 * 
	 * When adding industries to each market, industries are added in order of priority, 
	 * up to the maximum allowed by the market size.
	 * Returning a priority of less than zero will prevent this industry from being selected.
	 * 
	 * When adding key industries to each faction's markets, one industry is added to each market
	 * in order of priority, up to the amount desired for that faction.
	 * @param entity
	 * @return
	 */
	public float getPriority(ProcGenEntity entity) {
		return 100;
	}
	
	public boolean canApply(String factionId, ProcGenEntity entity)
	{
		for (String ind : industryIds)
		{
			if (entity.market.hasIndustry(ind))
				return false;
		}
		return getPriority(entity) > 0;
	}
	
	/**
	 * Adds the industry to the entity's market.
	 * Multi-industry classes should override this method to specify exactly which industry gets added.
	 * @param entity
	 */
	public void apply(ProcGenEntity entity) {
		entity.market.addIndustry(industryIds.toArray(new String[0])[0]);
		entity.numProductiveIndustries += 1;
	}
	
	public Set<String> getIndustryIds() {
		return industryIds;
	}
	
	/**
	 * Gets the random picker weight for this industry, when used as a special industry.
	 * @param entity
	 * @return
	 */
	public float getSpecialWeight(ProcGenEntity entity) {
		return 1;
	}
	
	public boolean isSpecial() {
		return false;
	}
}
