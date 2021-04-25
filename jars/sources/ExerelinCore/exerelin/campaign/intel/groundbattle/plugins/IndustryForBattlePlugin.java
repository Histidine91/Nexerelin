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
import exerelin.campaign.intel.groundbattle.GroundBattleSide;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import java.awt.Color;

public class IndustryForBattlePlugin implements GroundBattlePlugin {
	
	IndustryForBattle indForBattle;
	protected String defId;
	
	@Override
	public void init() {
		
	}
	
	public void init(String defId, IndustryForBattle ind) {
		this.defId = defId;
		this.indForBattle = ind;
	}
	
	protected IndustryDef getDef() {
		return GBDataManager.getDef(defId);
	}
	
	public Float getTroopContribution(String type) {
		if (indForBattle.getIndustry().isDisrupted()) return 0f;
		
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
		
		if (def.enemyLiftCapMult != 1) {
			otherSide.getLiftCapacity().modifyMult(indForBattle.getIndustry().getId(), def.enemyLiftCapMult, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.enemyBombardCostMult != 1) {
			otherSide.getBombardCostMod().modifyMult(defId, def.enemyBombardCostMult, 
					indForBattle.getIndustry().getCurrentName());
		}
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			GroundBattleIntel.applyTagWithReason(otherSide.getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					indForBattle.getIndustry().getId());
		}
	}	

	@Override
	public void unapply() {
		GroundBattleSide otherSide = indForBattle.getNonHoldingSide();
		IndustryDef def = getDef();
		
		if (def.enemyLiftCapMult != 1) {
			otherSide.getLiftCapacity().unmodify(defId);
		}
		
		if (def.enemyBombardCostMult != 1) {
			otherSide.getBombardCostMod().unmodify(defId);
		}
		
		if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			GroundBattleIntel.unapplyTagWithReason(otherSide.getData(), GBConstants.TAG_PREVENT_BOMBARDMENT, 
					indForBattle.getIndustry().getId());
		}
	}

	@Override
	public void beforeTurnResolve(int turn) {
		
	}

	@Override
	public void afterTurnResolve(int turn) {
		
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
						
		TooltipCreator tt = getModifierTooltip();
		if (tt == null) return;
		
		Industry ind = indForBattle.getIndustry();
		CustomPanelGenResult gen = NexUtilsGUI.addPanelWithFixedWidthImage(outer, 
				null, width, GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, ind.getCurrentName(), 
				width - GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT - 8, 8, 
				"graphics/icons/industry/local_production2.png", GroundBattlePlugin.MODIFIER_ENTRY_HEIGHT, 3, 
				null, true, tt);
		
		info.addCustom(gen.panel, pad);
	}
	
	public TooltipCreator getModifierTooltip() {
		final IndustryDef def = getDef();
		// no special features
		if (def.enemyLiftCapMult == 1 && def.enemyBombardCostMult == 1 
				&& !def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
			return null;
		}
		
		final Color h = Misc.getHighlightColor();
		return new TooltipCreator() {
				@Override
				public boolean isTooltipExpandable(Object tooltipParam) {
					return false;
				}

				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return 360;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					if (def.enemyLiftCapMult != 1) {
						tooltip.addPara("- Enemy lift capacity %s×", 0, h, String.format("%.2f", def.enemyLiftCapMult));
					}

					if (def.enemyBombardCostMult != 1) {
						tooltip.addPara("- Enemy bombardment cost %s×", 0, h, String.format("%.2f", def.enemyBombardCostMult));
					}

					if (def.tags.contains(GBConstants.TAG_PREVENT_BOMBARDMENT)) {
						tooltip.addPara("- Prevents enemy bombardments", 0);
					}
				}
		};
	}

	@Override
	public boolean isDone() {
		return false;
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
