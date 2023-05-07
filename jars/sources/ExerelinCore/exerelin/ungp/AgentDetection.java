package exerelin.ungp;

import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import exerelin.utilities.NexUtilsMath;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings;

@Deprecated
public class AgentDetection extends UNGP_BaseRuleEffect {
    protected float bonus;

    @Override
    public void updateDifficultyCache(int difficulty) {
        bonus = getValueByDifficulty(0, difficulty);
    }

    // 10-50%
    @Override
    public float getValueByDifficulty(int index, int difficulty) {
		float denominator = UNGP_SpecialistSettings.MAX_DIFFICULTY - 1;
		bonus = 0.1f + NexUtilsMath.lerp(0, 0.4f, (difficulty-1)/denominator);
        if (index == 0) return bonus;
		if (index == 1) return bonus * 2;
        return 0;
    }
	
	//@Override
	public void applyPlayerCharacterStats(MutableCharacterStatsAPI stats) {
		stats.getDynamic().getStat("nex_agent_detectionChance").modifyMult(rule.getBuffID(), 1+bonus, rule.getName());
		stats.getDynamic().getStat("nex_agent_injuryChance").modifyMult(rule.getBuffID(), 1+bonus*2, rule.getName());
	}

	//@Override
	public void unapplyPlayerCharacterStats(MutableCharacterStatsAPI stats) {
		stats.getDynamic().getStat("nex_agent_detectionChance").unmodify(rule.getBuffID());
        stats.getDynamic().getStat("nex_agent_injuryChance").unmodify(rule.getBuffID());
	}

    @Override
    public String getDescriptionParams(int index) {
        if (index <= 1) return getPercentString(bonus * 100f);
        return null;
    }

    @Override
    public String getDescriptionParams(int index, int difficulty) {
        if (index <= 1) return getPercentString(getValueByDifficulty(index, difficulty) * 100f);
        return null;
    }

}
