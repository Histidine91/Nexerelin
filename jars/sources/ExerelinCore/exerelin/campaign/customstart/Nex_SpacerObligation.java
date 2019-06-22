package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.tutorial.SpacerObligation;

public class Nex_SpacerObligation extends SpacerObligation {
	
	@Override
	protected int getDebt() {
		return (int)(super.getDebt() * Global.getSettings().getFloat("nex_spacerDebtMult"));
	}
	
	// runcode exerelin.campaign.customstart.Nex_SpacerObligation.replaceObligation()
	public static void replaceObligation() {
		Global.getSector().getListenerManager().removeListenerOfClass(SpacerObligation.class);
		new Nex_SpacerObligation();
	}
}
