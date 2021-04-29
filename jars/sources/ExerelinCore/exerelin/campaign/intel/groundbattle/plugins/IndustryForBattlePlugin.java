package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.GroundDefenses;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GBDataManager;
import exerelin.campaign.intel.groundbattle.GBDataManager.IndustryDef;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class IndustryForBattlePlugin extends BaseGroundBattlePlugin {
	
	IndustryForBattle indForBattle;
	protected String defId;
		
	public void init(String defId, IndustryForBattle ind) {
		init(ind.getIntel());
		this.defId = defId;
		this.indForBattle = ind;
	}
	
	protected IndustryDef getDef() {
		return GBDataManager.getDef(defId);
	}
	
	public Float getTroopContribution(String type) {
		//if (indForBattle.getIndustry().isDisrupted()) return 0f;
		
		Float num = getDef().troopCounts.get(type);
		if (num == null) return 0f;
		
		Industry ind = indForBattle.getIndustry();
		if (ind.getSpec().hasTag(Industries.TAG_GROUNDDEFENSES)) 
		{
			if (Commodities.ALPHA_CORE.equals(ind.getAICoreId()))
				num *= 1 + GroundDefenses.ALPHA_CORE_BONUS;
			if (ind.isImproved())
				num *= 1 + GroundDefenses.IMPROVE_DEFENSE_BONUS;
		}
		if (indForBattle.getIndustry().isDisrupted()) {
			num *= GBConstants.DISRUPTED_TROOP_CONTRIB_MULT;
		}		
		
		return num;
	}
	
	public float getStrengthMult() {
		if (indForBattle.isIndustryTrueDisrupted()) {
			return 1f;
		}
		float mult = getDef().strengthMult;
		String aiCoreId = indForBattle.getIndustry().getAICoreId();
		if (Commodities.BETA_CORE.equals(aiCoreId))
			mult *= 1.1f;
		else if (Commodities.ALPHA_CORE.equals(aiCoreId))
			mult *= 1.25f;
		
		return mult;
	}
		
	@Override
	public void apply() {
		
		if (indForBattle.isIndustryTrueDisrupted()) {
			return;
		}
		
		GroundBattleSide otherSide = indForBattle.getNonHoldingSide();
		IndustryDef def = getDef();
		
		if (def.enemyDropCostMult != 1) {
			otherSide.getDropCostMod().modifyMult(indForBattle.getIndustry().getId(), def.enemyDropCostMult, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.enemyBombardmentCostMult != 1) {
			otherSide.getBombardmentCostMod().modifyMult(defId, def.enemyBombardmentCostMult, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.dropAttritionFactor != 0) {
			otherSide.getDropAttrition().modifyFlat(defId, def.dropAttritionFactor, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.dropAttritionMult != 1) {
			otherSide.getDropAttrition().modifyMult(defId, def.dropAttritionMult, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			GroundBattleIntel.applyTagWithReason(otherSide.getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					indForBattle.getIndustry().getId());
		}
		//Global.getLogger(this.getClass()).info(defId +" Drop attrition: " + otherSide.getDropAttrition().getModifiedValue());
	}	

	@Override
	public void unapply() {
		GroundBattleSide otherSide = indForBattle.getNonHoldingSide();
		IndustryDef def = getDef();
		
		otherSide.getDropCostMod().unmodify(defId);
		otherSide.getBombardmentCostMod().unmodify(defId);
		otherSide.getDropAttrition().unmodify(defId);
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			GroundBattleIntel.unapplyTagWithReason(otherSide.getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					indForBattle.getIndustry().getId());
		}
	}

	@Override
	public void addModifierEntry(TooltipMakerAPI info, CustomPanelAPI outer, 
			float width, float pad, Boolean isAttacker) {		
		if (indForBattle.isIndustryTrueDisrupted()) {
			return;
		}
		if (isAttacker == null || indForBattle.heldByAttacker != isAttacker) {
			return;
		}
		
		if (!hasTooltip()) return;
		TooltipCreator tt = getModifierTooltip();
		
		Industry ind = indForBattle.getIndustry();
		String icon = getDef().icon == null ? "graphics/icons/industry/local_production2.png" : getDef().icon;
		CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, ind.getCurrentName(), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				icon, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				null, true, tt);
		
		info.addCustom(gen.panel, pad);
	}
	
	public boolean hasTooltip() {
		if (indForBattle.isIndustryTrueDisrupted())
			return false;
		
		final IndustryDef def = getDef();
		if (def.enemyDropCostMult == 1 && def.enemyBombardmentCostMult == 1
				&& def.dropAttritionFactor == 0
				&& def.dropAttritionMult == 1
				&& !def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			return false;
		}
		return true;
	}
	
	public void processTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
		IndustryDef def = getDef();
		final Color h = Misc.getHighlightColor();
		if (def.enemyDropCostMult != 1) {
			tooltip.addPara("- " + getString("modifierEnemyDropCost"), 0, h, String.format("%.1f", def.enemyDropCostMult));
		}

		if (def.enemyBombardmentCostMult != 1) {
			tooltip.addPara("- " + getString("modifierEnemyDropCost"), 0, h, String.format("%.1f", def.enemyBombardmentCostMult));
		}
		
		if (def.dropAttritionFactor != 0) {
			tooltip.addPara("- " + getString("modifierDropAttrition"), 0, h, StringHelper.toPercent(def.dropAttritionFactor/100));
		}
		
		if (def.dropAttritionMult != 1) {
			tooltip.addPara("- " + getString("modifierDropAttritionMult"), 0, h, String.format("%.1f", def.dropAttritionMult));
		}

		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			tooltip.addPara("- " + getString("modifierPreventBombardment"), 0);
		}
	}
	
	public static IndustryForBattlePlugin loadPlugin(String defId, IndustryForBattle ind) 
	{
		String className = GBDataManager.getDef(defId).plugin;
		if (className == null) className = "exerelin.campaign.intel.groundbattle.plugins.IndustryForBattlePlugin";
		
		IndustryForBattlePlugin plugin = null;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			plugin = (IndustryForBattlePlugin)clazz.newInstance();
			plugin.init(defId, ind);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryForBattlePlugin.class).error("Failed to load industry plugin " + defId, ex);
		}
		
		return plugin;
	}
}
