package exerelin.skills.scripts;

import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import exerelin.ExerelinData;
import exerelin.SectorManager;

public class ActiveDiplomacyPerk1 implements CharacterStatsSkillEffect {

    public void apply(MutableCharacterStatsAPI stats, String id, float level)
    {
        if(ExerelinData.getInstance().getSectorManager() != null)
        {
            SectorManager.getCurrentSectorManager().getSectorEventManager().triggerEvent("saboteur");
            SectorManager.getCurrentSectorManager().getSectorEventManager().triggerEvent("saboteur");
        }
    }

    public void unapply(MutableCharacterStatsAPI stats, String id)
    {
    }

	public String getEffectDescription(float level) {
		//return "+" + (int)(ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_PERK_NEW_ITEM) + "% travel speed";
        return "Sabateur special agent available";
	}
	
	public String getEffectPerLevelDescription() {
		//return "" + (int)(ExerelinSkillData.FACTION_ACTIVEDIPLOMACY_PERK_NEW_ITEM) + "%";
        return "";
	}

	public ScopeDescription getScopeDescription() {
		return ScopeDescription.ALL_OUTPOSTS;
	}

}
