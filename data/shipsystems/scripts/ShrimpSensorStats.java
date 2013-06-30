package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class ShrimpSensorStats implements ShipSystemStatsScript {

	public static final float ROF_BONUS = 1f;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		float mult = 1f + ROF_BONUS * effectLevel;
		stats.getAutofireAimAccuracy().modifyMult(id, mult);
		stats.getDamageToTargetEnginesMult().modifyMult(id, mult);
		stats.getDamageToTargetWeaponsMult().modifyMult(id, mult);
        stats.getHitStrengthBonus().modifyMult(id, mult);
        stats.getDamageToTargetShieldsMult().modifyMult(id, mult);		
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getAutofireAimAccuracy().unmodify(id);
		stats.getDamageToTargetEnginesMult().unmodify(id);
		stats.getDamageToTargetWeaponsMult().unmodify(id);
        stats.getHitStrengthBonus().unmodify(id);
        stats.getDamageToTargetShieldsMult().unmodify(id);			
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float mult = 1f + ROF_BONUS * effectLevel;
		float bonusPercent = (int) (mult - 1f) * 100f;
		if (index == 0) {
			return new StatusData("aim effectiveness, shield damage, system damage, armor damage +" + (int) bonusPercent + "%", false);			
		}
		return null;
	}
}
