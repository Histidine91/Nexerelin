package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.SectorManager.VictoryType;
import exerelin.campaign.ui.VictoryScreenScript.CustomVictoryParams;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VictoryIntel extends BaseIntelPlugin {
	
	public static Object BUTTON_CANCEL_VICTORY = new Object(); 
	
	protected String factionId;
	protected VictoryType type;
	protected boolean playerWon;
	protected CustomVictoryParams customparams;
	
	public VictoryIntel(String factionId, VictoryType type, boolean playerWon, CustomVictoryParams customparams)
	{
		this.factionId = factionId;
		this.type = type;
		this.playerWon = playerWon;
		this.customparams = customparams;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		if (type == VictoryType.RETIRED) return;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		String name = Misc.ucFirst(faction.getDisplayName());
		String strKey = "intelBulletFaction";
		if (type.isDiplomatic())
			strKey = "intelBulletFactionAndAllies";
		
		String str = StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", strKey, "$faction", name);
		info.addPara(str, initPad, tc, faction.getBaseUIColor(), name);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		info.addImage(faction.getLogo(), width, 128, opad);
		
		if (type == VictoryType.RETIRED)
		{
			String str = StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", 
					"intelDescRetired", "$playerName", Global.getSector().getPlayerPerson().getNameString());
			info.addPara(str, opad);
		}
		else if (customparams != null) {
			info.addPara(customparams.intelText, opad, faction.getBaseUIColor(), 
				faction.getDisplayNameWithArticleWithoutArticle());
			
			if (playerWon)
				info.addPara(StringHelper.getString("exerelin_victoryScreen", "intelStringYouWon"), opad);
		}
		else {
			String strKey = "intelDescConquest";
			if (type.isDiplomatic()) strKey = "intelDescDiplomatic";

			Map<String, String> replace = new HashMap<>();
			replace.put("$theFaction", faction.getDisplayNameWithArticle());
			replace.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			replace.put("$hasOrHave", faction.getDisplayNameHasOrHave());
			String str = StringHelper.getStringAndSubstituteTokens("exerelin_victoryScreen", 
						strKey, replace);

			info.addPara(str, opad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			
			if (playerWon)
				info.addPara(StringHelper.getString("exerelin_victoryScreen", "intelStringYouWon"), opad);
		}
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
		
		info.addButton(StringHelper.getString("exerelin_victoryScreen", "intelButtonCancel"), 
				BUTTON_CANCEL_VICTORY, width, 20, opad);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_CANCEL_VICTORY) {
			SectorManager.getManager().clearVictory();
			SectorManager.checkForVictory();
			ui.updateIntelList();
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return true;
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		prompt.addPara(StringHelper.getString("exerelin_victoryScreen", "intelButtonCancelPrompt"), 0);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String key;
		switch (type)
		{
			case CONQUEST:
			case CONQUEST_ALLY:
			case DEFEAT_CONQUEST:
				key = "intelTitleConquest";
				break;
			case DIPLOMATIC:
			case DIPLOMATIC_ALLY:
			case DEFEAT_DIPLOMATIC:
				key = "intelTitleDiplomatic";
				break;
			case RETIRED:
				key = "intelTitleRetired";
				break;
			case CUSTOM:
				return customparams.name;
			default:
				return StringHelper.getString("victory", true);
		}
		
		String str = StringHelper.getString("exerelin_victoryScreen", key);
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
		tags.add(Tags.INTEL_STORY);
		tags.add(factionId);
		return tags;
	}
	
	@Override
	public String getIcon() {
		return Global.getSector().getFaction(factionId).getCrest();
	}
	
	@Override
	public String getSortString() {
		//return "aaaa";	// put at the top - doesn't work
		return "Victory";
	}
}
