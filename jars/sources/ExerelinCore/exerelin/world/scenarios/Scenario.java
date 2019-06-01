package exerelin.world.scenarios;

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
	
	public void init() {
		
	}
}
