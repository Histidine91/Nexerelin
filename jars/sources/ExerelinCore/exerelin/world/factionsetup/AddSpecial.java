package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.SpecialItemRendererAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;
import java.util.ArrayList;
import java.util.List;

public class AddSpecial extends FactionSetupItem {
		
	@Override
	public void apply() {
		FactionSetupItemDef def = getDef();
		List entries = (List)def.params.get("items");
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		for (int i=0; i<entries.size(); i++) {
			List item = (List)entries.get(0);
			String id = (String)item.get(0);
			String param = null;
			if (item.size() >= 2) param = (String)item.get(1);
			cargo.addSpecial(new SpecialItemData(id, param), 1);
		}
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		
		FactionSetupItemDef def = getDef();
		
		String desc = def.desc;
		List<String> sub = new ArrayList<>();
		List entries = (List)def.params.get("items");
		for (Object entry : entries) {
			List item = (List)entry;
			String id = (String)item.get(0);
			String name = Global.getSettings().getSpecialItemSpec(id).getName();
			sub.add(name + "");
		}
		
		tooltip.addPara(desc, 3, Misc.getHighlightColor(), sub.toArray(new String[0]));
	}
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
		super.render(x, y, w, h, alphaMult, glowMult, renderer);
		
		List entries = (List)getDef().params.get("items");
		List item = (List)entries.get(0);
		String id = (String)item.get(0);
		
		SpriteAPI sprite = Global.getSettings().getSprite(Global.getSettings().getSpecialItemSpec(id).getIconName());
		float mult = 1f;
		
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.renderAtCenter(x + w/2, y + h/2);
	}
}
