package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class AllianceChangedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(AllianceChangedEvent.class);
	public static final MessagePriority MESSAGE_PRIORITY = MessagePriority.ENSURE_DELIVERY;
	
	protected String faction1Id;
	protected String faction2Id;
	protected String allianceId;
	protected float age = 0;
	protected String stage = "formed";
	protected boolean done = false;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public String getEventName() {
		String str = "allianceChanged";
		if (stage.equals("formed")) str = "allianceFormed";
		else if (stage.equals("dissolved")) str = "allianceDissolved";
		String allianceName = AllianceManager.getAllianceByUUID(allianceId).getName();
		return StringHelper.getStringAndSubstituteToken("exerelin_events", str, "$alliance", allianceName);
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	@Override
	public void setParam(Object param) {
		Map<String, Object> params = (HashMap)param;
		faction1Id = (String)params.get("faction1Id");
		faction2Id = (String)params.get("faction2Id");
		if (params.containsKey("allianceId"))
			allianceId = (String)params.get("allianceId");
		stage = (String)params.get("stage");
	}
	
	@Override
	public void startEvent()
	{
		Global.getSector().reportEventStage(this, stage, eventTarget.getEntity(), MESSAGE_PRIORITY);
	}
	
	public void reportEvent()
	{
		Global.getSector().reportEventStage(this, stage, eventTarget.getEntity(), MESSAGE_PRIORITY);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		FactionAPI faction1 = Global.getSector().getFaction(faction1Id);
		FactionAPI faction2 = Global.getSector().getFaction(faction2Id);
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		
		int numMarkets = alliance.getNumAllianceMarkets();
		int marketSizeSum = alliance.getAllianceMarketSizeSum();
		
		map.put("$alliance", alliance.getName());
		map.put("$numMembers", alliance.getMembersCopy().size() + "");
		map.put("$numMarkets", numMarkets + "");
		map.put("$marketSizeSum", marketSizeSum + "");
		
		if (faction1 != null)
		{
			String faction1Str = ExerelinUtilsFaction.getFactionShortName(faction1);
			String theFaction1Str = faction1.getDisplayNameWithArticle();
			map.put("$faction1", faction1Str);
			map.put("$theFaction1", theFaction1Str);
			map.put("$Faction1", Misc.ucFirst(faction1Str));
			map.put("$TheFaction1", Misc.ucFirst(theFaction1Str));
		}
		if (faction2 != null)
		{
			String faction2Str = ExerelinUtilsFaction.getFactionShortName(faction2);
			String theFaction2Str = faction2.getDisplayNameWithArticle();
			map.put("$faction2", faction2Str);
			map.put("$theFaction2", theFaction2Str);
			map.put("$Faction2", Misc.ucFirst(faction2Str));
			map.put("$TheFaction2", Misc.ucFirst(theFaction2Str));
			map.put("$TheFaction2", Misc.ucFirst(theFaction2Str));
		}
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		if (!stage.equals("formed")) addTokensToList(result, "$numMembers");
		addTokensToList(result, "$numMarkets");
		addTokensToList(result, "$marketSizeSum");
		return result.toArray(new String[0]);
	}
	
	public void setDone(boolean done) {
		this.done = done;
	}
	
	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
}