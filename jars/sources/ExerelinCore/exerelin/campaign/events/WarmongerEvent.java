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
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;


public class WarmongerEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(WarmongerEvent.class);
	
    protected Map<String, Float> repLoss = new HashMap<>();
	protected float avgRepLoss = 0;
	protected int numFactions = 0;
	protected float myFactionLoss = 0;
	protected String targetFaction = "independent";
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	@Override
	public void setParam(Object param) {
		Map<String, Object> params = (Map<String, Object>)param;
        repLoss = (Map<String, Float>)params.get("repLoss");
		avgRepLoss = (float)params.get("avgRepLoss");
		myFactionLoss = (float)params.get("myFactionLoss");
		numFactions = (int)params.get("numFactions");
		targetFaction = (String)params.get("targetFaction");
	}
		
	@Override
	public void advance(float amount)
	{
	}
	
	@Override
	public void startEvent() {
		
	}
	
	public static WarmongerEvent getOngoingEvent()
	{
		CampaignEventPlugin event = Global.getSector().getEventManager().getOngoingEvent(null, "exerelin_warmonger");
		if (event != null)
			return (WarmongerEvent)event;
		return null;
	}
	
	public void reportEvent(SectorEntityToken target, Object param) {
		setParam(param);
		faction = target.getFaction();
		
		// we can set the reputation change only on message delivery
		// but problem is, the token replacement method needs to know the relationship change NOW
		//DiplomacyManager.adjustRelations(event, market, market.getFaction(), otherFaction, delta);
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
		String stage = "report";
		if (myFactionLoss <= 0) stage = "report_noOwnFaction";
		if (target.getContainingLocation() == null)
			target = Global.getSector().getPlayerFleet();
		Global.getSector().reportEventStage(this, stage, target, priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
				for (Map.Entry<String, Float> tmp : repLoss.entrySet())
				{
					String factionId = tmp.getKey();
					float loss = tmp.getValue();
					NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), -loss);
				}
				NexUtilsReputation.syncFactionRelationshipsToPlayer();
			}
		});
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "warmonger");
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
		FactionAPI faction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		//RepLevel level = faction.getRelationshipLevel(Factions.PLAYER);
		RepLevel level = RepLevel.getLevelFor(faction.getRelationship(Factions.PLAYER) - myFactionLoss);
		int repInt = (int) Math.ceil((faction.getRelationship(Factions.PLAYER) - myFactionLoss) * 100f);
		
		String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
		return standing;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		FactionAPI playerAlignedFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		
		Map<String, String> map = super.getTokenReplacements();
		
		// fix faction
		String factionName = faction.getEntityNamePrefix();
		if (factionName == null || factionName.isEmpty()) {
			factionName = faction.getDisplayName();
		}

		map.put("$factionIsOrAre", faction.getDisplayNameIsOrAre());

		map.put("$faction", factionName);
		map.put("$Faction", Misc.ucFirst(factionName));
		map.put("$theFaction", faction.getDisplayNameWithArticle());
		map.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));

		map.put("$factionLong", faction.getDisplayNameLong());
		map.put("$FactionLong", Misc.ucFirst(faction.getDisplayNameLong()));
		map.put("$theFactionLong", faction.getDisplayNameLongWithArticle());
		map.put("$TheFactionLong", Misc.ucFirst(faction.getDisplayNameLongWithArticle()));
		
		if (myFactionLoss > 0)
		{
			map.put("$playerFaction", playerAlignedFaction.getDisplayName());
			map.put("$thePlayerFaction", playerAlignedFaction.getDisplayNameWithArticle());
			map.put("$repPenaltyMyFactionAbs", "" + (int)Math.ceil(myFactionLoss*100f));
			map.put("$newRelationStr", getNewRelationStr());
		}
		map.put("$numFactions", "" + numFactions);
		map.put("$repPenaltyAvgAbs", "" + (int)Math.ceil(avgRepLoss*100f));
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		if (myFactionLoss > 0)
		{
			addTokensToList(result, "$repPenaltyMyFactionAbs");
			addTokensToList(result, "$newRelationStr");
			addTokensToList(result, "$numFactions");
		}
		addTokensToList(result, "$repPenaltyAvgAbs");
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		FactionAPI faction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		Color colorRepEffect = Global.getSettings().getColor("textEnemyColor");
		
		// hax to get the right color
		faction.adjustRelationship(Factions.PLAYER, -myFactionLoss);
		Color colorNew = faction.getRelColor(Factions.PLAYER);
		faction.adjustRelationship(Factions.PLAYER, +myFactionLoss);
		
		return new Color[] { colorRepEffect, colorNew, Misc.getHighlightColor(), colorRepEffect };
	}
	
	@Override
	public boolean isDone() {
		return false;
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