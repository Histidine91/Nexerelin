package exerelin.campaign.colony;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

public class LuddicColonyTargetValuator extends ColonyTargetValuator {
	
	@Override
	public float getConditionValue(String conditionId, FactionAPI faction) {
		float value = super.getConditionValue(conditionId, faction);
		
		if (conditionId.contains("farmland_") || conditionId.equals(Conditions.WATER_SURFACE))
			value *= 1.5f;
		else if (conditionId.equals("US_religious"))
			value *= 2f;
		else if (conditionId.startsWith("ore_") || conditionId.startsWith("rare_ore_"))
			value *= 0.75f;
		
		return value;
	}
	
	@Override
	public float getHazardDivisorMult(FactionAPI faction) {
		return 3f;
	}
}
