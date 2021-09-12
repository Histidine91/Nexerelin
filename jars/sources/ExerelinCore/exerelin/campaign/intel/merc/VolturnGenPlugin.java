package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetInflater;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin.AutofitPluginDelegate;
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin;
import java.util.Random;

public class VolturnGenPlugin extends MercFleetGenPlugin {
	
	@Override
	public void inflateFleet(CampaignFleetAPI fleet) {
		super.inflateFleet(fleet);
		
		FleetMemberAPI flag = fleet.getFleetData().getMembersListCopy().get(0);
		FleetInflater inf = fleet.getInflater();
		if (inf instanceof AutofitPluginDelegate) {
			AutofitPluginDelegate del = (AutofitPluginDelegate)inf;
			CoreAutofitPlugin auto = new CoreAutofitPlugin(fleet.getCommander());
			int existingMods = flag.getVariant().getSMods().size();
			if (existingMods >= 2) return;
			
			auto.setRandom(new Random(intel.seed));
			auto.setChecked(CoreAutofitPlugin.UPGRADE, true);
			// if you don't set a clone as the target, the ship doesn't get any weapons
			auto.doFit(flag.getVariant(), flag.getVariant().clone(), 2 - existingMods, del);

			fleet.getFleetData().setSyncNeeded();
			fleet.getFleetData().syncIfNeeded();
		}
	}
}
