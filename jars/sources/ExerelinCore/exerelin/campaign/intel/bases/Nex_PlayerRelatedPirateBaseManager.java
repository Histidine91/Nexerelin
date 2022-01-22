package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.impl.campaign.intel.bases.PlayerRelatedPirateBaseManager;

public class Nex_PlayerRelatedPirateBaseManager extends PlayerRelatedPirateBaseManager {
	// nothing here yet, this is just in case I want to override stuff later
	
	// debug stuff
	/*
	public void advance(float amount) {
		
		for (PirateBaseIntel intel : bases) {
			intel.advance(amount);
		}
		
		float days = Misc.getDays(amount);
		
		if (DebugFlags.RAID_DEBUG) {
			days *= 100f;
		}
		
		monthlyInterval.advance(days);
		
		if (monthlyInterval.intervalElapsed()) {
			removeDestroyedBases();
			
			FactionAPI player = Global.getSector().getPlayerFaction();
			List<MarketAPI> markets = Misc.getFactionMarkets(player);
			
			Iterator<MarketAPI> iter = markets.iterator();
			while (iter.hasNext()) {
				if (iter.next().isHidden()) iter.remove();
			}
			
			if (markets.isEmpty()) {
				return;
			}
			
			monthsPlayerColoniesExist++;
			
			if (!sentFirstRaid) {
				if (monthsPlayerColoniesExist >= MIN_MONTHS_BEFORE_RAID && !markets.isEmpty()) {
					sendFirstRaid(markets);
					baseCreationTimeout = MIN_TIMEOUT + random.nextInt(MAX_TIMEOUT - MIN_TIMEOUT + 1);
				}
				return;
			}
			
			Global.getLogger(this.getClass()).info("Base creation timeout: " + baseCreationTimeout);
			if (baseCreationTimeout > 0) {
				baseCreationTimeout--;
			} else {
				if (random.nextFloat() > 0.5f) {
					addBasesAsNeeded();
				}
			}
		}
	}
	
	protected void addBasesAsNeeded() {
		Global.getLogger(this.getClass()).info("Trying pirate bases targeting player");
		FactionAPI player = Global.getSector().getPlayerFaction();
		List<MarketAPI> markets = Misc.getFactionMarkets(player);
		
		Set<StarSystemAPI> systems = new LinkedHashSet<StarSystemAPI>();
		for (MarketAPI curr : markets) {
			StarSystemAPI system = curr.getStarSystem();
			if (system != null) {
				systems.add(system);
			}
		}
		if (systems.isEmpty()) {
			Global.getLogger(this.getClass()).info("  No target systems available");
			return;
		}
		
		float marketTotal = markets.size();
		int numBases = (int) (marketTotal / 2);
		if (numBases < 1) numBases = 1;
		if (numBases > 2) numBases = 2;
		
		
		if (bases.size() >= numBases) {
			Global.getLogger(this.getClass()).info(String.format("  Reached max bases: %s/%s", bases.size(), numBases));
			return;
		}
		
		
		StarSystemAPI initialTarget = null;
		float bestWeight = 0f;
		OUTER: for (StarSystemAPI curr : systems) {
			float w = 0f;
			for (MarketAPI m : Global.getSector().getEconomy().getMarkets(curr)) {
				if (m.hasCondition(Conditions.PIRATE_ACTIVITY)) continue OUTER;
				if (m.getFaction().isPlayerFaction()) {
					w += m.getSize() * m.getSize();
				}
			}
			if (w > bestWeight) {
				bestWeight = w;
				initialTarget = curr;
			}
		}
		
		if (initialTarget == null) {
			Global.getLogger(this.getClass()).info("  No target system found");
			return;
		}
		
		StarSystemAPI target = pickSystemForPirateBase(initialTarget);
		if (target == null) {
			Global.getLogger(this.getClass()).info("  No location for pirate base found");
			return;
		}
		
		PirateBaseIntel.PirateBaseTier tier = pickTier(target);
		
		String factionId = pickPirateFaction();
		if (factionId == null) return;
		
		//factionId = Factions.HEGEMONY;
		
		PirateBaseIntel intel = new PirateBaseIntel(target, factionId, tier);
		if (intel.isDone()) {
			Global.getLogger(this.getClass()).info("  Pirate base generation failed");
			intel = null;
			return;
		}
		
		intel.setTargetPlayerColoniesOnly(true);
		intel.setForceTarget(initialTarget);
		intel.updateTarget();
		bases.add(intel);
		
		baseCreationTimeout = MIN_TIMEOUT + random.nextInt(MAX_TIMEOUT - MIN_TIMEOUT + 1);
		Global.getLogger(this.getClass()).info("  Base generated, timeout " + baseCreationTimeout);
	}
	
	// runcode $print(exerelin.campaign.intel.bases.Nex_PlayerRelatedPirateBaseManager.getInstance() == null)
	public static void query() {
		Nex_PlayerRelatedPirateBaseManager instance = (Nex_PlayerRelatedPirateBaseManager)Nex_PlayerRelatedPirateBaseManager.getInstance();
		for (PirateBaseIntel intel : instance.bases) {
			
		}
		//instance.
	}
	*/
}
