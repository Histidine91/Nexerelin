package exerelin.campaign.colony;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

public class IndustrialColonyTargetValuator extends ColonyTargetValuator {
	
	@Override
	public float getConditionValue(String conditionId, FactionAPI faction) {
		float value = super.getConditionValue(conditionId, faction);
		
		if (conditionId.contains("farmland_") || conditionId.equals(Conditions.WATER_SURFACE))
			value *= 0.75f;
		else if (conditionId.startsWith("ore_") || conditionId.startsWith("rare_ore_"))
			value *= 1.5f;
		
		return value;
	}
	
	@Override
	public float getHazardDivisorMult(FactionAPI faction) {
		return 1.5f;
	}
}
