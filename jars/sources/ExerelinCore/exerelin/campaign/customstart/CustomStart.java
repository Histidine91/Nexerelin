package exerelin.campaign.customstart;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.List;
import java.util.Map;

// Custom starting fleet; e.g. spacer start
public abstract class CustomStart {
	public abstract void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap);

	public boolean shouldShow() {
		return true;
	}

	/**
	 * Will disable the custom start if this returns a non-null value.
	 * @return
	 */
	public String getDisabledTooltip() {
		return null;
	}
	
	protected String getShip(FactionAPI faction, String role) {
		List<ShipRolePick> roles = faction.pickShip(role, ShipPickParams.all());
		if (roles.isEmpty()) return null;
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (ShipRolePick pick : roles) {
			picker.add(pick.variantId, pick.weight);
		}
		
		return picker.pick();
	}
}
