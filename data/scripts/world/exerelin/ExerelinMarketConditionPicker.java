package data.scripts.world.exerelin;

import java.util.List;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import data.scripts.world.exerelin.*;
import exerelin.*;

/*
/// HOW IT WORKS:
/// Each market picks from a subset of possible market conditions n times.
/// Filter out ones that don't meet our requirements (out of allowed population range, or is banned on current market type).
/// Also filter out any conditions we already have.
/// From the remaining list, calculate the normalised chance and pick the corresponding condition. 
/// After picking a condition, add it to the market. Remove it from list of pickables if it doesn't allow duplicates. 
/// Repeat numConditions (== market size) times.
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
class ExerelinPossibleMarketCondition	// this is the most annoyingly bloated object class name ever
{
	String name;
	private float chance;
	private int minSize;
	private int maxSize;
		boolean allowDuplicates;
	boolean allowStations;
	// todo: invalid planet types (e.g. no aquaculture on a desert world)
	// also permitted planet types (e.g. lobster could require water world)
	// some other stuff I forgot
	
	public ExerelinPossibleMarketCondition(String name, float chance)
	{
		this(name, chance, 0, 99, true);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize)
	{
		this(name, chance, minSize, 99, true);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, boolean allowDuplicates)
	{
		this(name, chance, minSize, 99, allowDuplicates);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, int maxSize)
	{
		this(name, chance, minSize, maxSize, true);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, int maxSize, boolean allowDuplicates)
	{
		this.name = name;
		this.chance = chance;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.allowDuplicates = allowDuplicates;
		this.allowStations = true;
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
	
	private List primaryResourceConds;
	private List industryConds;
	private List heavyIndustryConds;
	private List specialConds;
	
	public ExerelinMarketConditionPicker()
	{
		ExerelinPossibleMarketCondition cond;
		primaryResourceConds = new ArrayList();
		industryConds = new ArrayList();
		heavyIndustryConds = new ArrayList();
		specialConds = new ArrayList();
		
		// size 2
		primaryResourceConds.add(new ExerelinPossibleMarketCondition("ore_complex", 1.4f, 2));
		primaryResourceConds.add(new ExerelinPossibleMarketCondition("volatiles_complex", 1f, 2));
		primaryResourceConds.add(new ExerelinPossibleMarketCondition("organics_complex", 1f, 2));
		specialConds.add(new ExerelinPossibleMarketCondition("outpost", 1f, 2, 4, false));
		specialConds.add(new ExerelinPossibleMarketCondition("cryosanctum", 0.5f, 2, 4, false));
		specialConds.add(new ExerelinPossibleMarketCondition("volatiles_depot", 0.6f, 2));
		cond = new ExerelinPossibleMarketCondition("cottage_industry", 0.6f, 2, false);
		cond.setAllowStations(false);
		primaryResourceConds.add(cond);

		// size 3
		industryConds.add(new ExerelinPossibleMarketCondition("ore_refining_complex", 1.3f, 3));
		//industryConds.add(new ExerelinPossibleMarketCondition("ssp_light_fuel_production", 0.8f, 3));
		industryConds.add(new ExerelinPossibleMarketCondition("light_industrial_complex", 1f, 3, false));
		//industryConds.add(new ExerelinPossibleMarketCondition("exerelin_cloning_vats", 1f, 3));
		specialConds.add(new ExerelinPossibleMarketCondition("vice_demand", 0.8f, 3));
		specialConds.add(new ExerelinPossibleMarketCondition("dissident", 0.6f, 3, 5, false));
		specialConds.add(new ExerelinPossibleMarketCondition("stealth_minefields", 0.7f, 3, 5, false));
		specialConds.add(new ExerelinPossibleMarketCondition("military_base", 1.1f, 4, false));
		cond = new ExerelinPossibleMarketCondition("volturnian_lobster_pens", 0.5f, 3);
		cond.setAllowStations(false);
		industryConds.add(cond);
		cond = new ExerelinPossibleMarketCondition("aquaculture", 0.4f, 3, false);
		cond.setAllowStations(false);
		primaryResourceConds.add(cond);
		
		// size 4
		industryConds.add(new ExerelinPossibleMarketCondition("exerelin_recycling_plant", 0.5f, 4));
		industryConds.add(new ExerelinPossibleMarketCondition("trade_center", 0.8f, 4, false));
		industryConds.add(new ExerelinPossibleMarketCondition("spaceport", 0.9f, 4));
		specialConds.add(new ExerelinPossibleMarketCondition("organized_crime", 0.7f, 4));
		specialConds.add(new ExerelinPossibleMarketCondition("large_refugee_population", 0.7f, 4, false));
	
		// size 5
		heavyIndustryConds.add(new ExerelinPossibleMarketCondition("autofac_heavy_industry", 1f, 5));
		heavyIndustryConds.add(new ExerelinPossibleMarketCondition("antimatter_fuel_production", 1f, 5));
		
		// size 6
		heavyIndustryConds.add(new ExerelinPossibleMarketCondition("shipbreaking_center", 0.5f, 6, false));
	}
	
	private void TryAddMarketCondition(MarketAPI market, List possibleConds, int size, String planetType, boolean isStation)
	{
		TryAddMarketCondition(market, possibleConds, 1, size, planetType, isStation);
	}
		
	private void TryAddMarketCondition(MarketAPI market, List possibleConds, int count, int size, String planetType, boolean isStation)
	{
		List allowedConds = new ArrayList();
		for (int i=0; i<possibleConds.size(); i++)
		{
			ExerelinPossibleMarketCondition possibleCond = (ExerelinPossibleMarketCondition)(possibleConds.get(i));
			if (possibleCond.getMinSize() <= size && possibleCond.getMaxSize() >= size
			&& (possibleCond.getAllowStations() || !isStation)
			&& (possibleCond.getAllowDuplicates() || !market.hasCondition(possibleCond.name))
			)
				allowedConds.add(possibleCond);
		}
		
		if (allowedConds.size() == 0)
			return;	// out of possible conditions; nothing more to do
		
		// normalise chance
		float sumChanceValues = 0;
		float accumulatedChance = 0;
		for (int i=0; i<allowedConds.size(); i++)
		{
			ExerelinPossibleMarketCondition cond = (ExerelinPossibleMarketCondition)(allowedConds.get(i));
			sumChanceValues += cond.getChance();
		}
		float roll = (float)(Math.random()) * sumChanceValues;
		
		for (int i=0; i<allowedConds.size(); i++)
		{
			ExerelinPossibleMarketCondition cond = (ExerelinPossibleMarketCondition)(allowedConds.get(i));
			accumulatedChance += cond.getChance();
			if( accumulatedChance >= roll )
			{
				String name = cond.getName();
				for (int j=0; j<count; j++)
					market.addCondition(name);
				if (!cond.getAllowDuplicates())
					allowedConds.remove(cond);
				if (name == "military_base")
					market.addSubmarket(Submarkets.GENERIC_MILITARY);
				log.info("\tCondition added: " + name);
				break;
			}
		}
	}
	
	public void AddMarketConditions(MarketAPI market, int size, String planetType, boolean isStation)
	{
		log.info("Processing market conditions for " + market.getPrimaryEntity().getName() + " (" + market.getFaction().getDisplayName() + ")");
		
		// add primary resource conditions
		TryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
		if (size >= 4)
			TryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
		if (size >= 7)
			TryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
		
		// add industry
		
		if (size >= 4)
			TryAddMarketCondition(market, industryConds, size, planetType, isStation);
		if (size >= 5)
			TryAddMarketCondition(market, industryConds, size, planetType, isStation);
		if (size >= 7)
			TryAddMarketCondition(market, industryConds, size, planetType, isStation);
				
		// add heavy industry
		if (size >= 5)
			TryAddMarketCondition(market, heavyIndustryConds, size, planetType, isStation);
		
		// add primary OR industry
		if (size == 3 || size == 5 || size == 6)
		{
			if (ExerelinUtils.getRandomInRange(0, 1) == 0)
				TryAddMarketCondition(market, primaryResourceConds, size, planetType, isStation);
			else
				TryAddMarketCondition(market, industryConds, size, planetType, isStation);
		}
				
		// add industry OR heavy industry
		if (size == 7)
		{
			if (ExerelinUtils.getRandomInRange(0, 1) == 0)
				TryAddMarketCondition(market, industryConds, size, planetType, isStation);
			else
				TryAddMarketCondition(market, heavyIndustryConds, size, planetType, isStation);
		}
				
		// add special
		if (size == 2)
		{
			if (ExerelinUtils.getRandomInRange(0, 1) == 0)
				TryAddMarketCondition(market, specialConds, size, planetType, isStation);
		}
		else if (size >= 3)
		{
			TryAddMarketCondition(market, specialConds, size, planetType, isStation);
			if (size >= 5)
				TryAddMarketCondition(market, specialConds, size, planetType, isStation);
		}
	}
}