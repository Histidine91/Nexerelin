package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetInflater;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin.AutofitPluginDelegate;
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin;
import java.util.Random;

public class VolturnGenPlugin extends MercFleetGenPlugin {
	
	@Override
	public CampaignFleetAPI generateFleet(MarketAPI market) {
		CampaignFleetAPI fleet = super.generateFleet(market);
		if (!intel.getDef().noAutofit) {
			FleetMemberAPI flag = fleet.getFleetData().getMembersListCopy().get(0);
			FleetInflater inf = fleet.getInflater();
			if (inf instanceof AutofitPluginDelegate) {
				AutofitPluginDelegate del = (AutofitPluginDelegate)inf;
				CoreAutofitPlugin auto = new CoreAutofitPlugin(fleet.getCommander());
				auto.setRandom(new Random());
				auto.setChecked(CoreAutofitPlugin.UPGRADE, true);
				// if you don't set a clone as the target, the ship doesn't get any weapons
				auto.doFit(flag.getVariant(), flag.getVariant().clone(), 2, del);
						
				fleet.getFleetData().setSyncNeeded();
				fleet.getFleetData().syncIfNeeded();
			}
		}
		
		return fleet;
	}
}
