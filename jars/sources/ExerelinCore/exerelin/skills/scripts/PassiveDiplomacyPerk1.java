package exerelin.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class PassiveDiplomacyPerk1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "" + (int)(ExerelinSkillData.FACTION_PASSIVEDIPLOMACY_PERK_ALLIANCE_BETRAYAL_REDUCTION_PERCENTAGE) + "% reduction in alliance betrayal chance in alliance your faction is in";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_PASSIVEDIPLOMACY_PERK_ALLIANCE_BETRAYAL_REDUCTION_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
