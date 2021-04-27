package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import exerelin.campaign.SectorManager;

public class Nex_PirateBaseManager extends PirateBaseManager {
		
	@Override
	protected StarSystemAPI pickSystemForPirateBase() {
		if (!SectorManager.isFactionAlive(Factions.PIRATES))
			return null;
		
		return super.pickSystemForPirateBase();
	}
}
