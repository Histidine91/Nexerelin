package exerelin.campaign.entities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin;
import static com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin.NAV_BONUS;
import static com.fs.starfarer.api.impl.campaign.NavBuoyEntityPlugin.NAV_BONUS_MAKESHIFT;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;

public class Nex_NavBuoyEntityPlugin extends NavBuoyEntityPlugin {
	
	@Override
	public void advance(float amount) {
		if (entity.getContainingLocation() == null || entity.isInHyperspace()) return;
		
		String id = getModId();
		for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
			if (fleet.isInHyperspaceTransition()) continue;
			
			if (canReceiveBonus(fleet)) {
				
				String desc = Misc.ucFirst(entity.getCustomEntitySpec().getDefaultName().toLowerCase());
				float bonus = NAV_BONUS;
				if (isMakeshift()) {
					bonus = NAV_BONUS_MAKESHIFT;
				}
				
				MutableStat.StatMod curr = fleet.getStats().getFleetwideMaxBurnMod().getFlatBonus(id);
				if (curr == null || curr.value <= bonus) {
					fleet.getStats().addTemporaryModFlat(0.1f, id,
							desc, bonus, 
							fleet.getStats().getFleetwideMaxBurnMod());
				}
			}
		}
	}
	
	public boolean canReceiveBonus(CampaignFleetAPI fleet) {
		if (fleet.getFaction() == entity.getFaction())
			return true;
		if (fleet.getFaction().isPlayerFaction() || entity.getFaction().isPlayerFaction()) {
			if (isHacked()) return true;
			if (AllianceManager.areFactionsAllied(entity.getFaction().getId(), Misc.getCommissionFactionId())) 
				return true;
		}
		if (AllianceManager.areFactionsAllied(fleet.getFaction().getId(), entity.getFaction().getId()))
			return true;
		
		return false;
	}
}
