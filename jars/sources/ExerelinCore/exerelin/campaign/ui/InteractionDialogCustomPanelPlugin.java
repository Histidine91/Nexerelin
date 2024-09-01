package exerelin.campaign.ui;

import com.fs.starfarer.api.Global;

public class InteractionDialogCustomPanelPlugin extends FramedCustomPanelPlugin {
	
	public InteractionDialogCustomPanelPlugin() {
		super(0.25f, Global.getSector().getPlayerFaction().getBaseUIColor(), false);
	}

	public InteractionDialogCustomPanelPlugin(float sideRatio, boolean square) {
		super(sideRatio, Global.getSector().getPlayerFaction().getBaseUIColor(), square);
	}
}
