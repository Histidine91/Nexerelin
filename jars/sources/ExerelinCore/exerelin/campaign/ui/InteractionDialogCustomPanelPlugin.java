package exerelin.campaign.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class InteractionDialogCustomPanelPlugin extends FramedCustomPanelPlugin {
	
	protected List<ButtonEntry> buttons = new LinkedList<>();
	
	public InteractionDialogCustomPanelPlugin() {
		super(0.25f, Global.getSector().getPlayerFaction().getBaseUIColor(), false);
	}

	public InteractionDialogCustomPanelPlugin(float sideRatio, boolean square) {
		super(sideRatio, Global.getSector().getPlayerFaction().getBaseUIColor(), square);
	}
	
	public void addButton(ButtonEntry entry) {
		buttons.add(entry);
	}
	
	public void checkButtons() {
		Iterator<ButtonEntry> iter = buttons.iterator();
		while (iter.hasNext()) {
			iter.next().checkButton();
		}
	}
	
	@Override
	public void advance(float amount) {
		checkButtons();
	}
	
	@Override
	public void processInput(List<InputEventAPI> input) {
		/*
		for (InputEventAPI event : input) {
			if (event.isMouseEvent()) {
				checkButtons();				
				break;
			}
		}
		*/
	}
	
	public static abstract class ButtonEntry {
		public ButtonAPI button;
		public boolean state;
		public String id;
		
		public ButtonEntry() {
		}
		
		public ButtonEntry(ButtonAPI button, String id) {
			this.button = button;
			this.id = id;
			state = button.isChecked();
		}
		
		public void setState(boolean state) {
			this.state = state;
			button.setChecked(state);
		}
		
		public void checkButton() {
			if (state != button.isChecked()) {
				state = button.isChecked();
				onToggle();
			}
			//Global.getLogger(this.getClass()).info("Button " + id + ": " + button.isChecked());
		}
		
		public abstract void onToggle();
	}
	
	public static class RadioButtonEntry extends ButtonEntry {
		
		public List<RadioButtonEntry> buttons;

		/**
		 * Use the other constructor, the one that specifies the buttons.
		 * @param button
		 * @param id
		 */
		@Deprecated
		public RadioButtonEntry(ButtonAPI button, String id) {
			super(button, id);
		}

		public RadioButtonEntry(ButtonAPI button, String id, List<RadioButtonEntry> buttons) {
			super(button, id);
			this.buttons = buttons;
		}
		
		@Override
		public void onToggle() {
			for (RadioButtonEntry entry : buttons) {
				if (entry != this) {
					entry.setState(false);
					//Global.getLogger(this.getClass()).info("Toggling other button " + entry.id);
				}
				else entry.setState(true);
			}
			onToggleImpl();
		}
		
		public void onToggleImpl() {
			
		}
	}
}
