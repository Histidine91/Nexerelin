package exerelin.campaign.events;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager.DiplomacyEventDef;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.utilities.StringHelper;


public class DiplomacyEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(DiplomacyEvent.class);
	protected static final int DAYS_TO_KEEP = 30;
	
	protected FactionAPI otherFaction;
	protected DiplomacyEventDef event;	// FIXME: legacy, remove
	protected String eventStage;
	protected ExerelinReputationAdjustmentResult result;
	protected float delta;
	protected float age = 0;
	protected Map<String, Object> params;
		
	protected boolean done = false;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		otherFaction = (FactionAPI)params.get("otherFaction");
		delta = (Float)params.get("delta");
		eventStage = (String)params.get("eventStage");
		result = (ExerelinReputationAdjustmentResult)params.get("result");
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
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;	//MessagePriority.ENSURE_DELIVERY;
		
		/*
		Global.getSector().reportEventStage(this, event.stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
			final DiplomacyEventDef thisEvent = event;
			final float thisDelta = delta;
			final MarketAPI thisMarket = market;
			final FactionAPI fac = market.getFaction();
			final FactionAPI otherFac = otherFaction;

			public void beforeDelivery(CommMessageAPI message) {
				//DiplomacyManager.adjustRelations(thisEvent, fac, otherFac, thisDelta);
			}
		});
		*/
		Global.getSector().reportEventStage(this, eventStage, market.getPrimaryEntity(), priority);
		log.info("Diplomacy event: " + eventStage);
	}

	@Override
	public String getEventName() {
		String name = StringHelper.getString("exerelin_events", "diplomacy");
		name = StringHelper.substituteToken(name, "$faction", faction.getDisplayName());
		name = StringHelper.substituteToken(name, "$otherFaction", otherFaction.getDisplayName());
		return Misc.ucFirst(name);
	}
	
	/*
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}
	*/

	@Override
	public String getCurrentMessageIcon() {
		if (result.isHostile && !result.wasHostile) return "graphics/exerelin/icons/intel/war.png";
		else if (!result.isHostile && result.wasHostile) return "graphics/exerelin/icons/intel/peace.png";
		return null;
	}
	
	@Override
	public String getEventIcon() {
		if (result.isHostile && !result.wasHostile) return "graphics/exerelin/icons/intel/war.png";
		else if (!result.isHostile && result.wasHostile) return "graphics/exerelin/icons/intel/peace.png";
		return null;
	}
	
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		if (result.isHostile != result.wasHostile)
			return CampaignEventPlugin.CampaignEventCategory.EVENT;
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	protected String getNewRelationStr(float delta)
	{
		RepLevel level = faction.getRelationshipLevel(otherFaction.getId());
		int repInt = (int) Math.ceil((faction.getRelationship(otherFaction.getId()) + delta) * 100f);
		
		String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
		return standing;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		addFactionNameTokens(map, "other", otherFaction);
		map.put("$deltaAbs", "" + (int)Math.ceil(Math.abs(delta*100f)));
		//map.put("$newRelationStr", getNewRelationStr(delta));
		map.put("$newRelationStr", getNewRelationStr(0));
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> highlights = new ArrayList<>();
		addTokensToList(highlights, "$deltaAbs");
		addTokensToList(highlights, "$newRelationStr");
		return highlights.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorDelta = delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = faction.getRelColor(otherFaction.getId());
		return new Color[] {colorDelta, colorNew};
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