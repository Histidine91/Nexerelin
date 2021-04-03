package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import java.util.LinkedList;
import java.util.List;

public class GroundBattleSide {
	
	protected boolean isAttacker;
	protected FactionAPI faction;
	protected List<GroundUnit> units = new LinkedList<>();
	protected PersonAPI commander;
	protected int marinesLost;
	protected int mechsLost;
	
	public GroundBattleSide(boolean isAttacker) {
		this.isAttacker = isAttacker;
	}
}
