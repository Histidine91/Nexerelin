package exerelin.world.factionsetup;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtils;

public class FactionSetupItemPlugin extends BaseSpecialItemPlugin {
	
	protected String id;
	protected FactionSetupItem item;
	
	@Override
	public void init(CargoStackAPI stack) {
		super.init(stack);
		id = stack.getSpecialDataIfSpecial().getData();
		FactionSetupHandler.FactionSetupItemDef def = FactionSetupHandler.getDef(id);
		item = loadFactionSetupItem(id, def.className);
	}
	
	public FactionSetupItem getItem() {
		return item;
	}

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) {
        item.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
    }
	
	@Override
	public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
		item.render(x, y, w, h, alphaMult, glowMult, renderer);
		item.renderAfter(x, y, w, h, alphaMult, glowMult, renderer);
	}
	
	@Override
    public String getDesignType() {
        return null;
    }
	
	public static <T extends FactionSetupItem> T loadFactionSetupItem(String id, String className)
	{
		FactionSetupItem item = (FactionSetupItem) NexUtils.instantiateClassByName(className);
		item.init(id);

		return (T)item;
	}
}
