package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.*;

public class RaidCondition extends BaseMarketConditionPlugin {
	public static final String CONDITION_ID = "nex_raid_condition";
	public static final int STABILITY_PER_RAID = 1;
	public static final float ACCESSIBILITY_PER_RAID = 0.1f;
	
	protected List<RaidIntel> raids = new LinkedList<>();
	protected float accessPenalty = 0;
	protected float stabPenalty = 0;
	protected IntervalUtil interval = new IntervalUtil(0.9f, 1.1f);
		
	@Override
	public void apply(String id) {
		int count = raids.size() + 1;
		
		accessPenalty = count * ACCESSIBILITY_PER_RAID;
		stabPenalty = count * STABILITY_PER_RAID;
		String name = StringHelper.getString("exerelin_raid", "raids", true);
		if (accessPenalty != 0) {
			market.getAccessibilityMod().modifyFlat(id, -accessPenalty, name);
		}
		if (stabPenalty != 0) {
			market.getStability().modifyFlat(id, -stabPenalty, name);
		}
	}

	@Override
	public void unapply(String id) {
		market.getAccessibilityMod().unmodifyFlat(id);
		market.getStability().unmodifyFlat(id);
	}
	
	@Override
	public void advance(float amount) {
		if (!market.isInEconomy()) {
			market.removeSpecificCondition(condition.getIdForPluginModifications());
		}
		NexUtils.advanceIntervalDays(interval, amount);
		if (interval.intervalElapsed()) {
			refreshRaids();
		}
	}
	
	public void refreshRaids() {
		List<RaidIntel> toRemove = new LinkedList<>();
		for (RaidIntel raid : raids) {
			if (raid.isEnding() || raid.isEnded() || !raid.getFaction().isHostileTo(market.getFaction())) {
				toRemove.add(raid);
			}
		}
		raids.removeAll(toRemove);
		checkForRemove();
	}
	
	protected void checkForRemove() {
		if (raids.isEmpty()) {
			market.removeSpecificCondition(condition.getIdForPluginModifications());
		}
	}
	
	@Override
	public boolean isTransient() {
		return false;
	}

	@Override
	public void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		Color h = Misc.getHighlightColor();
		
		float pad = 3f;
		float opad = 10f;
		
		if (stabPenalty != 0 || accessPenalty != 0) {
			tooltip.addPara(StringHelper.getString("exerelin_raid", "conditionPenalties"), 
					opad, h,
					"-" + (int)stabPenalty, "-" + (int)Math.round(accessPenalty * 100f) + "%");
		}
		// list raiders
		tooltip.addPara(StringHelper.getString("exerelin_raid", "conditionListHeader"), opad);
		Set<FactionAPI> alreadySeen = new HashSet<>();
		float thisPad = pad;
		for (RaidIntel raid : raids) {
			FactionAPI faction = raid.getFaction();
			if (alreadySeen.contains(faction)) continue;
			alreadySeen.add(faction);
			tooltip.addPara(" - " + faction.getDisplayName(), thisPad, faction.getBaseUIColor(), faction.getDisplayName());
			thisPad = 0;
		}
	}

	@Override
	public float getTooltipWidth() {
		return super.getTooltipWidth();
	}

	@Override
	public boolean hasCustomTooltip() {
		return true;
	}
	
	@Override
	public String getIconName() {
		if (!raids.isEmpty())
			return raids.get(0).getFaction().getCrest();
		return null;
	}
	
	public static void addOrUpdateConditionsForMarkets(LocationAPI loc, RaidIntel raid) 
	{
		// copy to new array to avoid concurrent modification
		List<MarketAPI> markets = new ArrayList<>(Global.getSector().getEconomy().getMarkets(loc));
		
		for (MarketAPI market : markets) {
			addOrUpdateConditionForMarket(market, raid);
		}
	}
	
	public static void addOrUpdateConditionForMarket(MarketAPI market, RaidIntel raid) {
		if (!market.getFaction().isHostileTo(raid.getFaction()))
			return;
		
		if (!market.hasCondition(CONDITION_ID))
			market.addCondition(CONDITION_ID);
		
		RaidCondition cond = (RaidCondition)market.getCondition(CONDITION_ID).getPlugin();
		
		if (cond.raids.contains(raid))
			return;
		
		cond.raids.add(raid);
		market.reapplyConditions();
	}
	
	public static void removeRaidFromCondition(MarketAPI market, RaidIntel raid) {
		if (!market.hasCondition(CONDITION_ID))
			return;
		
		RaidCondition cond = (RaidCondition)market.getCondition(CONDITION_ID).getPlugin();
		cond.raids.remove(raid);
		cond.checkForRemove();
	}
	
	public static void removeRaidFromConditions(LocationAPI loc, RaidIntel raid) {
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc)) {
			removeRaidFromCondition(market, raid);
		}
	}
}