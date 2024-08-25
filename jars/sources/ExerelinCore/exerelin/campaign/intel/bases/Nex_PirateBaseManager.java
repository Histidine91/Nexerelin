package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import exerelin.campaign.SectorManager;

import java.util.List;

public class Nex_PirateBaseManager extends PirateBaseManager {

	public List<EveryFrameScript> getActive() {
		return active;
	}
		
	@Override
	protected StarSystemAPI pickSystemForPirateBase() {
		if (Global.getSettings().getBoolean("nex_pirateBasesRequireLivePirateFaction") && !SectorManager.isFactionAlive(Factions.PIRATES))
			return null;
		
		return super.pickSystemForPirateBase();
	}

	// same as vanilla except create our own pirate base intel
	@Override
	protected EveryFrameScript createEvent() {
		if (numSpawnChecksToSkip > 0) {
			numSpawnChecksToSkip--;
			return null;
		}

		if (random.nextFloat() < CHECK_PROB) return null;

		StarSystemAPI system = pickSystemForPirateBase();
		if (system == null) return null;

		//PirateBaseIntel intel = new PirateBaseIntel(system, Factions.PIRATES, PirateBaseTier.TIER_5_3MODULE);
		//PirateBaseIntel intel = new PirateBaseIntel(system, Factions.PIRATES, PirateBaseTier.TIER_3_2MODULE);
		PirateBaseIntel.PirateBaseTier tier = pickTier();

		//tier = PirateBaseTier.TIER_5_3MODULE;

		String factionId = pickPirateFaction();
		if (factionId == null) return null;

		PirateBaseIntel intel = new NexPirateBaseIntel(system, factionId, tier);
		if (intel.isDone()) intel = null;

		return intel;
	}

	/**
	 * Replaces the vanilla pirate base manager with the Nex one, with proper handling for Pather cells.
	 * Call when adding Nex to an existing save.
	 */
	public static void replaceManager() {
		for (EveryFrameScript script : Global.getSector().getScripts()) {
			if (script instanceof PirateBaseManager && !(script instanceof Nex_PirateBaseManager)) {

				Global.getSector().removeScript(script);

				Nex_PirateBaseManager manager = new Nex_PirateBaseManager();
				// migrate the existing bases to new script
				for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(PirateBaseIntel.class)) {
					PirateBaseIntel base = (PirateBaseIntel)intel;
					manager.getActive().add(base);
				}

				Global.getSector().addScript(manager);
				break;
			}
		}
	}
}
