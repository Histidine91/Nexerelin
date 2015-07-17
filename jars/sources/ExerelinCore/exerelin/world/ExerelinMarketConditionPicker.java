package exerelin.world;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;

/*
/// HOW IT WORKS:
/// Each market picks from a subset of possible market conditions n times.
/// Filter out ones that don't meet our requirements (out of allowed population range, banned on current market type, etc.).
/// Also filter out any conditions we already have if we don't allow duplicates for those.
/// From the remaining list, pick a condition using a WeightedRandomPicker. 
/// After picking a condition, add it to the market. Remove it from list of pickables if it doesn't allow duplicates. 
/// Repeat numConditions times.
///
/// CONDITIONS
/// Size 2: 1 primary cond, 0.5 special conds
/// Size 3: 1 primary cond, 1 primary/industry cond, 1 special cond
/// Size 4: 2 primary conds, 1 industry cond, 1 special cond
/// Size 5: 2 primary conds, 1 industry conds, 1 primary/industry cond, 1 heavy industry cond, 2 special conds
/// Size 6: 2 primary conds, 2 industry conds, 1 primary/industry cond, 1 heavy industry cond, 2 special conds
/// Size 7: 3 primary conds, 3 industry conds, 1 heavy industry cond, 1 industry/heavy industry cond, 2 special conds


*/

@SuppressWarnings("unchecked")
class MarketConditionDef
{
	String name;
	private float chance;
	private int minSize;
	private int maxSize;
	boolean allowDuplicates;
	boolean allowStations = true;
	public final List<String> allowedPlanets = new ArrayList<>();
	public final List<String> disallowedPlanets  = new ArrayList<>();
	
	public MarketConditionDef(String name, float chance)
	{
		this(name, chance, 0, 99, true);
	}
	
	public MarketConditionDef(String name, float chance, int minSize)
	{
		this(name, chance, minSize, 99, true);
	}
	
	public MarketConditionDef(String name, float chance, int minSize, boolean allowDuplicates)
	{
		this(name, chance, minSize, 99, allowDuplicates);
	}
	
	public MarketConditionDef(String name, float chance, int minSize, int maxSize)
	{
		this(name, chance, minSize, maxSize, true);
	}
	
	public MarketConditionDef(String name, float chance, int minSize, int maxSize, boolean allowDuplicates)
	{
		this.name = name;
		this.chance = chance;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.allowDuplicates = allowDuplicates;
	}	
	
	public void setMaxSize(int size)
	{
		maxSize = size;
	}
	
	public void setAllowDuplicates(boolean dupe)
	{
		allowDuplicates = dupe;
	}
		public void setAllowStations(boolean station)
	{
		allowStations = station;
	}
	
	public String getName()
	{ return name; }
	public int getMinSize() 
	{ return minSize; }
	public int getMaxSize() 
	{ return maxSize; }
	public boolean getAllowStations() 
	{ return allowStations;	}
	public boolean getAllowDuplicates() 
	{ return allowDuplicates; }
	public float getChance()	// hee hee
	{ return chance; }
}

@SuppressWarnings("unchecked")
public class ExerelinMarketConditionPicker
{
	public static Logger log = Global.getLogger(ExerelinMarketConditionPicker.class);
	//private List possibleMarketConditions;
	
	private final List<MarketConditionDef> primaryResourceConds = new ArrayList<>();
	private final List<MarketConditionDef> industryConds = new ArrayList<>();
	private final List<MarketConditionDef> heavyIndustryConds = new ArrayList<>();
	private final List<MarketConditionDef> specialConds = new ArrayList<>();
	
	public ExerelinMarketConditionPicker()
	{
		MarketConditionDef cond;
		
		// size 2
		primaryResourceConds.add(new MarketConditionDef("ore_complex", 1.4f, 2));
		primaryResourceConds.add(new MarketConditionDef("volatiles_complex", 1f, 2));
		primaryResourceConds.add(new MarketConditionDef("organics_complex", 1f, 2));
		specialConds.add(new MarketConditionDef("outpost", 1f, 2, 4, false));
		specialConds.add(new MarketConditionDef("cryosanctum", 0.5f, 2, 4, false));
		specialConds.add(new MarketConditionDef("volatiles_depot", 0.6f, 2));
		
		cond = new MarketConditionDef("cottage_industry", 0.6f, 2, false);
		cond.setAllowStations(false);
		primaryResourceConds.add(cond);
		cond = new MarketConditionDef("exerelin_hydroponics", 1f, 2);
		disallowFertileWorlds(cond);
		primaryResourceConds.add(cond);

		// size 3
		industryConds.add(new MarketConditionDef("ore_refining_complex", 1.3f, 3));
		//industryConds.add(new MarketConditionDef("ssp_light_fuel_production", 0.8f, 3));
		industryConds.add(new MarketConditionDef("light_industrial_complex", 1f, 3, false));
		//industryConds.add(new MarketConditionDef("exerelin_cloning_vats", 1f, 3));
		specialConds.add(new MarketConditionDef("vice_demand", 0.8f, 3));
		specialConds.add(new MarketConditionDef("dissident", 0.6f, 3, 5, false));
		specialConds.add(new MarketConditionDef("stealth_minefields", 0.7f, 3, 5, false));
		specialConds.add(new MarketConditionDef("military_base", 1.1f, 4, false));
		
		cond = new MarketConditionDef("volturnian_lobster_pens", 1f, 3);
		cond.setAllowStations(false);
		industryConds.add(cond);
		allowWateryWorlds(cond);
		cond = new MarketConditionDef("aquaculture", 1f, 3, false);
		cond.setAllowStations(false);
		allowWateryWorlds(cond);
		primaryResourceConds.add(cond);
		
		// size 4
		industryConds.add(new MarketConditionDef("exerelin_recycling_plant", 0.5f, 4));
		industryConds.add(new MarketConditionDef("trade_center", 0.8f, 4, false));
		industryConds.add(new MarketConditionDef("spaceport", 0.9f, 4));
		specialConds.add(new MarketConditionDef("organized_crime", 0.7f, 4));
		specialConds.add(new MarketConditionDef("large_refugee_population", 0.7f, 4, false));
		cond = new MarketConditionDef("urbanized_polity", 0.7f, 3, false);
		cond.setAllowStations(false);
		specialConds.add(cond);
		
		// size 5
		heavyIndustryConds.add(new MarketConditionDef("autofac_heavy_industry", 1f, 5));
		heavyIndustryConds.add(new MarketConditionDef("antimatter_fuel_production", 1f, 5));
		heavyIndustryConds.add(new MarketConditionDef("shipbreaking_center", 0.7f, 5, false));
		
		// size 6
	}
	
