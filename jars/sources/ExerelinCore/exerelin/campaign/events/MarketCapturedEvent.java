package exerelin.campaign.events;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MarketCapturedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(MarketCapturedEvent.class);
	
	protected static final int DAYS_TO_KEEP = 60;
	
	protected FactionAPI newOwner;
	protected FactionAPI oldOwner;
	protected List<String> factionsToNotify;
	protected float repChangeStrength;
	protected boolean playerInvolved;
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
		repChangeStrength = (Float)params.get("repChangeStrength");
		playerInvolved = (Boolean)params.get("playerInvolved");
		factionsToNotify = (List<String>)params.get("factionsToNotify");
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
		//MessagePriority priority = MessagePriority.SECTOR;
		MessagePriority priority = MessagePriority.ENSURE_DELIVERY;
		if (playerInvolved) 
		{
			stage = "report_player";
			//priority = MessagePriority.ENSURE_DELIVERY;
		}

		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
			public void beforeDelivery(CommMessageAPI message) {
				if (!playerInvolved) return;
				for (String factionId : factionsToNotify)
				{
					float amount = repChangeStrength;
					if (factionId.equals("templars")) amount *= 0.5f;
					ExerelinUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), null, amount);
				}
			}});
	}
	
	@Override
	public String getEventName() {
		String name = StringHelper.getString("exerelin_events", "marketCaptured");
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
		
		map.put("$marketSize", market.getSize() + "");
		return map;
	}
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		//addTokensToList(result, "$marketSize");
		addTokensToList(result, "$newOwnerMarketsNum");
		addTokensToList(result, "$oldOwnerMarketsNum");
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
