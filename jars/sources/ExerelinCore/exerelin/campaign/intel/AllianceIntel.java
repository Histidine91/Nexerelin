package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AllianceIntel extends BaseIntelPlugin {
	private static Logger log = Global.getLogger(AllianceIntel.class);
	
	// everything protected out of habit
	protected FactionAPI faction1;
	protected FactionAPI faction2;
	protected String allianceId;
	// more permanent store, since alliance will no longer be accessible by ID once dissolved
	// or maybe alliances should just have more persistency?
	protected String allianceName;	
	protected UpdateType updateType;

	public AllianceIntel(FactionAPI faction1, FactionAPI faction2, String allianceId, String allianceName) {
		log.info("Creating Alliance Intel");
		this.faction1 = faction1;
		this.faction2 = faction2;
		this.allianceId = allianceId;
		this.allianceName = allianceName;
		this.updateType = UpdateType.FORMED;
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	@Override
	public String getSortString() {
		//what does this string do?
		return "Alliances";
	}
	
	protected String getString(String id)
	{
		return StringHelper.getString("exerelin_alliances", id);
	}
	
	@Override
	//This is the message info that shows on the left
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		if (listInfoParam != null)
		{
			Map<String, Object> params = (Map<String, Object>)listInfoParam;
			updateType = (UpdateType)params.get("type");
			faction1 = (FactionAPI)params.get("faction1");
		}
		
		Color c = getTitleColor(mode);
		float pad = 0f;

		info.addPara(getName(), c, pad);

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		bullet(info);
		String str, sub1, sub2;
		
		// TODO: write strength bullet points as well
		switch (updateType) {
			case FORMED:
				addFactionNamePara(info, pad, tc, faction1);
				addFactionNamePara(info, pad, tc, faction2);
				
				sub1 = AllianceManager.getAllianceByUUID(allianceId).getAllianceMarketSizeSum() + "";
				str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelStrengthPointShort", 
						"$size", sub1);
				info.addPara(str, pad, tc, hl, sub1);
				
				break;
			case JOINED:
			case LEFT:
				sub1 = Misc.ucFirst(faction1.getDisplayName());
				str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
						updateType == UpdateType.JOINED ? "intelJoinedPoint" : "intelLeftPoint", 
						"$Faction", sub1);
				info.addPara(str, pad, tc, faction1.getBaseUIColor(), sub1);
				
				sub1 = AllianceManager.getAllianceByUUID(allianceId).getMembersCopy().size() + "";
				sub2 = AllianceManager.getAllianceByUUID(allianceId).getAllianceMarketSizeSum() + "";
				str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelStrengthPoint", 
						"$num", sub1);
				str = StringHelper.substituteToken(str, "$size", sub2);
				info.addPara(str, pad, tc, hl, sub1, sub2);
				break;
			case DISSOLVED:
				//str = StringHelper.getString(Misc.ucFirst("dissolved"));
				//info.addPara(str, pad);
				break;
		}		
	}

	protected static void addFactionNamePara(TooltipMakerAPI info, float pad, Color color, FactionAPI faction) {
		String name = faction.getDisplayName();
		info.addPara(name, pad, color, faction.getBaseUIColor(), name);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;

		String str = "";
		Color h = Misc.getHighlightColor();
		
		if (updateType == UpdateType.DISSOLVED)
		{
			str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
					"intelDissolvedDesc", "$alliance", allianceName);
			info.addPara(str, opad);
			return;
		}
		
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		if (alliance == null)
		{
			info.addPara("ERROR: Unable to find alliance " + allianceName + "!", opad);
			return;
		}
		// header
		String numMembers = alliance.getMembersCopy().size() + "";
		String numMarkets = alliance.getNumAllianceMarkets() + "";
		String size = alliance.getAllianceMarketSizeSum() + "";
		Map<String, String> replace = new HashMap<>();
		replace.put("$alliance", alliance.getName());
		replace.put("$numMembers", numMembers);
		replace.put("$numMarkets", numMarkets);
		replace.put("$marketSizeSum", size);
		str = StringHelper.getStringAndSubstituteTokens("exerelin_alliances", "intelDesc", replace);
		info.addPara(str, opad, h, alliance.getName(), numMembers, numMarkets, size);
		
		str = StringHelper.getString("exerelin_alliances", "intelMembersHeader");
		info.addSectionHeading(str, Alignment.MID, opad);
		
		// TODO: print members
		for (String factionId : alliance.getMembersCopy())
		{
			printMemberInfo(info, factionId);
		}
	}
	
	protected void printMemberInfo(TooltipMakerAPI info, String factionId)
	{
		float opad = 10f;
		Color hl = Misc.getHighlightColor();
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		String str = StringHelper.getString("exerelin_alliances", "intelMemberEntry");
		String name = Misc.ucFirst(faction.getDisplayName());
		String num = ExerelinUtilsFaction.getFactionMarkets(factionId).size() + "";
		String sizeSum = ExerelinUtilsFaction.getFactionMarketSizeSum(factionId) + "";
		Map<String, String> sub = new HashMap<>();
		sub.put("$faction", name);
		sub.put("$num", num);
		sub.put("$size", sizeSum);
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(name, num, sizeSum);
		para.setHighlightColors(faction.getBaseUIColor(), hl, hl);
	}

	protected String getName() {
		String str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelTitle", "$name", allianceName);
		if (updateType == UpdateType.FORMED)
		{
			str += " - " + StringHelper.getString("formed", true);
		}
		else if (updateType == UpdateType.DISSOLVED)
		{
			str += " - " + StringHelper.getString("dissolved", true);
		}
		
		return str;
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add("Alliances");
		tags.add(faction1.getId());
		if(faction2 != null) tags.add(faction2.getId());
		return tags;
	}

	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "alliance");
	}

	@Override
	protected void notifyEnded() {
		super.notifyEnded();
		Global.getSector().removeScript(this);
	}
	
	public enum UpdateType {
		DISSOLVED,
		FORMED,
		JOINED,
		LEFT
	}
}