	private void allowWateryWorlds(MarketConditionDef cond)
	{
		cond.allowedPlanets.add("terran");
		cond.allowedPlanets.add("water");
		cond.allowedPlanets.add("jungle");
	}
	
	private void disallowFertileWorlds(MarketConditionDef cond)
	{
		cond.disallowedPlanets.add("terran");
		cond.disallowedPlanets.add("water");
		cond.disallowedPlanets.add("jungle");
		//cond.inallowedPlanets.add("arid");
	}
	
	private void tryAddMarketCondition(MarketAPI market, List possibleConds, int size, String planetType, boolean isStation)
	{
		tryAddMarketCondition(market, possibleConds, 1, size, planetType, isStation);
	}
		
	private void tryAddMarketCondition(MarketAPI market, List<MarketConditionDef> possibleConds, int count, int size, String planetType, boolean isStation)
	{
		WeightedRandomPicker<MarketConditionDef> picker = new WeightedRandomPicker<>();
		int numConds = 0;
		for (MarketConditionDef possibleCond : possibleConds) 
		{
			if (possibleCond.getMinSize() >= size || possibleCond.getMaxSize() <= size) continue;
			if (!possibleCond.getAllowStations() && isStation) continue;
			if (!possibleCond.getAllowDuplicates() && market.hasCondition(possibleCond.name)) continue;
			if (!possibleCond.allowedPlanets.isEmpty())
			{
				if (!possibleCond.allowedPlanets.contains(planetType))
					continue;
			}
			if (!possibleCond.disallowedPlanets.isEmpty())
			{
				if (possibleCond.disallowedPlanets.contains(planetType))
					continue;
			}
			
			picker.add(possibleCond, possibleCond.getChance());
			numConds++;
		}
		
		if (numConds == 0)
			return;	// out of possible conditions; nothing more to do
		
		int numAdded = 0;
		while (numAdded < count)
		{
			MarketConditionDef cond = picker.pick();
			if (cond == null) break;
			String name = cond.getName();
			if (cond.getAllowDuplicates())
			{
				for (int i=numAdded; i<count; i++)
				{
					market.addCondition(cond.name);
					numAdded++;
				}
			}
			else
			{
				market.addCondition(cond.name);
				picker.remove(cond);
				numAdded++;
			}
			log.info("\tCondition added: " + name);
		}
	}
	
	public void addMarketConditions(MarketAPI market, int size, String planetType, boolean isStation)
	{
		log.info("Processing market conditions for " + market.getPrimaryEntity().getName() + " (" + market.getFaction().getDisplayName() + ")");
		
		// add primary resource conditions
		if (size >= 4)
			tryAddMarketCondition(market, primaryResourceConds, 2, size, planetType, isStation);
		else
			tryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
		if (size >= 7)
			tryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
		
		// add industry
		if (size >= 5)
			tryAddMarketCondition(market, industryConds, 2, size, planetType, isStation);
		else if (size == 4)
			tryAddMarketCondition(market, industryConds, size, planetType, isStation);
		
		if (size >= 7)
			tryAddMarketCondition(market, industryConds, size, planetType, isStation);
				
		// add heavy industry
		if (size >= 5)
			tryAddMarketCondition(market, heavyIndustryConds, size, planetType, isStation);
		
		// add primary OR industry
		if (size == 3 || size == 5 || size == 6)
		{
			if (MathUtils.getRandomNumberInRange(0, 1) == 0)
				tryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
			else
				tryAddMarketCondition(market, industryConds, size, planetType, isStation);
		}
				
		// add industry OR heavy industry
		if (size == 7)
		{
			if (MathUtils.getRandomNumberInRange(0, 1) == 0)
				tryAddMarketCondition(market, industryConds, size, planetType, isStation);
			else
				tryAddMarketCondition(market, heavyIndustryConds, size, planetType, isStation);
		}
				
		// add special
		if (size == 2)
		{
			if (MathUtils.getRandomNumberInRange(0, 1) == 0)
				tryAddMarketCondition(market, specialConds, size, planetType, isStation);
		}
		else if (size >= 3)
		{
			tryAddMarketCondition(market, specialConds, size, planetType, isStation);
			if (size >= 5)
				tryAddMarketCondition(market, specialConds, size, planetType, isStation);
		}
	}
}