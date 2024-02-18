package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NexRaidActionStage extends PirateRaidActionStage {
	
	/*
		Note to self 2020-08-09:
	
			Finally figured out the damn "raid fails immediately upon arriving" issue

			When a raid arrives (vanilla pirate raid, whose code Nex raids share) 
			arrives at a system, it checks all markets the raid allows it to target

			If any of the targets is too strong (local station + all fleets of the 
			market's faction in-system, vs. all fleets of raider faction in-system), 
			it skips that target
	
			If all the targets get skipped in this way, the raid auto-fails
	
			Not sure whether to make the raid take its chances with the weakest target, 
			or just clarify the intel message to explain what it's doing
	*/
	
	public NexRaidActionStage(RaidIntel raid, StarSystemAPI system) {
		super(raid, system);
	}

	protected NexRaidIntel getNexRaidIntel() {
		return (NexRaidIntel)intel;
	}

	// Count ally strength instead of just the direct factions
	@Override
	protected void autoresolve() {
		// MODIFIED
		float str = NexWarSimScript.getFactionAndAlliedStrength(intel.getFaction(), null, system);
		float enemyStr = WarSimScript.getEnemyStrength(intel.getFaction(), system);

		status = RaidIntel.RaidStageStatus.FAILURE;
		for (MarketAPI target : targets) {
			if (!target.getFaction().isHostileTo(intel.getFaction())) continue;

			float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), system, target.getPrimaryEntity());
			if (defensiveStr >= str) {
				continue;
			}

			Industry station = Misc.getStationIndustry(target);
			if (station != null) {
				OrbitalStation.disrupt(station);
			}

//			float raidStr = intel.getRaidFP() / intel.getNumFleets() * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
//			new MarketCMD(target.getPrimaryEntity()).doGenericRaid(intel.getFaction(), raidStr);
			performRaid(null, target);

			str -= defensiveStr * 0.5f;
			status = RaidIntel.RaidStageStatus.SUCCESS;
		}
		removeMilScripts();
	}

	// Simplified version of PirateRaidActionStage.updateRoutes()

	/**
	 * Finds the weakest target market in this system and attacks it.
	 */
	protected void targetWeakest() {
		List<MarketAPI> targets = getTargets();
		if (targets.isEmpty()) return;
		
		Map<FactionAPI, Float> factionStrs = new HashMap<>();
		for (MarketAPI target : targets) 
		{
			FactionAPI faction = target.getFaction();
			if (factionStrs.containsKey(faction)) continue;
			
			factionStrs.put(faction, WarSimScript.getFactionStrength(faction, system));
		}
		
		MarketAPI weakest = null;
		float weakestStr = 999999999;
		
		for (MarketAPI target : targets) {
			FactionAPI faction = target.getFaction();
			float defensiveStr = factionStrs.get(faction) + WarSimScript.getStationStrength(
					faction, system, target.getPrimaryEntity());
			if (defensiveStr < weakestStr) {
				weakestStr = defensiveStr;
				weakest = target;
			}
		}
		
		RaidActionSubStage step = new RaidActionSubStage();
		step.duration = 20f + 10f * (float) Math.random();

		float weight = 1f;
		Industry station = Misc.getStationIndustry(weakest);
		if (station != null && station.getDisruptedDays() < step.duration) {
			step.duration += 10f + (float) Math.random() * 5f;
			weight += 1f;
		}

		step.targets.add(new Pair<SectorEntityToken, Float>(weakest.getPrimaryEntity(), weight));
		steps.add(step);
		this.targets.add(weakest);
		
		maxDays = step.duration;
	}
	
	@Override
	protected void updateRoutes() {
		super.updateRoutes();
		// All targets too strong, either pick the weakest one, or retreat and say so
		if (steps.isEmpty()) {
			if (((NexRaidIntel)intel).shouldRetreatIfOvermatched()) {
				((OffensiveFleetIntel)intel).reportOutcome(OffensiveOutcome.RETREAT_BEFORE_ACTION);
			}
			else {
				targetWeakest();
			}
		}
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (market == null || !market.isInEconomy()) return;
		if (!market.getFaction().isHostileTo(intel.getFaction())) return;
		
		float raidStr = intel.getRaidFP() / intel.getNumFleets() * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
		if (fleet != null && fleet.getCargo().getMaxPersonnel() > 0) {
			raidStr = Nex_MarketCMD.getRaidStr(fleet);
		}
		
		float maxPenalty = 3f;
		
		Nex_MarketCMD cmd = new Nex_MarketCMD(market.getPrimaryEntity());
		Industry toDisrupt = pickIndustryToDisrupt(market);
		// disrupt industries
		if (toDisrupt != null)
			cmd.doIndustryRaid(intel.getFaction(), raidStr, toDisrupt, 0.4f);
		// stability damage
		cmd.doGenericRaid(intel.getFaction(), raidStr, maxPenalty);
	}
	
	protected Industry pickIndustryToDisrupt(MarketAPI market) {
		String preferred = getNexRaidIntel().getPreferredIndustryTarget();
		if (preferred != null) {
			Industry ind = market.getIndustry(preferred);
			if (ind != null) return ind;
		}

		WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
		for (Industry industry : market.getIndustries()) {
			if (!industry.canBeDisrupted()) continue;
			if (industry.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) continue;
			float weight = 1;
			if (industry.getDisruptedDays() > 0)
				weight *= 0.5f;
			picker.add(industry, weight);
		}
		return picker.pick();
	}
	
	protected int getNumBombardables(MarketAPI market) {
		int count = 0;
		float dur = Nex_MarketCMD.getBombardDisruptDuration();
		for (Industry ind : market.getIndustries()) {
			if (ind.getSpec().hasTag(Industries.TAG_TACTICAL_BOMBARDMENT)) {
				if (ind.getDisruptedDays() >= dur * 0.8f) continue;
				count++;
			}
		}
		return count;
	}
	
	@Override
	protected List<MarketAPI> getTargets() {
		boolean pirateInvasions = NexConfig.allowPirateInvasions;
		List<MarketAPI> targets = new ArrayList<>();
		NexRaidIntel raid = getNexRaidIntel();
		for (MarketAPI market : Misc.getMarketsInLocation(system)) {
			if (market.isHidden()) continue;
			if (!market.getFaction().isHostileTo(intel.getFaction())) continue;
			if (raid.getProxyForFaction() != null && !raid.getProxyForFaction().isHostileTo(market.getFaction())) continue;
			if (!pirateInvasions && !(intel instanceof RemnantRaidIntel)
					&& NexUtilsFaction.isPirateFaction(market.getFactionId())) 
				continue;
			targets.add(market);
		}
		return targets;
	}

	@Override
	public void notifyStarted() {
		super.notifyStarted();
		
		RaidCondition.addOrUpdateConditionsForMarkets(system, intel);
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		NexRaidIntel raid = ((NexRaidIntel)intel);
		
		if (raid.getOutcome() == OffensiveOutcome.RETREAT_BEFORE_ACTION) {
			float opad = 10f;
			String str = StringHelper.getString("nex_fleetIntel", "stageActionRetreated");
			str = StringHelper.substituteToken(str, "$location", raid.getTarget().getContainingLocation().getNameWithLowercaseType());
			str = StringHelper.substituteToken(str, "$theForceType", raid.getForceTypeWithArticle(), true);
			str = StringHelper.substituteToken(str, "$theAction", raid.getActionNameWithArticle(), true);
			
			info.addPara(str, opad);
			return;
		}
		
		super.showStageInfo(info);
		
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		if (status == RaidIntel.RaidStageStatus.ONGOING && curr == index) {
			
			if (Global.getSettings().isDevMode()) {
				info.addPara("DEBUG: Raid ends in %s days", 10, Misc.getHighlightColor(), 
						String.format("%.1f", maxDays - elapsed));
			}
		}
	}
}
