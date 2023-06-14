package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.NexUtilsMarket;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;

import java.util.*;

public class HeavyIndustry extends IndustryClassGen {
	
	public static final Set<String> HEAVY_INDUSTRY = new HashSet<>(Arrays.asList(
		Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, "ms_modularFac", 
			"ms_massIndustry", "ms_militaryProduction", "ms_orbitalShipyard"));

	public static final String MEM_KEY_WANTED_SHADOWYARDS_UPGRADE = "$nex_sraModularFab_wantedUpgrade";
	/**
	 * Key = special item ID, value = the industry ID that this special item allows an upgrade to. Bi-directional with {@code SHADOWYARDS_MODULARFAC_UPGRADE_INDUSTRIES}.
	 */
	public static final Map<String, String> SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM = new LinkedHashMap<>();
	/**
	 * Key = industry ID, value = the special item to allow an upgrade to this industry. Bi-directional with {@code SHADOWYARDS_MODULARFAC_UPGRADE_ITEMS}.
	 */
	public static final Map<String, String> SHADOWYARDS_MODULARFAC_UPGRADES_BY_INDUSTRY = new LinkedHashMap<>();

	static {
		SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM.put("ms_parallelTooling", "ms_massIndustry");
		SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM.put("ms_militaryLogistics", "ms_militaryProduction");
		SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM.put("ms_specializedSystemsFabs", "ms_militaryProduction");

		for (String specialItemId : SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM.keySet()) {
			String industryId = SHADOWYARDS_MODULARFAC_UPGRADES_BY_ITEM.get(specialItemId);
			SHADOWYARDS_MODULARFAC_UPGRADES_BY_INDUSTRY.put(industryId, specialItemId);
		}

	}

	public HeavyIndustry() {
		super(HEAVY_INDUSTRY);
	}

	public String pickRandomModularFabIndustry(Random random) {
		if (random == null) random = new Random();
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		picker.addAll(SHADOWYARDS_MODULARFAC_UPGRADES_BY_INDUSTRY.keySet());
		return picker.pick();
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		// special handling for Derelict Empire: make sure capitals always have heavy industry
		if (Global.getSector().isInNewGameAdvance() 
				&& "derelict_empire".equals(ExerelinSetupData.getInstance().startScenario)) 
		{
			if (entity.isHQ) {
				//Global.getLogger(this.getClass()).info("Enforcing heavy industry for homeworld " + entity.name + "(" + entity.market.getFactionId() + ")");
				return 9999;
			}
		}
		
		MarketAPI market = entity.market;
		
		// upgrades have max priority
		if (market.hasIndustry(Industries.HEAVYINDUSTRY) || market.hasIndustry("ms_modularFac")) {
			//Global.getLogger(this.getClass()).info("Enforcing Heavy Industry upgrade on " + entity.name + "(" + entity.market.getFactionId() + ")");
			return 9999 * market.getSize();
		}
		
		// prioritise new heavy industry if we don't have any
		if (!Global.getSector().isInNewGameAdvance()) {
			if (!EconomyInfoHelper.getInstance().hasHeavyIndustry(market.getFactionId()) 
					&& market.getSize() >= 4)
				return 9999;
		}
		
		float weight = 20 + market.getSize() * 4;
				
		// bad for high hazard worlds
		weight += (150 - market.getHazardValue()) * 2;
		
		// nanoforges cause pollution on habitable worlds, so try to avoid that
		if (market.hasCondition(Conditions.POLLUTION) || !market.hasCondition(Conditions.HABITABLE))
			weight *= 2;
		else
			weight /= 2;
		
		// prefer not to be on same planet as fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			weight -= 200;
		// or light industry
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 100;
		
		weight *= getCountWeightModifier(9);
		
		weight *= getFactionMult(entity);
		
		return weight;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.ORBITALWORKS) 
				|| market.hasIndustry("ms_massIndustry")
				|| market.hasIndustry("ms_militaryProduction")
				|| market.hasIndustry("ms_orbitalShipyard"))
			return false;
		
		// don't build heavy industry on new small colonies, they're raid bait
		if (!Global.getSector().isInNewGameAdvance()) {
			int minSize = 4;
			if (EconomyInfoHelper.getInstance().hasHeavyIndustry(market.getFactionId())) {
				minSize = 5;
			}
			if (market.getSize() < minSize)
				return false;
		}
		
		return true;
	}
	
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.HEAVYINDUSTRY)) {
			Industry ind = market.getIndustry(Industries.HEAVYINDUSTRY);
			NexUtilsMarket.upgradeIndustryIfCan(ind, instant);
		}
		else if (market.hasIndustry("ms_modularFac")) {
			String wantedIndustry = market.getMemoryWithoutUpdate().getString(MEM_KEY_WANTED_SHADOWYARDS_UPGRADE);
			if (wantedIndustry == null) wantedIndustry = pickRandomModularFabIndustry(random);
			String wantedSpecial = SHADOWYARDS_MODULARFAC_UPGRADES_BY_INDUSTRY.get(wantedIndustry);
			if (Global.getSettings().getSpecialItemSpec(wantedSpecial) == null) {
				String err = String.format("Picked nonexistent special item %s for modular fac upgrade on %s",
						wantedSpecial, market.getName());
				Global.getLogger(this.getClass()).error(err);
				Global.getSector().getCampaignUI().addMessage(err, Misc.getNegativeHighlightColor(), wantedSpecial, market.getName(),
						Misc.getHighlightColor(), market.getTextColorForFactionOrPlanet());
				return;
			}

			Industry ind = market.getIndustry("ms_modularFac");
			if (!ind.canUpgrade()) return;
			ind.setSpecialItem(new SpecialItemData(wantedSpecial, null));
			//NexUtilsMarket.upgradeIndustryIfCan(ind, instant);	// checks for upgrade target spec, which doesn't exist
			NexUtilsMarket.upgradeIndustryToTarget(ind, wantedIndustry, false, true);

		}
		else {
			boolean upgrade = false;
			if (Global.getSector().isInNewGameAdvance()) {
				//upgrade = Math.random() < 0.25f;
			}
			
			String id;
			if (market.getFactionId().equals("shadow_industry"))
				id = upgrade ? "ms_specializedSystemsFabs" : "ms_modularFac";
			else
				id = upgrade ? Industries.ORBITALWORKS : Industries.HEAVYINDUSTRY;
			
			NexMarketBuilder.addIndustry(market, id, this.id, instant);
		}
		
		entity.numProductiveIndustries += 1;
	}
	
	public static boolean hasHeavyIndustry(MarketAPI market)
	{
		for (String ind : HEAVY_INDUSTRY)
		{
			if (market.hasIndustry(ind))
				return true;
		}
		return false;
	}
}
