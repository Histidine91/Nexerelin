package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import lombok.Getter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GBPlayerData {
	
	protected GroundBattleIntel intel;
	// FIXME: ForceType is deprecated
	@Deprecated @Getter private Map<GroundUnit.ForceType, Integer> losses = new HashMap<>();
	@Deprecated @Getter private Map<GroundUnit.ForceType, Integer> lossesLastTurn = new HashMap<>();
	@Deprecated @Getter private Map<GroundUnit.ForceType, Integer> disbanded = new HashMap<>();

	@Getter protected Map<String, Integer> lossesV2 = new HashMap<>();
	@Getter protected Map<String, Integer> lossesLastTurnV2 = new HashMap<>();
	@Getter protected Map<String, Integer> disbandedV2 = new HashMap<>();

	protected Map<String, Integer> sentToStorage = new HashMap<>();
	public int suppliesUsed;
	public int fuelUsed;
	protected List<GroundUnit> units = new LinkedList<>();	
	@Getter protected PersonnelAtEntity xpTracker;
	protected CargoAPI loot;
	protected ReputationAdjustmentResult andradaRepChange;
	protected Float andradaRepAfter;
	protected Float governorshipPrice;
	protected boolean autoMoveAtEndTurn;
	protected boolean autoMoveAllowDrop;

	/**
	 * Ratio of friend strength to enemy strength at the time of joining an ongoing battle.
	 * Used for calculating rep impact at end of battle.
	 */
	protected float strFractionAtJoinTime;	
	
	public GBPlayerData(GroundBattleIntel intel) {
		this.intel = intel;
		xpTracker = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(
				Commodities.MARINES, intel.market.getPrimaryEntity(), 
				null, true);
	}

	protected Object readResolve() {
		if (lossesV2 == null) lossesV2 = new HashMap<>();
		if (lossesLastTurnV2 == null) lossesLastTurnV2 = new HashMap<>();
		if (disbandedV2 == null) disbandedV2 = new HashMap<>();

		return this;
	}
	
	public List<GroundUnit> getUnits() {
		return units;
	}
	
	public Map<String, Integer> getSentToStorage() {
		return sentToStorage;
	}
	
	public CargoAPI getLoot() {
		return loot;
	}
	
	public void setLoot(CargoAPI loot) {
		this.loot = loot;
	}

	public void addToLoot(String commodityId, int amount) {
		loot.addCommodity(commodityId, amount);
	}

	public boolean isAutoMoveAtEndTurn() {
		return autoMoveAtEndTurn;
	}
	
	public void setAutoMoveAtEndTurn(boolean autoMoveAtEndTurn) {
		this.autoMoveAtEndTurn = autoMoveAtEndTurn;
	}
	
	public void updateXPTrackerNum() {
		int num = 0;
		for (GroundUnit unit : units) {
			num += unit.getMarines();
		}
		xpTracker.data.numMayHaveChanged(num, false);
	}
}
