package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

public abstract class IndustryClassGen implements Comparable {
	
	// all Starsector industry IDs that this class handles
	// e.g. Farming class handles farming and aquaculture industries
	protected final Set<String> industryIds;
	protected String id;
	protected String name;
	protected float priority;
	protected boolean special;
	@Getter	@Setter	protected transient Random random;
		
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
		return 100 * getFactionMult(entity);
	}
	
	protected float getFactionMult(ProcGenEntity entity) {
		NexFactionConfig conf = NexConfig.getFactionConfig(entity.market.getFactionId());
		return conf.getIndustryTypeMult(id);
	}
	
	public boolean canApply(ProcGenEntity entity)
	{
		if (alreadyExists(entity)) return false;
		return true;
	}
	
	/**
	 * If false, will not appear on its own and must be specified in the faction's industry seeds.
	 * @return
	 */
	public boolean canAutogen()
	{
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
	
	public float getCountWeightModifier(float divisorMult) {
		int existingCount = NexMarketBuilder.countIndustries(this.id);
		if (existingCount == 0) return 2;
		
		int numMarkets = Global.getSector().getEconomy().getNumMarkets();
		float countMult = numMarkets/(float)(existingCount + 1);
		countMult /= divisorMult;
		if (countMult > 2) countMult = 2;
		
		//Global.getLogger(this.getClass()).info(String.format("Current %s count: %s; "
		//		+ "num markets: %s; mult: %s", this.id, existingCount, numMarkets, countMult));
		
		return countMult;
	}
	
	/**
	 * Adds the industry to the entity's market.
	 * Multi-industry classes should override this method to specify exactly which industry gets added.
	 * Not used for faction industry seeds, those add the industry by ID directly.
	 * @param entity
	 * @param instant If false, industry starts construction
	 */
	public void apply(ProcGenEntity entity, boolean instant) {
		String id = industryIds.toArray(new String[0])[0];
		NexMarketBuilder.addIndustry(entity.market, id, this.id, instant);
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
		IndustryClassGen gen = (IndustryClassGen) NexUtils.instantiateClassByName(generatorClass);
		gen.init(id, name, priority, special);

		return (T)gen;
	}
}
