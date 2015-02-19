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


public class DiplomacyEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(DiplomacyEvent.class);
        private static final int DAYS_TO_KEEP = 60;
	
	private FactionAPI otherFaction;
	private String stage;
	private float before;
        private float after;
        private float delta;
        float age;
	private Map<String, Object> params;
	    
        public boolean done;
        public boolean transmitted;
		
        @Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
                done = false;
                transmitted = false;
                age = 0;
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		otherFaction = (FactionAPI)params.get("otherFaction");
		before = (Float)params.get("before");
                after = (Float)params.get("after");
                delta = (Float)params.get("delta");
		stage = (String)params.get("stage");
		//log.info("Params newOwner: " + newOwner);
		//log.info("Params oldOwner: " + oldOwner);
		//log.info("Params playerInvolved: " + playerInvolved);
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
		if (!transmitted)
                {
			MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
                        Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
                        log.info("Diplomacy event: " + this.stage);
                        transmitted = true;
                }
	}

	@Override
	public String getEventName() {
		return (faction.getEntityNamePrefix() + " - " + otherFaction.getEntityNamePrefix() + " diplomatic event");
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
		return CampaignEventPlugin.CampaignEventCategory.EVENT;
	}
	
        private String getNewRelationStr()
        {
            RepLevel level = faction.getRelationshipLevel(otherFaction.getId());
            int repInt = (int) Math.ceil((Math.abs(faction.getRelationship(otherFaction.getId())) * 100f));
		
            String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
            return standing;
        }
        
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$otherFaction", otherFaction.getEntityNamePrefix());
                map.put("$theOtherFaction", otherFaction.getDisplayNameWithArticle());
		map.put("$deltaAbs", "" + (int)Math.ceil(Math.abs(delta*100f)));
                map.put("$newRelationStr", getNewRelationStr());
		return map;
	}
        
        @Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$deltaAbs");
                addTokensToList(result, "$newRelationStr");
		return result.toArray(new String[0]);
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