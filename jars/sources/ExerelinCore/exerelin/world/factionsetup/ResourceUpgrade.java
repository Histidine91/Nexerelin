package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.SpecialItemRendererAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import static com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.BASE_MODIFIER;
import static com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.COMMODITY;
import static com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.INDUSTRY;
import static com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.MODIFIER;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceUpgrade extends FactionSetupItem {
	
	public static final float NO_CONDITION_COST_MULT = 1.5f;
	public static final Map<String, List<String>> CONDITION_IDS = new HashMap<>();
	
	static {
		CONDITION_IDS.put("farming", new ArrayList<>(Arrays.asList(
			"farmland_poor", "farmland_adequate", "farmland_rich", "farmland_bountiful"
		)));
		CONDITION_IDS.put("ore", new ArrayList<>(Arrays.asList(
			"ore_sparse", "ore_moderate", "ore_abundant", "ore_rich", "ore_ultrarich"
		)));
		CONDITION_IDS.put("rare_ore", new ArrayList<>(Arrays.asList(
			"rare_ore_sparse", "rare_ore_moderate", "rare_ore_abundant", "rare_ore_rich", "rare_ore_ultrarich"
		)));
		CONDITION_IDS.put("organics", new ArrayList<>(Arrays.asList(
			"organics_trace", "organics_common", "organics_abundant", "organics_plentiful"
		)));
		CONDITION_IDS.put("volatiles", new ArrayList<>(Arrays.asList(
			"volatiles_trace", "volatiles_diffuse", "volatiles_abundant", "volatiles_plentiful"
		)));
	}
	
	@Override
	public int getCost() {
		MarketAPI home = getPlayerHome();
		if (home == null) return super.getCost();
		
		String type = (String)getDef().params.get("type");
		List<String> conds = CONDITION_IDS.get(type);
		for (String cond : conds) {
			if (home.hasCondition(cond))
				return super.getCost();
		}
		
		return Math.round(super.getCost() * NO_CONDITION_COST_MULT);
	}
	
	@Override
	public boolean isEnabled() {
		String type = (String)getDef().params.get("type");
		if (type.equals("farming")) return !getPlayerHome().hasCondition(Conditions.WATER_SURFACE);
		
		return true;
	}
	
	@Override
	public void apply() {
		if (!isEnabled()) return;
		
		MarketAPI home = getPlayerHome();
		if (home == null) return;
		
		String type = (String)getDef().params.get("type");
		
		List<String> conds = CONDITION_IDS.get(type);
		// already has highest-level condition, do nothing
		if (home.hasCondition(conds.get(conds.size() - 1))) {
			return;
		}
		
		for (int i=conds.size()-2; i>=0; i--) {
			String currCond = conds.get(i);
			if (home.hasCondition(currCond)) {
				
				Global.getLogger(this.getClass()).info("Removing condition " + currCond);
				unapplyResourceCondition(home, currCond);
				home.removeCondition(currCond);
				
				String newCond = conds.get(i+1);
				Global.getLogger(this.getClass()).info("Adding condition " + newCond);
				home.addCondition(newCond);
				home.getCondition(newCond).setSurveyed(true);
				break;
			}
			else if (i == 0) {
				Global.getLogger(this.getClass()).info("Adding condition " + currCond);
				home.addCondition(currCond);
				home.getCondition(currCond).setSurveyed(true);
				break;
			}
		}
		home.reapplyConditions();
		home.reapplyIndustries();
	}
	
	public void unapplyResourceCondition(MarketAPI market, String conditionId) 
	{
		MarketConditionAPI currCond = market.getCondition(conditionId);
		String idForMod = currCond.getIdForPluginModifications();
		currCond.getPlugin().unapply(idForMod);
		
		String commodityId = COMMODITY.get(conditionId);
		if (commodityId == null) return;
		
		Integer mod = MODIFIER.get(conditionId);
		if (mod == null) return;
		
		Integer baseMod = BASE_MODIFIER.get(commodityId);
		if (baseMod == null) return;
		
		String industryId = INDUSTRY.get(commodityId);
		if (industryId == null) return;
		
		Industry industry = market.getIndustry(industryId);
		if (industry == null) {
			if (Industries.FARMING.equals(industryId)) {
				industryId = Industries.AQUACULTURE;
				industry = market.getIndustry(industryId);
			}
			if (industry == null) return;
		}
		
		industry.getSupply(commodityId).getQuantity().unmodifyFlat(idForMod + "_0");
		industry.getSupply(commodityId).getQuantity().unmodifyFlat(idForMod + "_1");

	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		
		FactionSetupItemDef def = getDef();
		
		String type = (String)def.params.get("type");
		String commodity = type;
		if (commodity.equals("farming")) commodity = "food";
		
		String resName = StringHelper.getCommodityName(commodity);
		if (def.params.containsKey("resourceName"))
			resName = (String)def.params.get("resourceName");
		
		String condId = (String)CONDITION_IDS.get(type).get(0);
		String condName = Global.getSettings().getMarketConditionSpec(condId).getName();
		String costMod = (int)((NO_CONDITION_COST_MULT - 1) * 100) + "%";
		
		String desc = def.desc;
		tooltip.addPara(desc, 3, Misc.getHighlightColor(), resName, condName, costMod);
	}
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
		super.render(x, y, w, h, alphaMult, glowMult, renderer);
		
		String commodity = (String)getDef().params.get("type");
		if (commodity.equals("farming")) commodity = "food";
		
		SpriteAPI sprite = Global.getSettings().getSprite(Global.getSettings().getCommoditySpec(commodity).getIconName());
		float mult = 1f;
		
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.renderAtCenter(x + w/2, y + h/2);
		
		//mult = 0.7f;
		sprite = Global.getSettings().getSprite("ui", commodity.equals("food") ? "nex_plant" : "nex_mining");
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.setSize(36, 36);
		sprite.renderAtCenter(x + w - 24, -h/2 + 24);
	}
}
