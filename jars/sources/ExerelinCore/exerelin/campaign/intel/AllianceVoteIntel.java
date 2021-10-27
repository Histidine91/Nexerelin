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
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Warning: Will break if alliance by ID ceases to be accessible
public class AllianceVoteIntel extends BaseIntelPlugin {
	
	protected AllianceVoter.VoteResult result;
	protected String allianceId;
	protected String otherPartyId;
	protected boolean otherPartyIsAlliance = false;
	protected boolean isWar = false;
	protected String stage;
	
	public AllianceVoteIntel(String allianceId, AllianceVoter.VoteResult result, String otherPartyId,
			boolean otherPartyIsAlliance, boolean isWar)
	{
		this.allianceId = allianceId;
		this.result = result;
		this.otherPartyId = otherPartyId;
		this.otherPartyIsAlliance = otherPartyIsAlliance;
		this.isWar = isWar;
	}
	
	protected FactionAPI getFaction(String id)
	{
		return Global.getSector().getFaction(id);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		Color h = result.success ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		
		// $warOrPeace with $otherParty: $passOrFail
		String warOrPeace = StringHelper.getString(isWar ? "war" : "peace", true);
		String passOrFail = StringHelper.getString(result.success ? "pass" : "fail", true);
		String otherParty;
		Color otherPartyCol;
		if (otherPartyIsAlliance)
		{
			otherParty = AllianceManager.getAllianceByUUID(otherPartyId).getName();
			otherPartyCol = Misc.getHighlightColor();
		}
		else {
			otherParty = NexUtilsFaction.getFactionShortName(otherPartyId);
			otherPartyCol = Global.getSector().getFaction(otherPartyId).getBaseUIColor();
		}
		Map<String, String> sub = new HashMap<>();
		sub.put("$warOrPeace", warOrPeace);
		sub.put("$otherParty", otherParty);
		sub.put("$passOrFail", passOrFail);
				
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_alliances", "intelVoteBullet", sub);
		LabelAPI para = info.addPara(str, tc, initPad);
		para.setHighlight(otherParty, passOrFail);
		para.setHighlightColors(otherPartyCol, h);
	}
	
	@Override
	public Color getTitleColor(ListInfoMode mode) {
		return Misc.getBasePlayerColor();
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		AllianceIntel.printMemberCrests(info, alliance, width, opad);
		
		String strKey;
		if (result.success)	{
			strKey = isWar ? "intelDescWarYes" : "intelDescPeaceYes";
		}
		else {
			strKey = isWar ? "intelDescWarNo" : "intelDescPeaceNo";
		}
		String str = StringHelper.getString("exerelin_alliances", strKey);
		
		String otherParty, otherPartyHl;
		Color otherPartyCol;
		if (otherPartyIsAlliance)
		{
			otherParty = AllianceManager.getAllianceByUUID(otherPartyId).getName();
			otherPartyHl = otherParty;
			otherPartyCol = h;
		}
		else {
			FactionAPI other = Global.getSector().getFaction(otherPartyId);
			otherParty = other.getDisplayNameWithArticle();
			otherPartyHl = other.getDisplayNameWithArticleWithoutArticle();
			otherPartyCol = other.getBaseUIColor();
		}
		
		Color warPeaceHl = Misc.getTextColor();	// no highlight, unless vote was successful
		if (result.success) warPeaceHl = isWar ? Misc.getNegativeHighlightColor() :  Misc.getPositiveHighlightColor();
		
		str = StringHelper.substituteToken(str, "$alliance", alliance.getName());
		str = StringHelper.substituteToken(str, "$theOtherParty", otherParty);
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(alliance.getName(), StringHelper.getString(isWar ? "war" : "peace"), otherPartyHl);
		para.setHighlightColors(h, warPeaceHl, otherPartyCol);
		
		// defiers
		addDefiancePara(info, opad);
		
		info.addSectionHeading(StringHelper.getString("exerelin_alliances", "intelVoteBreakdownHeader"),
				Alignment.MID, opad);
		
		addVotePara(info, "yes", result.yesVotes);
		addVotePara(info, "no", result.noVotes);
		addVotePara(info, "abstain", result.abstentions);
		
		// days ago
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
	}
	
	protected void addVotePara(TooltipMakerAPI info, String type, Set<String> voters)
	{
		float opad = 10f;
		
		if (voters.isEmpty())
			return;
		
		List<String> highlights = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		List<String> factionNames = new ArrayList<>();
		
		highlights.add(voters.size() + "");
		colors.add(Misc.getHighlightColor());
		
		for (String factionId : voters)
		{
			String name = NexUtilsFaction.getFactionShortName(factionId);
			highlights.add(name);
			colors.add(Global.getSector().getFaction(factionId).getBaseUIColor());
			factionNames.add(name);
		}
		
		String str = StringHelper.getString(type, true) + ": " + voters.size() + " (";
		str += StringHelper.writeStringCollection(factionNames, false, true);
		str += ")";
		
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(highlights.toArray(new String[0]));
		para.setHighlightColors(colors.toArray(new Color[0]));
	}
	
	protected void addDefiancePara(TooltipMakerAPI info, float padding)
	{
		if (result.defied.isEmpty())
			return;
		
		String stringKey = (result.defied.size() > 1 ? "defy" : "defies") + "Decision";
		stringKey += isWar ? "War" : "Peace";
		
		String str = StringHelper.getString("exerelin_alliances", stringKey);
		
		List<String> defiers = new ArrayList<>();
		List<Color> hlColors = new ArrayList<>();
		for (String defier : result.defied)
		{
			defiers.add(NexUtilsFaction.getFactionShortName(defier));
			hlColors.add(Global.getSector().getFaction(defier).getBaseUIColor());
		}
		str = StringHelper.substituteToken(str, "$defying", StringHelper.writeStringCollection(defiers, true, true));
		
		LabelAPI para = info.addPara(str, padding);
		para.setHighlight(defiers.toArray(new String[0]));
		para.setHighlightColors(hlColors.toArray(new Color[0]));
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = StringHelper.getString("exerelin_alliances", "intelVoteTitle");
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		str = StringHelper.substituteToken(str, "$alliance", alliance.getName());
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("alliances", true));
		tags.add(StringHelper.getString("diplomacy", true));
		if (result.success)
		{
			Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
			for (String factionId : alliance.getMembersCopy())
			{
				tags.add(factionId);
			}
		}
		return tags;
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (result.success)
			return 30;
		return 15;
	}
	
	@Override
	public String getIcon() {
		String id = "allianceVote";
		if (result.success)
			id = isWar ? "war" : "peace";
		
		return Global.getSettings().getSpriteName("intel", id);
	}
	
	@Override
	public String getSortString() {
		return "Alliance Vote";
	}
}
