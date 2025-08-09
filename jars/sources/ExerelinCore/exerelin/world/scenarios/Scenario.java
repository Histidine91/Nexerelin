package exerelin.world.scenarios;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;

public abstract class Scenario {
	
	public void onCharacterCreation(CharacterCreationData data) {

	}
	
	public void afterProcGen(SectorAPI sector) {

	}
	
	public void afterEconomyLoad(SectorAPI sector) {
		
	}
	
	public void afterTimePass(SectorAPI sector) {
		
	}

	/**
	 * Called from {@code Nex_NGCFinalize}.
	 */
	public void init() {
		
	}


	/**
	 * When scenario is selected in manager. A one-off instance is created, so don't store data in the scenario class that needs to reach the sector.
	 * @param dialog
	 */
	public void onSelect(InteractionDialogAPI dialog) {

	}
}
