package exerelin.campaign;

import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;

public class ExerelinReputationAdjustmentResult extends ReputationAdjustmentResult {
	
	public boolean wasHostile = false;
	public boolean isHostile = false;
	
	public ExerelinReputationAdjustmentResult(float delta) {
		super(delta);
	}
	
	public ExerelinReputationAdjustmentResult(float delta, boolean wasHostile, boolean isHostile) {
		super(delta);
		this.wasHostile = wasHostile;
		this.isHostile = isHostile;
	}
}
