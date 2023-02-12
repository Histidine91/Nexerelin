package exerelin.campaign.ui;

import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

public class PopupDialogScript extends DelayedDialogScreenScript
{
	protected PopupDialog popup;
	
	public PopupDialogScript(PopupDialog popup) {
		this.popup = popup;
	}

	@Override
	public boolean shouldCancel() {
		return popup.shouldCancel();
	}
	
	@Override
	protected void showDialog() {
		Global.getSector().getCampaignUI().showInteractionDialog(new PopupDialogPlugin(popup), null);
	}
	
	public static interface PopupDialog {
		public void init(InteractionDialogAPI dialog);
		public void populateOptions(OptionPanelAPI options);
		public void optionSelected(InteractionDialogAPI dialog, Object optionData);
		public boolean shouldCancel();
	}

	public static class PopupDialogPlugin implements InteractionDialogPlugin
	{
		protected PopupDialog popup;
		protected InteractionDialogAPI dialog;
		protected TextPanelAPI text;
		protected OptionPanelAPI options;
		
		public PopupDialogPlugin(PopupDialog popup) {
			this.popup = popup;
		}
		
		protected void populateOptions()
		{
			popup.populateOptions(options);
		}
		
		@Override
		public void init(InteractionDialogAPI dialog)
		{
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();
			
			popup.init(dialog);
			popup.populateOptions(options);
			dialog.setPromptText(Misc.ucFirst(StringHelper.getString("options")));
		}

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			popup.optionSelected(dialog, optionData);
		}

		@Override
		public void optionMousedOver(String optionText, Object optionData)
		{
		}

		@Override
		public void advance(float amount)
		{
		}

		@Override
		public void backFromEngagement(EngagementResultAPI battleResult)
		{
		}

		@Override
		public Object getContext()
		{
			return null;
		}

		@Override
		public Map<String, MemoryAPI> getMemoryMap()
		{
			return null;
		}
	}
}
