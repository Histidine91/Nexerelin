package exerelin.campaign.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;

import java.util.List;

public class InteractionDialogCustomPanelPlugin extends FramedCustomPanelPlugin {
	
	public InteractionDialogCustomPanelPlugin() {
		super(0.25f, Global.getSector().getPlayerFaction().getBaseUIColor(), false);
	}

	public InteractionDialogCustomPanelPlugin(float sideRatio, boolean square) {
		super(sideRatio, Global.getSector().getPlayerFaction().getBaseUIColor(), square);
	}

	/**
	 * Moved to {@code CustomPanelPluginWithInput}, kept here for reverse compatibility reasons.
	 * DO NOT REMOVE, an unknown number of mods still use it (UAF is removing it at least)
	 */
	@Deprecated
	public static class ButtonEntry extends CustomPanelPluginWithInput.ButtonEntry {
		public ButtonEntry(ButtonAPI button, String id) {
			super(button, id);
			//Global.getLogger(this.getClass()).warn("who created me??", new Throwable());
		}

		@Override
		public void onToggle() {}
	}

	/**
	 * Moved to {@code CustomPanelPluginWithInput}, kept here for reverse compatibility reasons.
	 * DO NOT REMOVE, an unknown number of mods still use it (UAF is removing it at least)
	 */
	@Deprecated
	public static class RadioButtonEntry extends CustomPanelPluginWithInput.RadioButtonEntry {
		public RadioButtonEntry(ButtonAPI button, String id) {
			super(button, id);
			//Global.getLogger(this.getClass()).warn("who created me??", new Throwable());
		}

		public RadioButtonEntry(ButtonAPI button, String id, List<CustomPanelPluginWithInput.RadioButtonEntry> buttons) {
			super(button, id, buttons);
			//Global.getLogger(this.getClass()).warn("who created me??", new Throwable());
		}
	}
}
