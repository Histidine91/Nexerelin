package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import exerelin.campaign.PlayerFactionStore;


public class FactionSalaryEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionSalaryEvent.class);
        
        private static final float BASE_SALARY = 2000f;
        private static final float INCREMENT_PER_LEVEL = 500f;
        private int month;
        private float paidAmount = 0f;
		
        @Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
                Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
                month = Global.getSector().getClock().getMonth();
	}
	
	@Override
	public void advance(float amount) {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) {
                return;
            }
            CampaignClockAPI clock = Global.getSector().getClock();
            if (clock.getDay() == 1 && clock.getMonth() != month) {
                month = Global.getSector().getClock().getMonth();
                int level = Global.getSector().getPlayerPerson().getStats().getLevel();
                paidAmount = BASE_SALARY + INCREMENT_PER_LEVEL * (level - 1);
                playerFleet.getCargo().getCredits().subtract(paidAmount);
                Global.getSector().reportEventStage(this, "report", playerFleet, MessagePriority.DELIVER_IMMEDIATELY);
                Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
            }
	}

	@Override
	public String getEventName() {
            return ("Faction salary report");
	}
        
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	       
	@Override
	public Map<String, String> getTokenReplacements() {
            Map<String, String> map = super.getTokenReplacements();
            CampaignClockAPI previous = (CampaignClockAPI) Global.getSector().getPersistentData().get("salariesClock");
            if (previous != null) {
                map.put("$date", previous.getMonthString() + ", c." + previous.getCycle());
            }
            String factionName = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId()).getDisplayNameWithArticle();
            map.put("$employer", factionName);
            map.put("$paid", "" + (int) paidAmount + Strings.C);
            return map;
	}
        
        @Override
	public String[] getHighlights(String stageId) {
            String[] ret = {"$paid"};
            return ret;
	}
        
        @Override
        public String getCurrentImage() {
            FactionAPI myFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
            return myFaction.getLogo();
        }
      
        @Override
	public boolean isDone() {
            return false;
	}
      
        @Override
        public boolean showAllMessagesIfOngoing() {
            return false;
        }
}