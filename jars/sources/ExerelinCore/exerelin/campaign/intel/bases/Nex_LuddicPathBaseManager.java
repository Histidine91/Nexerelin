package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import exerelin.campaign.SectorManager;

import java.util.ArrayList;
import java.util.List;

public class Nex_LuddicPathBaseManager extends LuddicPathBaseManager {
	
	@Override
	protected StarSystemAPI pickSystemForLPBase() {
		if (!SectorManager.isFactionAlive(Factions.LUDDIC_PATH))
			return null;
		
		return super.pickSystemForLPBase();
	}

	public List<LuddicPathCellsIntel> getCells() {
		return new ArrayList<>(cells.values());
	}

}
