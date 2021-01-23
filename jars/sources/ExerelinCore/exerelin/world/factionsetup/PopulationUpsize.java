package exerelin.world.factionsetup;

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ColonyManager;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;

public class PopulationUpsize extends FactionSetupItem {
		
	@Override
	public void apply() {
		ColonyManager.getManager().upsizeMarket(getPlayerHome());
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) 
	{
		super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
		
		FactionSetupItemDef def = getDef();
		
		String desc = def.desc;
		tooltip.addPara(desc, 3);
	}
}
