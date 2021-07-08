package exerelin.campaign.submarkets;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

public class Nex_StoragePlugin extends StoragePlugin {
	
	public boolean isFree() {
		return market.isPlayerOwned() || Nex_IsFactionRuler.isRuler(market.getFactionId());
	}
	
	@Override
	public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
		if (isFree()) return false;
		if (action == action.PLAYER_BUY) return false;
		if (market.getFaction().isPlayerFaction()) return false;
		return super.isIllegalOnSubmarket(stack, action);
	}

	@Override
	public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
		if (isFree()) return false;
		if (market.getFaction().isPlayerFaction()) return false;
		if (action == action.PLAYER_BUY) return false;
		return super.isIllegalOnSubmarket(commodityId, action);
	}
	
	@Override
	public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
		if (isFree()) return false;
		if (market.getFaction().isPlayerFaction()) return false;
		if (action == action.PLAYER_BUY) return false;
		return super.isIllegalOnSubmarket(member, action);
	}
	
	@Override
	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		if (!market.isInEconomy()) return;
		
		float opad = 10f;
		// MODIFIED
		if (isFree()) {
			tooltip.addPara(Misc.getTokenReplaced(StringHelper.getString("exerelin_markets", 
					"storageFreeTooltip"), market.getPrimaryEntity()), opad); 
			return;
		}
		
		super.createTooltipAfterDescription(tooltip, expanded);
	}
}
