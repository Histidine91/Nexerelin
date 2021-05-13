package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GBPlayerData {
	
	protected GroundBattleIntel intel;
	protected Map<GroundUnit.ForceType, Integer> losses = new HashMap<>();
	protected Map<GroundUnit.ForceType, Integer> lossesLastTurn = new HashMap<>();
	protected Map<GroundUnit.ForceType, Integer> disbanded = new HashMap<>();
	protected Map<String, Integer> sentToStorage = new HashMap<>();
	protected List<GroundUnit> units = new LinkedList<>();	
	protected PersonnelAtEntity xpTracker;
	protected CargoAPI loot;
	protected ReputationAdjustmentResult andradaRepChange;
	protected Float andradaRepAfter;
	protected Float governorshipPrice;
	protected boolean autoMoveAtEndTurn;
	
	public GBPlayerData(GroundBattleIntel intel) {
		this.intel = intel;
		xpTracker = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(
				Commodities.MARINES, intel.market.getPrimaryEntity(), 
				null, true);
	}
	
	public List<GroundUnit> getUnits() {
		return units;
	}
	
	public Map<GroundUnit.ForceType, Integer> getLosses() {
		return losses;
	}
	
	public Map<GroundUnit.ForceType, Integer> getLossesLastTurn() {
		return lossesLastTurn;
	}
	
	public Map<GroundUnit.ForceType, Integer> getDisbanded() {
		return disbanded;
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

	public boolean isAutoMoveAtEndTurn() {
		return autoMoveAtEndTurn;
	}
	
	public void setAutoMoveAtEndTurn(boolean autoMoveAtEndTurn) {
		this.autoMoveAtEndTurn = autoMoveAtEndTurn;
	}
	
	public void updateXPTrackerNum() {
		int num = 0;
		for (GroundUnit unit : units) {
			num += unit.personnel;
		}
		xpTracker.data.numMayHaveChanged(num, false);
	}
}
