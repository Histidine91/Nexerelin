package data.characters.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

public class FleetDeploymentEffect1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		return "-" + (int)(ExerelinSkillData.FACTION_FLEETDEPLOYMENT_EFFECT_RESOURCECOST_REDUCTION_PERCENTAGE * level) + "% reduced resource cost of fleet deployments";
	}
	
	public String getEffectPerLevelDescription() {
		return "" + (int)(ExerelinSkillData.FACTION_FLEETDEPLOYMENT_EFFECT_RESOURCECOST_REDUCTION_PERCENTAGE) + "%";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
