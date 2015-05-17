package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class AllianceChangedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(AllianceChangedEvent.class);
	protected static final int DAYS_TO_KEEP = 90;
	
	protected FactionAPI faction1;
	protected FactionAPI faction2;
	protected Alliance alliance;
	protected float age = 0;
	protected String stage = "formed";
	protected Map<String, Object> params;
	protected boolean done = false;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
		
	@Override
	public String getEventName() {
		return ("Alliance changed: " + alliance.name);
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		faction1 = (FactionAPI)params.get("faction1");
		faction2 = (FactionAPI)params.get("faction2");
		alliance = (Alliance)params.get("alliance");
		stage = (String)params.get("stage");
	}
	
	@Override
	public void startEvent()
	{
		MessagePriority priority = MessagePriority.SECTOR;
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String faction1Str = faction1.getEntityNamePrefix();
		String theFaction1Str = faction1.getDisplayNameWithArticle();
		
		int numMarkets = alliance.getNumAllianceMarkets();
		int marketSizeSum = alliance.getAllianceMarketSizeSum();
		
		map.put("$alliance", alliance.name);
		map.put("$numMembers", alliance.members.size() + "");
		map.put("$numMarkets", numMarkets + "");
		map.put("$marketSizeSum", marketSizeSum + "");
		
		if (faction1 != null)
		{
			map.put("$faction1", faction1Str);
			map.put("$theFaction1", theFaction1Str);
			map.put("$Faction1", Misc.ucFirst(faction1Str));
			map.put("$TheFaction1", Misc.ucFirst(theFaction1Str));
		}
		if (faction2 != null)
		{
			String faction2Str = faction2.getEntityNamePrefix();
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
	
	@Override
	public void advance(float amount)
	{
		if (done)
		{
			return;
		}
		age = age + Global.getSector().getClock().convertToDays(amount);
		if (age > DAYS_TO_KEEP)
		{
			done = true;
			return;
		}
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