package exerelin.campaign.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreAdminPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import exerelin.campaign.skills.NexSkills;
import exerelin.utilities.StringHelper;

public class AICoreAdminPluginOmega implements AICoreAdminPlugin {
	
	public PersonAPI createPerson(String aiCoreId, String factionId, long seed) {
		PersonAPI person = Global.getFactory().createPerson();
		person.setFaction(factionId);
		person.setAICoreId(aiCoreId);
		String commodityName = StringHelper.getCommodityName(aiCoreId);
		person.setName(new FullName(commodityName, "", FullName.Gender.ANY));
		person.setPortraitSprite("graphics/portraits/characters/omega.png");
		
		person.setRankId(null);
		person.setPostId(Ranks.POST_ADMINISTRATOR);
		
//		person.getStats().setSkillLevel(Skills.PLANETARY_OPERATIONS, 1);
//		person.getStats().setSkillLevel(Skills.SPACE_OPERATIONS, 1);
		person.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
		person.getStats().setSkillLevel(NexSkills.AUXILIARY_SUPPORT_EX, 1);
		person.getStats().setSkillLevel(NexSkills.BULK_TRANSPORT_EX, 1);
		person.getStats().setSkillLevel(NexSkills.TACTICAL_DRILLS_EX, 1);
		person.getStats().setSkillLevel(NexSkills.MAKESHIFT_EQUIPMENT_EX, 1);
		person.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);		
		
		return person;
	}
	
}
