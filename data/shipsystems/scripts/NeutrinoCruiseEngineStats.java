package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class NeutrinoCruiseEngineStats implements ShipSystemStatsScript {

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		if (state == ShipSystemStatsScript.State.OUT) {
			stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
			stats.getMaxTurnRate().unmodify(id);
		} else {
			stats.getMaxSpeed().modifyFlat(id, 140f);
			stats.getTurnAcceleration().modifyFlat(id, -4f * effectLevel);
			stats.getTurnAcceleration().modifyPercent(id, 50f * effectLevel);
			stats.getMaxTurnRate().modifyFlat(id, -6f);
			stats.getAcceleration().modifyPercent(id, 66f * effectLevel);
			stats.getDeceleration().modifyPercent(id, 66f * effectLevel);
		}
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("cruise control engaged", false);
		} else if (index == 1) {
			return new StatusData("remember to fasten all loose items", false);
		}
		return null;
	}
}
