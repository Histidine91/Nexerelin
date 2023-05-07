package exerelin.ungp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings;

public class VengeanceBuff extends UNGP_BaseRuleEffect {
	public static final float SEARCH_TIME_MULT = 1.5f;
	public static final String MEMORY_KEY = "$nex_ungp_vengeanceBuff";

	protected float bonus;

	@Override
	public void updateDifficultyCache(int difficulty) {
		bonus = getValueByDifficulty(0, difficulty);
	}
	
	@Override
	public float getValueByDifficulty(int index, int difficulty) {
		float diffMult = (float)difficulty/ UNGP_SpecialistSettings.MAX_DIFFICULTY;
		bonus = 0.05f + 0.2f * diffMult;
		if (index == 0) return bonus;
		return 0;
	}
	
	@Override
	public void applyGlobalStats() {
		Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, bonus);
	}
	
	@Override
	public void unapplyGlobalStats() {
		Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_KEY);
	}

	@Override
	public String getDescriptionParams(int index) {
		switch (index) {
			case 0:
				return getPercentString(bonus * 100f);
			case 1:
				return getPercentString((SEARCH_TIME_MULT - 1) * 100f);
			case 2:
				return "+1";
			case 3:
				return Global.getSettings().getSkillSpec("sensors").getName();
			case 4:
				return Global.getSettings().getSkillSpec(Skills.NAVIGATION).getName();
		}
		return null;
	}

	@Deprecated @Override
	public String getDescriptionParams(int index, int difficulty) {
		if (index == 0) return getPercentString(getValueByDifficulty(index, difficulty) * 100f);
		return getDescriptionParams(index);
	}
	
	@Override
	public String getDescriptionParams(int index, UNGP_SpecialistSettings.Difficulty difficulty) {
		if (index == 0) return getPercentString(getValueByDifficulty(index, difficulty) * 100f);
		return getDescriptionParams(index);
	}

}