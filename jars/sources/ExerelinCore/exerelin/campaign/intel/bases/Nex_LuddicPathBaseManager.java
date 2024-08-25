package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import exerelin.campaign.SectorManager;

import java.util.ArrayList;
import java.util.List;

public class Nex_LuddicPathBaseManager extends LuddicPathBaseManager {

	public List<EveryFrameScript> getActive() {
		return active;
	}
	
	@Override
	protected StarSystemAPI pickSystemForLPBase() {
		if (Global.getSettings().getBoolean("nex_pirateBasesRequireLivePirateFaction") && !SectorManager.isFactionAlive(Factions.LUDDIC_PATH))
			return null;
		
		return super.pickSystemForLPBase();
	}

	public List<LuddicPathCellsIntel> getCells() {
		return new ArrayList<>(cells.values());
	}

	/**
	 * Replaces the vanilla Luddic Path base manager with the Nex one, with proper handling for Pather cells.
	 * Call when adding Nex to an existing save.
	 */
	public static void replaceManager() {
		for (EveryFrameScript script : Global.getSector().getScripts()) {
			if (script instanceof LuddicPathBaseManager && !(script instanceof Nex_LuddicPathBaseManager)) {

				Global.getSector().removeScript(script);
				Nex_LuddicPathBaseManager manager = new Nex_LuddicPathBaseManager();
				Global.getSector().addScript(manager);

				// migrate existing cells to new script
				for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(LuddicPathCellsIntel.class)) {
					LuddicPathCellsIntel cell = (LuddicPathCellsIntel) intel;
					manager.cells.put(cell.getMarket(), cell);
				}

				// migrate the existing bases to new script
				for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(LuddicPathBaseIntel.class)) {
					LuddicPathBaseIntel base = (LuddicPathBaseIntel)intel;
					manager.getActive().add(base);
				}
				break;
			}
		}
	}
}
