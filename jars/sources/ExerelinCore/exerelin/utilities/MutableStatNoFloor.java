package exerelin.utilities;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import java.util.HashMap;

/**
 * Almost identical to MutableStat except removes the floor in recompute()
 */
@Deprecated
public class MutableStatNoFloor extends MutableStat 
{	
//	private HashMap<String, StatMod> flatMods = new HashMap<String, StatMod>();
//	private HashMap<String, StatMod> percentMods = new HashMap<String, StatMod>();
//	private HashMap<String, StatMod> multMods = new HashMap<String, StatMod>();
	private HashMap<String, StatMod> flatMods;
	private HashMap<String, StatMod> percentMods;
	private HashMap<String, StatMod> multMods;
	
	private boolean needsRecompute = false;
	public MutableStatNoFloor(float base) {
		super(base);
	}
	
	protected Object readResolve() {
		return super.readResolve();
	}
	
	public void applyMods(MutableStat other) {
		getFlatMods().putAll(other.getFlatMods());
		getPercentMods().putAll(other.getPercentMods());
		getMultMods().putAll(other.getMultMods());
		//flatAfterMult.putAll(other.getFlatAfterMultMods());
		needsRecompute = true;
	}
	
	public void applyMods(StatBonus other) {
		getFlatMods().putAll(other.getFlatBonuses());
		getPercentMods().putAll(other.getPercentBonuses());
		getMultMods().putAll(other.getMultBonuses());
		needsRecompute = true;
	}
	
	public boolean isUnmodified() {
		return (flatMods == null || getFlatMods().isEmpty()) &&
			   (multMods == null || getMultMods().isEmpty()) &&
			   (percentMods == null || getPercentMods().isEmpty());
	}
	
//	public HashMap<String, StatMod> getFlatAfterMultMods() {
//		return flatAfterMult;
//	}

	public HashMap<String, StatMod> getFlatMods() {
		if (flatMods == null) {
			flatMods = new HashMap<String, StatMod>();
		} 
		return flatMods;
	}

	public HashMap<String, StatMod> getPercentMods() {
		if (percentMods == null) {
			percentMods = new HashMap<String, StatMod>();
		}
		return percentMods;
	}

	public HashMap<String, StatMod> getMultMods() {
		if (multMods == null) {
			multMods = new HashMap<String, StatMod>();
		}
		return multMods;
	}

	public StatMod getFlatStatMod(String source) {
		return getFlatMods().get(source);
	}
	
//	public StatMod getFlatAfterMultStatMod(String source) {
//		return flatAfterMult.get(source);
//	}
	
	public StatMod getPercentStatMod(String source) {
		return getPercentMods().get(source);
	}
	
	public StatMod getMultStatMod(String source) {
		return getMultMods().get(source);
	}
	
	public void modifyFlatAfterMult(String source, float value) {
		modifyFlat(source, value, null);
	}
	
//	public void modifyFlatAfterMult(String source, float value, String desc) {
//		StatMod mod = flatAfterMult.get(source);
//		if (mod == null && value == 0) return;
//		if (mod != null && mod.value == value) return;
//		
//		mod = new StatMod(source, StatModType.FLAT, value, desc);
//		flatAfterMult.put(source, mod);
//		needsRecompute = true;
//	}
	
	public void modifyFlat(String source, float value) {
		modifyFlat(source, value, null);
	}
	
	public void modifyFlat(String source, float value, String desc) {
		StatMod mod = getFlatMods().get(source);
		if (mod == null && value == 0) return;
		if (mod != null && mod.value == value) return;
		
		mod = new StatMod(source, StatModType.FLAT, value, desc);
		getFlatMods().put(source, mod);
		needsRecompute = true;
	}
	
	public void modifyPercent(String source, float value) {
		modifyPercent(source, value, null);
	}
	
	public void modifyPercent(String source, float value, String desc) {
		StatMod mod = getPercentMods().get(source);
		if (mod == null && value == 0) return;
		if (mod != null && mod.value == value) return;
		
		mod = new StatMod(source, StatModType.PERCENT, value, desc);
		getPercentMods().put(source, mod);
		needsRecompute = true;
	}
	
	public void modifyMult(String source, float value) {
		modifyMult(source, value, null);
	}
	
	public void modifyMult(String source, float value, String desc) {
		StatMod mod = getMultMods().get(source);
		if (mod == null && value == 1) return;
		if (mod != null && mod.value == value) return;
		
		mod = new StatMod(source, StatModType.MULT, value, desc);
		getMultMods().put(source, mod);
		needsRecompute = true;
	}
	
	public void unmodify() {
		if (flatMods != null) getFlatMods().clear();
		if (percentMods != null) getPercentMods().clear();
		if (multMods != null) getMultMods().clear();
		needsRecompute = true;
	}
	
	public void unmodify(String source) {
		if (flatMods != null) {
			StatMod mod = getFlatMods().remove(source);
			if (mod != null && mod.value != 0) needsRecompute = true; 
		}
		
		if (percentMods != null) {
			StatMod mod = getPercentMods().remove(source);
			if (mod != null && mod.value != 0) needsRecompute = true;
		}
		
		if (multMods != null) {
			StatMod mod = getMultMods().remove(source);
			if (mod != null && mod.value != 1) needsRecompute = true;
		}
	}
	
	public void unmodifyFlat(String source) {
		if (flatMods == null) return;
		
		StatMod mod = getFlatMods().remove(source);
		if (mod != null && mod.value != 0) needsRecompute = true;
	}
	
	public void unmodifyPercent(String source) {
		if (percentMods == null) return;
		
		StatMod mod = getPercentMods().remove(source);
		if (mod != null && mod.value != 0) needsRecompute = true;
	}
	
	public void unmodifyMult(String source) {
		if (multMods == null) return;
		
		StatMod mod = getMultMods().remove(source);
		if (mod != null && mod.value != 1) needsRecompute = true;
	}	
	
	private void recompute() {
//		float flatAMMod = 0;
		float flatMod = 0;
		float percentMod = 0;
		float mult = 1f;
		
		if (percentMods != null) {
			for (StatMod mod : getPercentMods().values()) {
				percentMod += mod.value;
			}
		}
		
		if (flatMods != null) {
			for (StatMod mod : getFlatMods().values()) {
				flatMod += mod.value;
			}
		}
		
		if (multMods != null) {
			for (StatMod mod : getMultMods().values()) {
				mult *= mod.value;
			}
		}
		
		modified = base + base * percentMod / 100f + flatMod;
		modified *= mult;
//		modified += flatAMMod;
		
		//if (modified < 0) modified = 0;
		needsRecompute = false;
	}
	
	public float computeMultMod() {
		if (multMods == null) return 1f;
		float mult = 1f;
		for (StatMod mod : getMultMods().values()) {
			mult *= mod.value;
		}
		return mult;
	}
	
	public float getModifiedValue() {
		if (needsRecompute) recompute();
		return modified;
	}
	
	public float getBaseValue() {
		return base;
	}

	public boolean isPositive() {
		return getModifiedValue() > getBaseValue();
	}
	
	public boolean isNegative() {
		return getModifiedValue() < getBaseValue();
	}
}
