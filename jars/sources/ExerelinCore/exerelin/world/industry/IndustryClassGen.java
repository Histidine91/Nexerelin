package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class IndustryClassGen implements Comparable {
	
	// all Starsector industry IDs that this class handles
	// e.g. Farming class handles farming and aquaculture industries
	protected final Set<String> industryIds;
	protected String id;
	protected String name;
	protected float priority;
	protected boolean special;
		
	public IndustryClassGen(Collection<String> industryIds)
	{
		this.industryIds = new HashSet<>(industryIds);
	}
	
	public IndustryClassGen(String... industryIds)
	{
		this.industryIds = new HashSet<>(Arrays.asList(industryIds));
	}
	
	public void init(String id, String name, float priority, boolean special)
	{
		this.id = id;
		this.name = name;
		this.priority = priority;
		this.special = special;
	}
	
	/**
	 * Gets the priority tier for this industry class.
	 * @return
	 */
	public float getPriority() {
		return priority;
	}
	
	/**
	 * Gets the weight for this industry class to be added as an economic industry.
	 * 
	 * <p>When adding industries to each market, industries are added from a weighted random picker,
	 * up to the maximum allowed by the market size. Higher priority industries are selected first.
	 * Returning a weight of less than zero will prevent this industry from being selected.</p>
	 * 
	 * <p>When adding key industries to each faction's markets, one industry is added to each market
	 * in order of weight, up to the amount desired for that faction.</p>
	 * @param entity
	 * @return
	 */
	public float getWeight(ProcGenEntity entity) {
		return 100;
	}
	
	public boolean canApply(ProcGenEntity entity)
	{
		if (alreadyExists(entity)) return false;
		return true;
	}
	
	public boolean alreadyExists(ProcGenEntity entity)
	{
		for (String ind : industryIds)
		{
			if (entity.market.hasIndustry(ind))
				return true;
		}
		return false;
	}
	
	/**
	 * Adds the industry to the entity's market.
	 * Multi-industry classes should override this method to specify exactly which industry gets added.
	 * @param entity
	 * @param instant If false, industry starts construction
	 */
	public void apply(ProcGenEntity entity, boolean instant) {
		String id = industryIds.toArray(new String[0])[0];
		NexMarketBuilder.addIndustry(entity.market, id, instant);
		entity.numProductiveIndustries += 1;
	}
	
	public Set<String> getIndustryIds() {
		return industryIds;
	}
	
	public boolean isSpecial() {
		return special;
	}
	
	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public int compareTo(Object o) {
		return Float.compare(priority, ((IndustryClassGen)o).priority);
	}
	
	public static <T extends IndustryClassGen> T loadIndustryClassGen(String id, String name, float priority, String generatorClass, boolean special)
	{
		IndustryClassGen gen = null;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(generatorClass);
			gen = (IndustryClassGen)clazz.newInstance();
			gen.init(id, name, priority, special);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryClassGen.class).error("Failed to load industry class generator " + name, ex);
		}

		return (T)gen;
	}
}
