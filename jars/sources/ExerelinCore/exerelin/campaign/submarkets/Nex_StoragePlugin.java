package exerelin.campaign.submarkets;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class Nex_StoragePlugin extends StoragePlugin {
	
	public boolean isFree() {
		return market.isPlayerOwned() || market.getFaction().isPlayerFaction();
	}
	
	@Override
	public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
		if (isFree()) return false;
		if (action == action.PLAYER_BUY) return false;
		return super.isIllegalOnSubmarket(stack, action);
	}

	@Override
	public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
		if (isFree()) return false;
		if (action == action.PLAYER_BUY) return false;
		return super.isIllegalOnSubmarket(commodityId, action);
	}
	
	@Override
	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		if (!market.isInEconomy()) return;
		
		float opad = 10f;
		// MODIFIED
		if (isFree()) {
			tooltip.addPara(Misc.getTokenReplaced("$market is under your control, and there " +
					"are no storage fees or expenses.", market.getPrimaryEntity()), opad); 
			return;
		}
		
		super.createTooltipAfterDescription(tooltip, expanded);
	}
}
