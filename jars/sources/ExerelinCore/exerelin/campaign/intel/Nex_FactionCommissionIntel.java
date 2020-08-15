package exerelin.campaign.intel;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
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
}
