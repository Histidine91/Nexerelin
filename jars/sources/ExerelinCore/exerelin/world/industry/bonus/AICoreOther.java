package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;
import exerelin.world.industry.HeavyIndustry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AICoreOther extends AICore {
	public static final Set<String> DISALLOWED_INDUSTRIES = new HashSet<>(Arrays.asList(
			Industries.FUELPROD
	));
	
	static {
		DISALLOWED_INDUSTRIES.addAll(HeavyIndustry.HEAVY_INDUSTRY);
		DISALLOWED_INDUSTRIES.addAll(AICoreMilitary.MILITARY);
	}
	
	public AICoreOther()
	{
		super(DISALLOWED_INDUSTRIES.toArray(new String[0]));
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getSpec().hasTag(Industries.TAG_STATION))
			return false;
		return super.canApply(ind, entity);
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		return super.getPriority(ind, entity) * ind.getBaseUpkeep()/20000;
	}
}
