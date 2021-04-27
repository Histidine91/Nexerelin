package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import exerelin.campaign.SectorManager;

public class Nex_LuddicPathBaseManager extends LuddicPathBaseManager {
	
	@Override
	protected StarSystemAPI pickSystemForLPBase() {
		if (!SectorManager.isFactionAlive(Factions.LUDDIC_PATH))
			return null;
		
		return super.pickSystemForLPBase();
	}
}
