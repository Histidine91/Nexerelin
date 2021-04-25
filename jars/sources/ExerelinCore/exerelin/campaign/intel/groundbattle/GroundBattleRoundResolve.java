package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.utilities.NexUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class GroundBattleRoundResolve {
	
	public static Logger log = Global.getLogger(GroundBattleRoundResolve.class);
		
	protected GroundBattleIntel intel;
	protected Map<ForceType, Integer> atkLosses = new HashMap<>();
	protected Map<ForceType, Integer> defLosses = new HashMap<>();
	protected Map<ForceType, Integer> playerLosses = new HashMap<>();
	
	public GroundBattleRoundResolve(GroundBattleIntel intel) {
		this.intel = intel;
	}
	
	public void resolveRound() {
		// attack, resolve moves, attack again
		// then check who holds each industry
		// update morale
		for (GroundUnit unit : intel.getAllUnits()) {
			unit.lossesLastTurn = 0;
			unit.moraleDeltaLastTurn = 0;
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			resolveCombatOnIndustry(ifb);
		}
		
		for (GroundUnit unit : intel.getAllUnits()) {
			unit.executeMove();
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			resolveCombatOnIndustry(ifb);
		}
		
		processUnitsAfterRound();
		updateIndustries();
	}
	
	public void processUnitsAfterRound() {
		for (GroundUnit unit : intel.getAllUnits()) {
			unit.reorganize(-1);
			
			IndustryForBattle lastLocation = unit.location;
			
			if (unit.isPlayer) {
				GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_LOSSES, intel.turnNum);
				lg.params.put("unit", unit);
				lg.params.put("losses", unit.lossesLastTurn);
				lg.params.put("morale", unit.moraleDeltaLastTurn);
				lg.params.put("location", lastLocation);
				intel.addLogEvent(lg);				
			}
			
			boolean destroyed = false;
			if (unit.getSize() <= 0) {	// ded
				unit.destroyUnit(0);
				destroyed = true;
			}			
			if (unit.lossesLastTurn > 0 && unit.morale < GBConstants.BREAK_AT_MORALE) {
				// TODO: try to rout unit before evaporating it
				unit.destroyUnit(0.5f);
				destroyed = true;
			}
			else if (unit.morale < GBConstants.REORGANIZE_AT_MORALE) {
				unit.reorganize(1);
			}
			
			if (destroyed) {
				GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_DESTROYED, intel.turnNum);
				lg.params.put("unit", unit);
				lg.params.put("location", lastLocation);
				intel.addLogEvent(lg);	
			}
		}
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.location.isContested()) {
				unit.modifyMorale(-GBConstants.MORALE_LOSS_FROM_COMBAT);
			} else {
				unit.modifyMorale(GBConstants.MORALE_RECOVERY_OUT_OF_COMBAT, 0.8f);
			}
		}
	}
	
	public void updateIndustries() {
		for (IndustryForBattle ifb : intel.industries) {
			ifb.updateOwner();
		}
	}
	
	public void resolveCombatOnIndustry(IndustryForBattle ifb) {
		if (!ifb.isContested()) return;
		log.info("Resolving combat on " + ifb.ind.getCurrentName());
		
		float atkStr = getAttackStrengthOnIndustry(ifb, true) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		log.info(String.format("  Attacker strength: %.2f", atkStr));
		float defStr = getAttackStrengthOnIndustry(ifb, false) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);		
		log.info(String.format("  Defender strength: %.2f", defStr));
		
		log.info(String.format("  Applying damage to defender"));
		distributeDamage(ifb, false, atkStr);
		log.info(String.format("  Applying damage to attacker"));
		distributeDamage(ifb, true, defStr);
	}
	
	/**
	 * Distributes the specified damage among all units of the specified side on the specified industry. 
	 * Each unit's share of the damage is proportional to its base strength.
	 * @param ifb
	 * @param attacker Is the damage recipient the attacking side?
	 * @param dam
	 */
	public void distributeDamage(IndustryForBattle ifb, boolean attacker, float dam) {
		List<GroundUnit> units = new ArrayList<>();
		for (GroundUnit unit : ifb.units) {
			if (unit.isAttacker == attacker)
				units.add(unit);
		}
		distributeDamage(units, dam);
	}
	
	/**
	 * Distributes the specified damage among all units of the specified side on the specified industry. 
	 * Each unit's share of the damage is proportional to its base strength.
	 * @param units
	 * @param dam
	 */
	public void distributeDamage(List<GroundUnit> units, float dam) {
		float totalStrength = 0;
		Map<GroundUnit, Float> contribs = new HashMap<>();
		for (GroundUnit unit : units) {
			float contrib = unit.getBaseStrength() * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			totalStrength += contrib;
			contribs.put(unit, contrib);
		}
		for (GroundUnit unit : units) {
			float mult = contribs.get(unit)/totalStrength;
			damageUnit(unit, dam * mult);
		}
	}
	
	public void damageUnit(GroundUnit unit, float dmg) {
		dmg = unit.getAdjustedDamageTaken(dmg);
		
		float dmgPerKill = unit.type.strength;
		int kills = (int)(dmg/dmgPerKill);
		
		// if any remaining damage after kills, roll to kill one more unit
		float surplusDmg = dmg % dmgPerKill;
		if (Math.random() * dmgPerKill < surplusDmg) {
			kills++;
		}
		damageUnitMorale(unit, kills);
		inflictUnitLosses(unit, kills);
	}
	
	public float damageUnitMorale(GroundUnit unit, int kills) {
		if (kills == 0) return 0;
		float lossProportion = (float)kills/unit.getSize();
		float moraleDmg = lossProportion/GBConstants.MORALE_DAMAGE_FACTOR;
		moraleDmg = unit.getAdjustedMoraleDamageTaken(moraleDmg);
		float moraleDmgClamped = unit.modifyMorale(-moraleDmg);
		
		log.info(String.format("    Unit %s (%s) took %.2f morale damage, now has %.2f", 
				unit.name, unit.type.toString(), moraleDmg, unit.morale));
		
		return moraleDmgClamped;
	}
	
	public void inflictUnitLosses(GroundUnit unit, int kills) {
		if (kills == 0) return;
		
		Map<ForceType, Integer> toModify = unit.isAttacker ? atkLosses : defLosses;
		
		if (unit.type == ForceType.HEAVY) {
			unit.heavyArms -= kills;
			unit.men -= kills * GroundUnit.CREW_PER_MECH;
			if (unit.heavyArms < 0) unit.heavyArms = 0;
			if (unit.men < 0) unit.men = 0;
		}
		else {
			unit.men -= kills;
			if (unit.men < 0) unit.men = 0;
		}
		unit.lossesLastTurn += kills;
		
		NexUtils.modifyMapEntry(toModify, unit.type, kills);
		if (unit.isPlayer)
			NexUtils.modifyMapEntry(playerLosses, unit.type, kills);
		
		log.info(String.format("    Unit %s (%s) took %s losses", unit.name, unit.type.toString(), kills));
	}
	
	public float getAttackStrengthOnIndustry(IndustryForBattle ifb, boolean attacker) 
	{
		float str = 0;
		for (GroundUnit unit : ifb.units) {
			if (unit.isAttacker != attacker) continue;
			float contrib = unit.getAttackStrength();
			contrib *= GBConstants.BASE_DAMAGE_MULT;
			log.info(String.format("    Unit %s (%s) contributing attack strength: %.2f", 
					unit.name, unit.type.toString(), contrib));
			str += contrib;
		}
		
		return str;
	}
}
