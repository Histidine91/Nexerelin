package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleCampaignListener;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import java.util.HashSet;
import java.util.Set;

/**
 * When player fleet is seen by a fleet in a location, record that fact to location's memory.
 */
public class PlayerInSystemTracker implements EveryFrameScript, ColonyPlayerHostileActListener, GroundBattleCampaignListener {
	
	public static final float REMEMBER_PLAYER_TIME = 15;
	public static final String MEMORY_KEY_PREFIX = "$nex_factionSeenPlayer_";
	
	protected LocationAPI currLoc;
	protected IntervalUtil interval = new IntervalUtil(0.05f, 0.06f);
	protected Set<String> factionsToTrack = new HashSet<>();
	
	public static boolean hasFactionSeenPlayer(LocationAPI loc, String factionId) {
		String memKey = MEMORY_KEY_PREFIX + factionId;
		return loc.getMemoryWithoutUpdate().getBoolean(memKey);
	}
	
	public static PlayerInSystemTracker create() {
		PlayerInSystemTracker tracker = new PlayerInSystemTracker();
		Global.getSector().addTransientScript(tracker);
		Global.getSector().getListenerManager().addListener(tracker, true);
		return tracker;
	}
	
	protected void updateLocationData() {
		LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
		if (loc != currLoc) {
			currLoc = loc;
			factionsToTrack.clear();
			if (currLoc == null || currLoc.isHyperspace())
				return;
			
			// List all factions that want to know the player is here
			// They must have a presence in system and be hostile to player
			for (MarketAPI market : Global.getSector().getEconomy().getMarkets(currLoc)) {
				FactionAPI faction = market.getFaction();
				if (faction.isPlayerFaction()) continue;
				String factionId = faction.getId();
				if (factionsToTrack.contains(factionId)) continue;
				if (!faction.isHostileTo(Factions.PLAYER)) continue;
				
				factionsToTrack.add(factionId);
			}
		}
	}
	
	/**
	 * Records the factions that know player is in this location, to that location's memory.
	 * Player is known if another faction fleet spots it, or it commits a hostile action. 
	 */
	protected void updateSeenPlayerFactions() {
		if (currLoc == null || currLoc.isHyperspace())
			return;
		
		Set<String> alreadySeenFactions = new HashSet<>();
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		for (CampaignFleetAPI fleet : currLoc.getFleets()) {
			if (fleet == player) continue;
			String factionId = fleet.getFaction().getId();
			if (!factionsToTrack.contains(factionId))
				continue;
			if (alreadySeenFactions.contains(factionId))
				continue;
			
			if (player.isVisibleToSensorsOf(fleet)) {
				alreadySeenFactions.add(factionId);
				spotPlayer(factionId);
			}
		}
	}
	
	public void spotPlayer(String factionId) {
		spotPlayer(factionId, REMEMBER_PLAYER_TIME);
	}
	
	public void spotPlayer(String factionId, float time) {
		if (currLoc == null) return;
		
		String memKey = MEMORY_KEY_PREFIX + factionId;
		MemoryAPI mem = currLoc.getMemoryWithoutUpdate();
		if (mem.contains(memKey) && mem.getExpire(memKey) > time) return;
		
		currLoc.getMemoryWithoutUpdate().set(memKey, true, time);
	}
	
	@Override
	public void advance(float amount) {
		float days = Misc.getDays(amount);
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		updateLocationData();
		updateSeenPlayerFactions();
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) 
	{
		spotPlayer(market.getFactionId());
	}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry industry)
	{
		spotPlayer(market.getFactionId());
	}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
		spotPlayer(market.getFactionId(), REMEMBER_PLAYER_TIME * 2);
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
		spotPlayer(market.getFactionId(), REMEMBER_PLAYER_TIME * 3);
	}

	@Override
	public void reportBattleStarted(GroundBattleIntel battle) {
		if (!battle.isPlayerInitiated()) return;
		if (!Boolean.TRUE.equals(battle.isPlayerAttacker())) return;
		spotPlayer(battle.getMarket().getFactionId(), REMEMBER_PLAYER_TIME * 2);
	}

	@Override
	public void reportBattleBeforeTurn(GroundBattleIntel battle, int turn) {}

	@Override
	public void reportBattleAfterTurn(GroundBattleIntel battle, int turn) {
		if (!Boolean.TRUE.equals(battle.isPlayerAttacker())) return;
		spotPlayer(battle.getMarket().getFactionId(), REMEMBER_PLAYER_TIME * 2);
	}

	@Override
	public void reportBattleEnded(GroundBattleIntel battle) {}

	@Override
	public void reportPlayerJoinedBattle(GroundBattleIntel battle) {
		if (!Boolean.TRUE.equals(battle.isPlayerAttacker())) return;
		spotPlayer(battle.getMarket().getFactionId(), REMEMBER_PLAYER_TIME * 2);
	}
}
