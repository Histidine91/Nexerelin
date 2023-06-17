package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsMarket;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddOrUpgradeIndustry extends FactionSetupItem {
	
	public static final Map<String, List<String>> CONDITION_IDS = new HashMap<>();
		
	@Override
	public void apply() {
		FactionSetupItemDef def = getDef();
		MarketAPI home = getPlayerHome();
		if (home == null) return;
		
		String toUpgrade = (String)def.params.get("upgrade");
		if (home.hasIndustry(toUpgrade)) {
			Industry ind = home.getIndustry(toUpgrade);
			String target = (String)def.params.get("upgradeTo");
			if (target == null) target = ind.getSpec().getUpgrade();
			if (target == null) {
				Global.getLogger(this.getClass()).error("No upgrade target for industry " + ind.getCurrentName());
			}
			NexUtilsMarket.upgradeIndustryToTarget(ind, target, true, true);
			return;
		}
		
		String toAdd = (String)def.params.get("add");
		if (home.hasIndustry(toAdd)) return;
		home.addIndustry(toAdd);
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		
		FactionSetupItemDef def = getDef();
		
		String toAdd = (String)def.params.get("add");
		String industryName = Global.getSettings().getIndustrySpec(toAdd).getName();
		
		String desc = def.desc;
		tooltip.addPara(desc, 3, Misc.getHighlightColor(), industryName);
	}
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemPlugin.SpecialItemRendererAPI renderer) {
		super.render(x, y, w, h, alphaMult, glowMult, renderer);
		
		String toAdd = (String)getDef().params.get("add");
		String spriteName = Global.getSettings().getIndustrySpec(toAdd).getImageName();
		
		SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
		float mult = 1f;
		
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.setSize(120, 60);
		//sprite.renderRegion(x + w/2, y + h/2, 47, 0, 96, 95);
		sprite.renderRegionAtCenter(x + w/2, y + h/2, 0.18f, 0, 0.64f, 1);
		
		sprite = Global.getSettings().getSprite("ui", "nex_factionsetup_industry_frame");
		sprite.setSize(80, 80);
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.renderAtCenter(x + w/2, y + h/2);
	}
}
