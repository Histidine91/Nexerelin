package exerelin.campaign.intel;

import java.util.HashSet;
import java.util.Set;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.impl.campaign.shared.PersonBountyEventData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import java.util.List;
import java.util.Map;

public class FactionBountyManager extends BaseEventManager {

	public static final String KEY = "$nex_factionBountyManager";
	public static final float MAX_CONCURRENT_MULT = 0.2f;
	
	@Override
	protected int getMinConcurrent() {
		return 0;
	}
	@Override
	protected int getMaxConcurrent() {
		return (int)Math.ceil(SectorManager.getLiveFactionIdsCopy().size() * MAX_CONCURRENT_MULT);
	}

	@Override
	protected EveryFrameScript createEvent() {
		//if ((float) Math.random() < 0.1f) return null;
		
		FactionAPI faction = pickFaction();
		if (faction == null) return null;
		
		FactionBountyIntel intel = new FactionBountyIntel(faction);
		return intel;
	}
	
	
	public boolean isActive(FactionAPI faction) {
		for (EveryFrameScript s : getActive()) {
			
			if (faction == ((FactionBountyIntel)s).getFactionForUIColors()) return true;
		}
		return false;
	}
	
	public FactionBountyIntel getActive(FactionAPI faction) {
		for (EveryFrameScript s : getActive()) {
			FactionBountyIntel intel = (FactionBountyIntel) s;
			if (intel.isDone()) continue;
			
			if (faction == intel.getFactionForUIColors()) return intel;
		}
		return null;
	}
	
	public void addOrResetBounty(FactionAPI faction) {
		if (faction != null) {
			FactionBountyIntel active = getActive(faction);
			if (active != null) {
				active.reset();
			} else {
				addActive(new FactionBountyIntel(faction));
			}
		}
	}
	
	protected FactionAPI pickFaction() {
		Set<FactionAPI> already = new HashSet<FactionAPI>();
		for (EveryFrameScript s : getActive()) {
			already.add(((FactionBountyIntel)s).getFactionForUIColors());
		}
		
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();
		boolean allowEnemies = Global.getSettings().getBoolean("nex_factionBounty_issuedByEnemies");
		PersonBountyEventData data = SharedData.getData().getPersonBountyEventData();
		
		for (String factionId : liveFactions) {
			if (factionId.equals(Factions.PLAYER))
				continue;
			
			//if (!data.isParticipating(factionId)) continue;
			
			// pirates don't issue bounties
			if (NexConfig.getFactionConfig(factionId).pirateFaction)
				continue;
			
			FactionAPI faction = Global.getSector().getFaction(factionId);
			if (already.contains(faction)) continue;
			if (faction.getCustomBoolean(Factions.CUSTOM_POSTS_NO_BOUNTIES)) continue;
			if (!allowEnemies && faction.isHostileTo(Factions.PLAYER)) continue;
			
			// weight based on number of enemies this faction has
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, true, false);
			float weight = 0;
			int count = enemies.size();
			for (String enemyId : enemies)
			{
				float thisWeight = 8;
				if (faction.isAtBest(enemyId, RepLevel.VENGEFUL))
					thisWeight *= 2.5f;
				
				weight += thisWeight;
			}
			if (NexConfig.getFactionConfig(factionId).hostileToAll >= 1)
				weight /= (count * 0.5f);
			else
				weight *= 0.5f;
			
			if (weight > 0)
				picker.add(faction, weight);
		}
		//picker.print("Bounty weights: ");
		
		FactionAPI faction = picker.pick();
		float w = picker.getWeight(faction);
		
		float probMult = 1f / (getOngoing() + 1f);
		
		if ((float) Math.random() > w * 0.01f * probMult) {
			faction = null;
		}
		
		return faction;
	}
	
	public static FactionBountyManager getInstance() {
		Map<String, Object> data = Global.getSector().getPersistentData();
		return (FactionBountyManager) data.get(KEY); 
	}
	
	public static void create() {
		Map<String, Object> data = Global.getSector().getPersistentData();
		FactionBountyManager manager = new FactionBountyManager();
		data.put(KEY, manager);
	}
	
	// runcode exerelin.campaign.intel.FactionBountyManager.getInstance().addOrResetBounty(Global.getSector().getFaction("hegemony"))
}








