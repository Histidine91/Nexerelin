package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import static com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel.log;
import com.fs.starfarer.api.util.Misc;

public class Nex_FactionCommissionIntel extends FactionCommissionIntel {
	
	public Nex_FactionCommissionIntel(FactionAPI faction) {
		super(faction);
	}
	
	@Override
	public void makeRepChanges(InteractionDialogAPI dialog) {
		// do nothing, we take care of it elsewhere
	}
	
	@Override
	public String getName() {
		return Misc.ucFirst(super.getName());
	}
	
	// Do not remove the script when mission ends, that keeps it from expiring
	@Override
	public void endMission(InteractionDialogAPI dialog) {
		log.info(String.format("Ending commission with [%s]", faction.getDisplayName()));
		Global.getSector().getListenerManager().removeListener(this);
		//Global.getSector().removeScript(this);
		
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_FACTION);
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().unset(MemFlags.FCM_EVENT);
		
		undoAllRepChanges(dialog);
		
		endAfterDelay();
	}
}
