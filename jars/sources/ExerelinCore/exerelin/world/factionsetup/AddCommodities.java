package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.SpecialItemRendererAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;
import java.util.ArrayList;
import java.util.List;

public class AddCommodities extends FactionSetupItem {
		
	@Override
	public void apply() {
		FactionSetupItemDef def = getDef();
		List commodities = (List)def.params.get("commodities");
		List counts = (List)def.params.get("counts");
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		for (int i=0; i<commodities.size(); i++) {
			String commodityId = (String)commodities.get(i);
			int count = (int)counts.get(i);
			cargo.addCommodity(commodityId, count);
		}
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		
		FactionSetupItemDef def = getDef();
		
		String desc = def.desc;
		List<String> sub = new ArrayList<>();
		List commodities = (List)def.params.get("commodities");
		List counts = (List)def.params.get("counts");
		for (int i=0; i<commodities.size(); i++) {
			String commodityId = (String)commodities.get(i);
			int count = (int)counts.get(i);
			String name = StringHelper.getCommodityName(commodityId);
			sub.add(count + "");
			sub.add(name + "");
		}
		
		tooltip.addPara(desc, 3, Misc.getHighlightColor(), sub.toArray(new String[0]));
	}
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
		super.render(x, y, w, h, alphaMult, glowMult, renderer);
		
		List commodities = (List)getDef().params.get("commodities");
		String commodity = (String)commodities.get(0);
		
		SpriteAPI sprite = Global.getSettings().getSprite(Global.getSettings().getCommoditySpec(commodity).getIconName());
		float mult = 1f;
		
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.renderAtCenter(x + w/2, y + h/2);
	}
}
