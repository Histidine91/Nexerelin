package data.scripts.world.exerelin;

import java.util.List;
import java.util.ArrayList;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import data.scripts.world.exerelin.*;
import exerelin.*;

/*
/// HOW IT WORKS:
/// For each market, make a copy of the list of all possible market conditions.
/// Filter out ones that don't meet our requirements (out of allowed population range, or is banned on current market type).
/// Also filter out any conditions we already have.
/// From the remaining list, calculate the normalised chance and pick the corresponding condition. 
/// After picking a condition, add it to the market and remove it from our list. 
/// Repeat numConditions times.
/// 
/// numConditions = market size
*/

@SuppressWarnings("unchecked")
class ExerelinPossibleMarketCondition	// this is the most annoyingly bloated object class name ever
{
	String name;
	private float chance;
	private int minSize;
	private int maxSize;
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
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, boolean allowStations)
	{
		this(name, chance, minSize, 99, allowStations);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, int maxSize)
	{
		this(name, chance, minSize, maxSize, true);
	}
	
	public ExerelinPossibleMarketCondition(String name, float chance, int minSize, int maxSize, boolean allowStations)
	{
		this.name = name;
		this.chance = chance;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.allowStations = allowStations;
	}	
	
	public void setMaxSize(int size)
	{
		maxSize = size;
	}
	
	public String getName()
	{ return name; }
	public int getMinSize() 
	{ return minSize; }
	public int getMaxSize() 
	{ return maxSize; }
	public boolean getAllowStations() 
	{ return allowStations;	}
	public float getChance()	// hee hee
	{ return chance; }
}

@SuppressWarnings("unchecked")
public class ExerelinMarketConditionPicker
{
	private List possibleMarketConditions;
	
	public ExerelinMarketConditionPicker()
	{
		ExerelinPossibleMarketCondition cond;
		possibleMarketConditions = new ArrayList();
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("ore_complex", 1.6f, 2));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("volatiles_complex", 1.2f, 2));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("volatiles_depot", 0.8f, 2));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("outpost", 0.8f, 2, 4));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("cryosanctum", 0.4f, 2, 4));

		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("organics_complex", 1f, 3));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("aquaculture", 0.4f, 3, false));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("light_industrial_complex", 1.2f, 3));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("ore_refining_complex", 1.2f, 3));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("stealth_minefields", 0.7f, 3, 5));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("vice_demand", 0.8f, 3));
		
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("military_base", 0.8f, 4));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("spaceport", 0.65f, 4));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("trade_center", 0.8f, 4));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("large_refugee_population", 0.8f, 4));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("organized_crime", 0.7f, 4));
	
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("shipbreaking_center", 0.6f, 5));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("autofac_heavy_industry", 0.7f, 5));
		possibleMarketConditions.add(new ExerelinPossibleMarketCondition("antimatter_fuel_production", 0.8f, 5));
	}
	
	public void AddMarketConditions(MarketAPI market, int size, String planetType, boolean isStation)
	{
		List allowedConditions = new ArrayList();
		for (int i=0; i<possibleMarketConditions.size(); i++)
		{
			ExerelinPossibleMarketCondition possibleCond = (ExerelinPossibleMarketCondition)(possibleMarketConditions.get(i));
			if (possibleCond.getMinSize() <= size && possibleCond.getMaxSize() >= size
			&& (possibleCond.getAllowStations() || !isStation)
			&& !market.hasCondition(possibleCond.name))
				allowedConditions.add(possibleCond);
		}
		
		int numConditions = size;
		//if (size > 4) numConditions++;
		
		for (int i=0; i<numConditions; i++)
		{
			if (allowedConditions.size() == 0)
				return;	// out of possible conditions; nothing more to do
			
			// normalise chance
			float sumChanceValues = 0;
			float accumulatedChance = 0;
			for (int j=0; j<allowedConditions.size() - 1; j++)
			{
				ExerelinPossibleMarketCondition cond = (ExerelinPossibleMarketCondition)(allowedConditions.get(j));
				sumChanceValues += cond.getChance();
			}
			float roll = (float)(Math.random()) * sumChanceValues;
			
			for (int j=0; j<allowedConditions.size() - 1; j++)
			{
				ExerelinPossibleMarketCondition cond = (ExerelinPossibleMarketCondition)(allowedConditions.get(j));
				accumulatedChance += cond.getChance();
				if( accumulatedChance >= roll )
				{
					market.addCondition(cond.getName());
					allowedConditions.remove(cond);
					break;
				}
			}
		}
	}
}