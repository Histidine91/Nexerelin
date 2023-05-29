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
	 * Moved to {@code CustomPanelPluginWithInput}, kept here for reverse compatibility reasons (which turned out to not work).
	 */
	@Deprecated
	public static class ButtonEntry extends CustomPanelPluginWithInput.ButtonEntry {
		public ButtonEntry(ButtonAPI button, String id) {
			super(button, id);
		}

		@Override
		public void onToggle() {}
	}

	/**
	 * Moved to {@code CustomPanelPluginWithInput}, kept here for reverse compatibility reasons (which turned out to not work).
	 */
	@Deprecated
	public static class RadioButtonEntry extends CustomPanelPluginWithInput.RadioButtonEntry {
		public RadioButtonEntry(ButtonAPI button, String id) {
			super(button, id);
		}

		public RadioButtonEntry(ButtonAPI button, String id, List<CustomPanelPluginWithInput.RadioButtonEntry> buttons) {
			super(button, id, buttons);
		}
	}
}
