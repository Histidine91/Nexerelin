package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class IncendiaryAmmoStats implements ShipSystemStatsScript {

	public static final float DAMAGE_BONUS_PERCENT = 50f;
	public static final float EXTRA_DAMAGE_TAKEN_PERCENT = 50f;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		float bonusPercent = DAMAGE_BONUS_PERCENT * effectLevel;
		stats.getBallisticWeaponDamageMult().modifyPercent(id, bonusPercent);
		//stats.getEnergyWeaponRangeBonus().modifyPercent(id, bonusPercent);
		
		float damageTakenPercent = EXTRA_DAMAGE_TAKEN_PERCENT * effectLevel;
		stats.getArmorDamageTakenMult().modifyPercent(id, damageTakenPercent);
		stats.getHullDamageTakenMult().modifyPercent(id, damageTakenPercent);
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getEnergyWeaponDamageMult().unmodify(id);
		stats.getEnergyWeaponRangeBonus().unmodify(id);
		stats.getArmorDamageTakenMult().unmodify(id);
		stats.getHullDamageTakenMult().unmodify(id);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float bonusPercent = DAMAGE_BONUS_PERCENT * effectLevel;
		float damageTakenPercent = EXTRA_DAMAGE_TAKEN_PERCENT * effectLevel;
		if (index == 0) {
			return new StatusData("ballistic weapon damage +" + (int) bonusPercent + "%", false);
		} else if (index == 1) {
			//return new StatusData("increased energy weapon range", false);
			return null;
		} else if (index == 2) {
			return new StatusData("damage taken +" + (int) damageTakenPercent + "%", true);
		}
		return null;
	}
}
