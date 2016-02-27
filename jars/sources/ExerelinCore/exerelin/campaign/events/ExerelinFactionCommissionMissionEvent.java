package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
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
	
	@Override
	public boolean isDone() {
		return ended;
	}
}
