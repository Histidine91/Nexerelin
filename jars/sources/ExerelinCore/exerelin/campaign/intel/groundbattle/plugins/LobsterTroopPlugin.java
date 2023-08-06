package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import exerelin.campaign.intel.groundbattle.GroundUnit;

import java.util.Map;

/**
 * Just a fun example of troop type replacement.
 */
public class LobsterTroopPlugin extends BaseGroundBattlePlugin {

	@Override
	public void onBattleStart() {
		replaceTroops(intel.getSide(true));
		replaceTroops(intel.getSide(false));
	}

	public void replaceTroops(GroundBattleSide side) {
		for (GroundUnit unit : side.getUnits()) {
			if (unit.isPlayer()) continue;
			if (!unit.getUnitDefId().equals("marine")) continue;

			unit.setUnitDef("lobster");
			Map<String, Integer> pers = unit.getPersonnelMap();
			int size = unit.getSize();
			pers.clear();
			pers.put(Commodities.LOBSTER, size);
			Global.getLogger(this.getClass()).info(String.format("Replaced unit %s with lobsters", unit.getName()));
		}
	}
}
