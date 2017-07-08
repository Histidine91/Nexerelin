package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter.VoteResult;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class AllianceVoteEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(AllianceVoteEvent.class);
	public static final MessagePriority MESSAGE_PRIORITY = MessagePriority.ENSURE_DELIVERY;	// workaround http://fractalsoftworks.com/forum/index.php?topic=12589.0
	
	protected VoteResult result;
	protected String allianceId;
	protected String otherParty;
	protected boolean otherPartyIsAlliance = false;
	protected boolean isWar = false;
	protected String stage;
	protected float age = 0;
	protected Map<String, Object> params;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public String getEventName() {
		String allianceName = AllianceManager.getAllianceByUUID(allianceId).getName();
		return StringHelper.getStringAndSubstituteToken("exerelin_events", "allianceVote", "$alliance", allianceName);
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		result = (VoteResult)params.get("result");
		if (params.containsKey("allianceId"))
			allianceId = (String)params.get("allianceId");
		isWar = (boolean)params.get("isWar");
		otherParty = (String)params.get("otherParty");
		otherPartyIsAlliance = (boolean)params.get("otherPartyIsAlliance");
		
		stage = (result.success ? "yes" : "no") + "_" + (isWar ? "war" : "peace");
	}
	
	@Override
	public void startEvent()
	{
	}
	
	public void reportEvent(Map<String, Object> params)
	{
		setParam(params);
		SectorEntityToken location = AllianceManager.getAllianceByUUID(allianceId).getRandomAllianceMarketForEvent(true).getPrimaryEntity();
		Global.getSector().reportEventStage(this, stage, location, MESSAGE_PRIORITY);
	}
	
	
	protected String getDefianceString()
	{
		if (result.defied.isEmpty())
			return "";
		String stringKey = (result.defied.size() > 1 ? "defy" : "defies") + "Decision";
		stringKey += isWar ? "War" : "Peace";
		
		String str = StringHelper.getString("exerelin_alliances", stringKey);
		
		List<String> defiers = new ArrayList<>();
		for (String defier : result.defied)
		{
			defiers.add(ExerelinUtilsFaction.getFactionShortName(defier));
		}
		str = StringHelper.substituteToken(str, "$defying", StringHelper.writeStringCollection(defiers, true, true));
		
		if (otherPartyIsAlliance)
		{
			String allianceName = AllianceManager.getAllianceByUUID(otherParty).getName();
			String theAllianceName = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "theAlliance", "$alliance", allianceName);
			str = StringHelper.substituteToken(str, "$otherParty", allianceName);
			str = StringHelper.substituteToken(str, "$theOtherParty", theAllianceName);
			str = StringHelper.substituteToken(str, "$OtherParty", Misc.ucFirst(allianceName));
			str = StringHelper.substituteToken(str, "$TheOtherParty", Misc.ucFirst(theAllianceName));
		}
		else
		{
			FactionAPI faction = Global.getSector().getFaction(otherParty);
			String factionStr = ExerelinUtilsFaction.getFactionShortName(faction);
			String theFactionStr = faction.getDisplayNameWithArticle();
			str = StringHelper.substituteToken(str, "$otherParty", factionStr);
			str = StringHelper.substituteToken(str, "$theOtherParty", theFactionStr);
			str = StringHelper.substituteToken(str, "$OtherParty", Misc.ucFirst(factionStr));
			str = StringHelper.substituteToken(str, "$TheOtherParty", Misc.ucFirst(theFactionStr));
		}
		
		return str;
	}
	
	protected String getVoteCountString(String type, Set<String> votes)
	{
		if (votes.isEmpty()) return "";
		String str = "\n" + Misc.ucFirst(StringHelper.getString(type)) + ": " + votes.size();
		List<String> voters = StringHelper.factionIdListToFactionNameList(new ArrayList<>(votes), false);
		str += " (" + StringHelper.writeStringCollection(voters) + ")";
		
		return str;
	}
	
	// TBD
	protected String getVoteBreakdown()
	{
		String str = getVoteCountString("yes", result.yesVotes);
		str += getVoteCountString("no", result.noVotes);
		str += getVoteCountString("abstain", result.abstentions);
		return str;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		map.put("$alliance", alliance.getName());
		
		if (otherPartyIsAlliance)
		{
			String allianceName = AllianceManager.getAllianceByUUID(otherParty).getName();
			String theAllianceName = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "theAlliance", "$alliance", allianceName);
			map.put("$otherParty", allianceName);
			map.put("$theOtherParty", theAllianceName);
			map.put("$OtherParty", Misc.ucFirst(allianceName));
			map.put("$TheOtherParty", Misc.ucFirst(theAllianceName));
		}
		else
		{
			FactionAPI faction = Global.getSector().getFaction(otherParty);
			String factionStr = ExerelinUtilsFaction.getFactionShortName(faction);
			String theFactionStr = faction.getDisplayNameWithArticle();
			map.put("$otherParty", factionStr);
			map.put("$theOtherParty", theFactionStr);
			map.put("$OtherParty", Misc.ucFirst(factionStr));
			map.put("$TheOtherParty", Misc.ucFirst(theFactionStr));
		}
		if (!result.defied.isEmpty())
			map.put("$defyText", "\n" + getDefianceString());
		else
			map.put("$defyText", "");
		
		map.put("$voteBreakdown", getVoteBreakdown());
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> highlights = new ArrayList<>();
		//addTokensToList(highlights, "$theOtherParty");
		
		return highlights.toArray(new String[0]);
	}
		
	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
}