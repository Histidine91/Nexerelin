package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.raid.NexAssembleStage;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;

public class InvAssembleStage extends NexAssembleStage {
	
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
			info.addPara(StringHelper.getString("exerelin_invasion", "intelStageAssembleFail"), opad);
		} else if (curr == index) {
			if (isSourceKnown()) {
				String loc = gatheringPoint.getContainingLocation().getNameWithLowercaseType();
				info.addPara(StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
						"intelStageAssembleUnknown", "$location", loc), opad);
			} else {
				info.addPara(StringHelper.getString("exerelin_invasion", "intelStageAssembleUnknown"), opad);
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
		
		base *= MathUtils.getRandomNumberInRange(0.85f, 1.15f);
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}
