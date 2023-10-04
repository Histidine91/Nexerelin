package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.SpecialItemRendererAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.Map;

public class BlueprintItem extends FactionSetupItem {
	
	public CargoAPI tempCargo;
	public String tempSpriteId;
	public SpecialItemPlugin tempForRender;
	
	@Override
	public void init(String id) {
		super.init(id);
		SpecialItemData data = getSpecialItemData();
		tempCargo = Global.getFactory().createCargo(true);
		tempCargo.addSpecial(data, 1);
		tempSpriteId = Global.getSettings().getSpecialItemSpec(data.getId()).getIconName();
		tempForRender = tempCargo.getStacksCopy().get(0).getPlugin();
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		tempForRender.createTooltip(tooltip, useGray, transferHandler, stackSource);
		//tooltip.addPara("", 10);
		
		//super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		SpecialItemData special = getSpecialItemData();
		SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(special.getId());
		
		//tooltip.addPara("Adds %s to your cargo.", 3, Misc.getHighlightColor(), spec.getName());
	}
	
	public SpecialItemData getSpecialItemData() {
		Map<String, Object> data = getDef().params;
		SpecialItemData special = new SpecialItemData((String)data.get("id"), (String)data.get("params"));
		return special;
	}
	
	@Override
	public void apply() {
		SpecialItemData special = getSpecialItemData();
		Global.getLogger(this.getClass()).info("Adding special item: " + special.getId() + ", " + special.getData());
		Global.getSector().getPlayerFleet().getCargo().addSpecial(special, 1);
	}
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
		super.render(x, y, w, h, alphaMult, glowMult, renderer);
		
		// draw the blueprint object frame manually, it's usually specified in special_items.csv
		SpriteAPI sprite = Global.getSettings().getSprite(tempSpriteId);
		
		float mult = 1f;
		sprite.setAlphaMult(alphaMult * mult);
		sprite.setNormalBlend();
		sprite.renderAtCenter(x + w/2, y + h/2);
		
		tempForRender.render(x, y, w, h, alphaMult, glowMult, renderer);
	}
}
