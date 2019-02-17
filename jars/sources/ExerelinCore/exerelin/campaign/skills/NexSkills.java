package exerelin.campaign.skills;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import exerelin.utilities.StringHelper;

public class NexSkills {
	
	public static class AgentBonus implements CharacterStatsSkillEffect {
		public static final int BONUS_AGENTS = 1;
		
		@Override
		public void apply(MutableCharacterStatsAPI stats, String id, float level) {
			stats.getDynamic().getStat("nex_max_agents").modifyFlat(id, BONUS_AGENTS);
		}

		@Override
		public void unapply(MutableCharacterStatsAPI stats, String id) {
			stats.getDynamic().getStat("nex_max_agents").unmodify(id);
		}
		
		@Override
		public String getEffectDescription(float level) {
			return "+" + (int) BONUS_AGENTS + " " + StringHelper.getString("nex_agents", "skillBonusAgents");
		}
		
		@Override
		public String getEffectPerLevelDescription() {
			return null;
		}

		@Override
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.NONE;
		}
	}
}
