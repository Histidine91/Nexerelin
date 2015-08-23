package exerelin.campaign.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsReputation;
import java.util.Iterator;


public class WarmongerEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(WarmongerEvent.class);
	protected static final int DAYS_TO_KEEP = 60;
	
    protected Map<String, Float> repLoss = new HashMap<>();
	protected float avgRepLoss = 0;
	protected float numFactions = 0;
	protected float myFactionLoss = 0;
	protected Map<String, Object> params;
	protected String targetFaction = "independent";
        
	protected float age;	
	protected boolean done;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		age = 0;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
        repLoss = (HashMap<String, Float>)params.get("repLoss");
		avgRepLoss = (float)params.get("avgRepLoss");
		myFactionLoss = (float)params.get("myFactionLoss");
		numFactions = (int)params.get("numFactions");
		targetFaction = (String)params.get("targetFaction");
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
	public void startEvent() {
		// we can set the reputation change only on message delivery
		// but problem is, the token replacement method needs to know the relationship change NOW
		//DiplomacyManager.adjustRelations(event, market, market.getFaction(), otherFaction, delta);
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
		Global.getSector().reportEventStage(this, "report", Global.getSector().getPlayerFleet(), priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
								Iterator<Map.Entry<String, Float>> iter = repLoss.entrySet().iterator();
								while (iter.hasNext())
                                {
									Map.Entry<String, Float> tmp = iter.next();
									String factionId = tmp.getKey();
									float loss = tmp.getValue();
									ExerelinUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), -loss);
                                }
								ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
			}
		});
	}

	@Override
	public String getEventName() {
		return ("Warmonger reputation penalty");
	}
	
	/*
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return newOwner.getLogo();
	}
	*/
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	protected String getNewRelationStr()
	{
		FactionAPI target = Global.getSector().getFaction(targetFaction);
		RepLevel level = target.getRelationshipLevel(Factions.PLAYER);
		int repInt = (int) Math.ceil((target.getRelationship(Factions.PLAYER)) * 100f);
		
		String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
		return standing;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		FactionAPI playerAlignedFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		
		Map<String, String> map = super.getTokenReplacements();
		map.put("$targetFaction", targetFaction);
		map.put("$repPenaltyAvg", "" + (int)Math.ceil(Math.abs(avgRepLoss*100f)));
		map.put("$numFactions", "" + numFactions);
		map.put("$playerFaction", playerAlignedFaction.getDisplayName());
		map.put("$thePlayerFaction", playerAlignedFaction.getDisplayNameWithArticle());
		map.put("$newRelationStr", getNewRelationStr());
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$repPenaltyAvg");
		addTokensToList(result, "$numFactions");
		return result.toArray(new String[0]);
	}
	
	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}