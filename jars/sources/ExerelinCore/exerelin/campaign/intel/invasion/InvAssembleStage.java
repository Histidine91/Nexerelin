package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.InvasionIntel;
import java.awt.Color;

public class InvAssembleStage extends AssembleStage {
	
	public InvAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
		super(raid, gatheringPoint);
	}

	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		if (status == RaidStageStatus.FAILURE) {
			info.addPara("The invasion force has failed to successfully assemble at the rendezvous point.", opad);
		} else if (curr == index) {
			if (isSourceKnown()) {
				info.addPara("The invasion force is currently assembling in the " + gatheringPoint.getContainingLocation().getNameWithLowercaseType() + ".", opad);
			} else {
				info.addPara("The invasion force is currently assembling at an unknown location.", opad);
			}
		}
	}
	
	@Override
	protected String pickNextType() {
		if (InvasionIntel.NO_STRIKE_FLEETS)
			return "exerelinInvasionFleet";
		
		int fleetCount = this.getRoutes().size();
		Global.getLogger(this.getClass()).info("Current fleet count: " + fleetCount);
		if (fleetCount % 3 == 1)	// first fleet will be invasion
			return "exerelinInvasionFleet";
		return "exerelinInvasionSupportFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		if (type.equals("exerelinInvasionFleet"))
			base = 180f;
		
		if (Math.random() < 0.33f)
			base *= 2f;
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}
