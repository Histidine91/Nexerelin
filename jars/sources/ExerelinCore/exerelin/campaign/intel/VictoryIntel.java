package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager.VictoryType;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VictoryIntel extends BaseIntelPlugin {
	
	protected String factionId;
	protected VictoryType type;
	protected boolean playerWon;
	
	public VictoryIntel(String factionId, VictoryType type, boolean playerWon)
	{
		this.factionId = factionId;
		this.type = type;
		this.playerWon = playerWon;
	}
	
	// bullet points
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		info.addPara(getName(), c, 0f);
		bullet(info);
		
		if (type == VictoryType.RETIRED) return;
		
		float pad = 3f;
		Color tc = getBulletColorForMode(mode);
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		String name = Misc.ucFirst(faction.getDisplayName());
		String strKey = "intelBulletFaction";
		if (type.isDiplomatic())
			strKey = "intelBulletFactionAndAllies";
		
		String str = StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", strKey, "$faction", name);
		info.addPara(str, pad, tc, faction.getBaseUIColor(), name);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		info.addImage(faction.getLogo(), width, 128, opad);
		
		if (type == VictoryType.RETIRED)
		{
			String str = StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", 
					"intelDescRetired", "$playerName", Global.getSector().getPlayerPerson().getNameString());
			info.addPara(str, opad);
			return;
		}
		
		String strKey = "intelDescConquest";
		if (type.isDiplomatic()) strKey = "intelDescDiplomatic";
		
		Map<String, String> replace = new HashMap<>();
		replace.put("$theFaction", faction.getDisplayNameWithArticle());
		replace.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
		replace.put("$hasOrHave", faction.getDisplayNameHasOrHave());
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_victoryScreen", 
					strKey, replace);
		
		LabelAPI para = info.addPara(str, opad, faction.getBaseUIColor(), 
				faction.getDisplayNameWithArticleWithoutArticle());
		
		if (playerWon)
			info.addPara(StringHelper.getString("exerelin_victoryScreen", "intelStringYouWon"), opad);
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
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
			default:
				return StringHelper.getString("victory", true);
		}
		
		String str = StringHelper.getString("exerelin_victoryScreen", key);
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("victory", true));
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
