package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AICoreMilitary extends AICore {
	
	protected static final Set<String> MILITARY = new HashSet<>(Arrays.asList(
			Industries.GROUNDDEFENSES, Industries.HEAVYBATTERIES, Industries.MILITARYBASE, 
			Industries.PATROLHQ, Industries.MILITARYBASE, Industries.HIGHCOMMAND
	));
	
	public AICoreMilitary()
	{
		super(MILITARY.toArray(new String[0]));
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		return super.getPriority(ind, entity) * ind.getBaseUpkeep()/20000;
	}
}
