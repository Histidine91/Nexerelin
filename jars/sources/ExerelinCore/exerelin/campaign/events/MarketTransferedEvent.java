package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MarketTransferedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(MarketTransferedEvent.class);
	
	protected static final int DAYS_TO_KEEP = 60;
	
	protected FactionAPI newOwner;
	protected FactionAPI oldOwner;
	protected float repEffect = 0;
	protected Map<String, Object> params;
	
	protected boolean done;
	protected float age;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		age = 0;
		//log.info("Capture event created");
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		newOwner = (FactionAPI)params.get("newOwner");
		oldOwner = (FactionAPI)params.get("oldOwner");
		repEffect = (Float)params.get("repEffect");
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
			return;
		
		if (newOwner == oldOwner)
		{
			done = true;
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
	public void startEvent()
	{
		String stage = "report";
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;

		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
				public void beforeDelivery(CommMessageAPI message) {
					InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
					TextPanelAPI panel = null;
					if (dialog != null)
						panel = dialog.getTextPanel();
					
					NexUtilsReputation.adjustPlayerReputation(newOwner, repEffect, message, panel);
					/*
					CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
					impact.delta = repEffect;
					ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
									new RepActionEnvelope(RepActions.CUSTOM, impact,
														  message, panel, true), 
														  newOwner.getId());
					*/					
				}
			});
	}
	
	@Override
	public String getEventName() {
		String name = StringHelper.getString("exerelin_events", "marketTransfered");
		name = StringHelper.substituteToken(name, "$market", market.getName());
		name = StringHelper.substituteToken(name, "$faction", newOwner.getDisplayName());
		return name;
	}
	
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return newOwner.getCrest();
	}
		
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String newOwnerStr = newOwner.getDisplayName();
		String oldOwnerStr = oldOwner.getDisplayName();
		String theNewOwnerStr = newOwner.getDisplayNameWithArticle();
		String theOldOwnerStr = oldOwner.getDisplayNameWithArticle();
		map.put("$newOwner", newOwnerStr);
		map.put("$oldOwner", oldOwnerStr);
		map.put("$NewOwner", Misc.ucFirst(newOwnerStr));
		map.put("$OldOwner", Misc.ucFirst(oldOwnerStr));
		map.put("$theNewOwner", theNewOwnerStr);
		map.put("$theOldOwner", theOldOwnerStr);
		map.put("$TheNewOwner", Misc.ucFirst(theNewOwnerStr));
		map.put("$TheOldOwner", Misc.ucFirst(theOldOwnerStr));
		
		map.put("$oldOwnerMarketsNum", "" + ExerelinUtilsFaction.getFactionMarkets(oldOwner.getId()).size());
		map.put("$newOwnerMarketsNum", "" + ExerelinUtilsFaction.getFactionMarkets(newOwner.getId()).size());
		
		//map.put("$repEffectAbs", "" + (int)Math.ceil(Math.abs(repEffect*100f)));
		//map.put("$newRelationStr", getNewRelationStr(newOwner));
		
		return map;
	}
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$newOwnerMarketsNum");
		addTokensToList(result, "$oldOwnerMarketsNum");
		
		//addTokensToList(result, "$repEffectAbs");
		//addTokensToList(result, "$newRelationStr");
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		//Color colorRepEffect = repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		//Color colorNew = faction.getRelColor(Factions.PLAYER);
		return new Color[] {
			Misc.getHighlightColor(), Misc.getHighlightColor(), 
			//colorRepEffect, colorNew
		};
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
	
	protected static String getNewRelationStr(FactionAPI faction)
	{
		RepLevel level = faction.getRelationshipLevel(Factions.PLAYER);
		int repInt = (int) Math.ceil((faction.getRelationship(Factions.PLAYER)) * 100f);
		
		String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
		return standing;
	}
}
