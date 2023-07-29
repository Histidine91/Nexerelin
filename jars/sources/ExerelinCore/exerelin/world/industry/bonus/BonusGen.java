package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import exerelin.utilities.NexUtils;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class BonusGen {
	
	// all Starsector industry IDs that this class handles
	// e.g. AICoreFarming class handles farming and aquaculture industries
	protected final Set<String> industryIds;
	protected String id;
	protected String name;
	@Setter protected transient NexMarketBuilder marketBuilder;
		
	public BonusGen(Collection<String> industryIds)
	{
		this.industryIds = new HashSet<>(industryIds);
	}
	
	public BonusGen(String... industryIds)
	{
		this.industryIds = new HashSet<>(Arrays.asList(industryIds));
	}
	
	public void init(String id, String name)
	{
		this.id = id;
		this.name = name;
	}
	
	/**
	 * Gets the priority for this bonus to be added to this industry.
	 * One bonus is added to each industry in order of priority, up to the amount
	 * desired for that faction and bonus type.
	 * @param ind
	 * @param entity
	 * @return
	 */
	public float getPriority(Industry ind, ProcGenEntity entity) {
		return (100 + entity.market.getSize() * 10) / (1 + entity.numBonuses/2);
	}
	
	public boolean canApply(Industry ind, ProcGenEntity entity)
	{
		return industryIds.contains(ind.getId());
	}
	
	/**
	 * Adds the bonus to the specified industry.
	 * Multi-industry classes should override this method to specify exactly which industry gets added.
	 * @param ind
	 * @param entity
	 */
	public void apply(Industry ind, ProcGenEntity entity) {
		entity.numBonuses += 1;
		entity.market.reapplyConditions();
		entity.market.reapplyIndustries();
	}
	
	public Set<String> getIndustryIds() {
		return industryIds;
	}
	
	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public static <T extends BonusGen> T loadBonusGen(String id, String name, String generatorClass)
	{
		BonusGen gen = (BonusGen) NexUtils.instantiateClassByName(generatorClass);
		gen.init(id, name);

		return (T)gen;
	}
}
