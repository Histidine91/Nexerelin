package exerelin.campaign.abilities.ai;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.TransponderAbilityAI;

public class AlwaysOnTransponderAI extends TransponderAbilityAI {
	
	public static final String MEMORY_KEY_ALWAYS_ON = "$nex_transponderAlwaysOn";
	
	@Override
	public void advance(float days) {
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		if (mem.getBoolean(MEMORY_KEY_ALWAYS_ON)) {
			if (!ability.isActive()) {
				ability.activate();
			}			
			return;
		}
		
		super.advance(days);
	}
}
