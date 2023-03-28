package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;

public class RespawnInvasionIntel extends InvasionIntel {
	
	public RespawnInvasionIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("exerelin_invasion", "respawnInvasion");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theRespawnInvasion");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("exerelin_invasion", "respawnInvasionForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theRespawnInvasionForce");
	}
	
	@Override
	protected String getDescString() {
		return StringHelper.getString("exerelin_invasion", "intelDescRespawn");
	}

	@Override
	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.RESPAWN;
	}
	
	public static List<RespawnInvasionIntel> getOngoing() {
		List<RespawnInvasionIntel> ongoing = new ArrayList<>();
		List<IntelInfoPlugin> ongoingRaw = Global.getSector().getIntelManager().getIntel(RespawnInvasionIntel.class);
		for (IntelInfoPlugin intel : ongoingRaw) {
			ongoing.add((RespawnInvasionIntel)intel);
		}
		return ongoing;
	}
}
