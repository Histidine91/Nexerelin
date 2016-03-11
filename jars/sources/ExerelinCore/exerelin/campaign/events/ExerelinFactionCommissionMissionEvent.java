package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.FactionCommissionMissionEvent;

// same as vanilla's except public endEvent()
public class ExerelinFactionCommissionMissionEvent extends FactionCommissionMissionEvent {	
	
	protected boolean ended = false;
	
	// same as vanilla one except public
	public void endEvent() {
		ended = true;
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_FACTION);
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_EVENT);
	}
	
	// same as vanilla
	@Override
	public void advance(float amount) {
		if (!isEventStarted()) return;
		if (isDone()) return;
		
		//System.out.println("Registered: " + Global.getSector().getAllListeners().contains(this));
		float days = Global.getSector().getClock().convertToDays(amount);
		RepLevel level = faction.getRelToPlayer().getLevel();
		if (!level.isAtWorst(RepLevel.NEUTRAL)) {
			endEvent();
			Global.getSector().reportEventStage(this, "annul", findMessageSender(),
					MessagePriority.ENSURE_DELIVERY,
					new BaseOnMessageDeliveryScript() {
				public void beforeDelivery(CommMessageAPI message) {
					
				}
			});
		}
	}
	
	@Override
	public boolean isDone() {
		return ended;
	}
}
