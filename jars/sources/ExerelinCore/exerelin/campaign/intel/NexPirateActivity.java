package exerelin.campaign.intel;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;

// same as vanilla, but effects are mitigated by higher reputation with pirates
public class NexPirateActivity extends PirateActivity {
	
	public float getStabilityRelationshipModifier() {
		RepLevel rep = market.getFaction().getRelationshipLevel(Factions.PIRATES);
		if (rep.isAtWorst(RepLevel.FRIENDLY))
			return -3;
		else if (rep.isAtWorst(RepLevel.FAVORABLE))
			return -2;
		else if (rep.isAtWorst(RepLevel.SUSPICIOUS))
			return -1;
		
		return 0;
	}
	
	public float getAccessibililityRelationshipMult() {
		RepLevel rep = market.getFaction().getRelationshipLevel(Factions.PIRATES);
		if (rep.isAtWorst(RepLevel.FRIENDLY))
			return 0;
		else if (rep == RepLevel.WELCOMING)
			return 0.2f;
		else if (rep  == RepLevel.FAVORABLE)
			return 0.4f;
		else if (rep == RepLevel.NEUTRAL)
			return 0.6f;
		else if (rep == RepLevel.SUSPICIOUS)
			return 0.8f;
		
		return 1;
	}
	
	public float getAccessibilityPenalty() {
		float accessibility = intel.getAccessibilityPenalty();
		accessibility *= getAccessibililityRelationshipMult();
		return accessibility;
	}
	
	public float getStabilityPenalty() {
		float stability = intel.getStabilityPenalty();
		stability += getStabilityRelationshipModifier();
		if (stability < 0) stability = 0;
		return stability;
	}
	
	@Override
	public void apply(String id) {
		float accessibility = getAccessibilityPenalty();
		float stability = getStabilityPenalty();
		String name = StringHelper.getString("exerelin_misc", "pirateActivityTitle");
		if (accessibility != 0) {
			market.getAccessibilityMod().modifyFlat(id, -accessibility, name);
		}
		if (stability != 0) {
			market.getStability().modifyFlat(id, -stability, name);
		}
	}
	
	@Override
	public void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		Color h = Misc.getHighlightColor();
		Color n = Misc.getNegativeHighlightColor();
		
		float pad = 3f;
		float small = 5f;
		float opad = 10f;
		
		float accessibility = getAccessibilityPenalty();
		float stability = getStabilityPenalty();
		
		if (stability != 0 && accessibility != 0) {
			tooltip.addPara(StringHelper.getString("exerelin_misc", "pirateActivityPenalty"),
					opad, h,
					"-" + (int)stability, "-" + (int)Math.round(accessibility * 100f) + "%");
		} else if (stability != 0) {
			tooltip.addPara(StringHelper.getString("exerelin_misc", "pirateActivityPenaltyStab"),
					opad, h,
					"-" + (int)stability);
		} else if (accessibility != 0) {
			tooltip.addPara(StringHelper.getString("exerelin_misc", "pirateActivityPenaltyAccess"),
					opad, h,
					"-" + (int)Math.round(accessibility * 100f) + "%");
		} else {
			tooltip.addPara(StringHelper.getString("exerelin_misc", "pirateActivityPenaltyNone"), opad);
		}
		
		FactionAPI fac = market.getFaction();
		RepLevel rep =fac.getRelationshipLevel(Factions.PIRATES);
		if (rep.isAtWorst(RepLevel.SUSPICIOUS)) {
			tooltip.addPara(StringHelper.getString("exerelin_misc", "pirateActivityPenaltyModified"),
					opad, NexUtilsReputation.getRelColor(fac.getRelationship(Factions.PIRATES)),
					rep.getDisplayName().toLowerCase());
		}
	}
}
