package exerelin.campaign.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.Map;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
public class FieldOptionsScreenScript implements EveryFrameScript
{
	protected transient boolean keyDown = false;

	@Override
	public boolean isDone()
	{
		return false;
	}

	@Override
	public boolean runWhilePaused()
	{
		return true;
	}

	
	@Override
	public void advance(float amount)
	{
		// Don't do anything while in a menu/dialog		
		if (Global.getSector().isInNewGameAdvance() || Global.getSector().getCampaignUI().isShowingDialog() 
				|| Global.getCurrentState() == GameState.TITLE)
		{
			return;
		}
		
		if (Keyboard.isKeyDown(NexConfig.directoryDialogKey))
		{
			keyDown = true;
		}
		else {
			if (keyDown) {
				CampaignFleetAPI player =  Global.getSector().getPlayerFleet();
				boolean success = Global.getSector().getCampaignUI().showInteractionDialog(
						new RuleBasedInteractionDialogPluginImpl(), player);
				if (success) {
					InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
					RuleBasedDialog rbd = ((RuleBasedDialog) dialog.getPlugin());
					dialog.getVisualPanel().showFleetInfo(null, player, null, null);
					FireAll.fire(null, dialog, rbd.getMemoryMap(), "ExerelinMarketSpecial");
				}
				keyDown = false;
			}
		}
	}

	// now handled via rules
	@Deprecated
	public static class FactionDirectoryDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		private InteractionDialogAPI dialog;
		private TextPanelAPI text;
		private OptionPanelAPI options;

		protected RuleBasedInteractionDialogPluginImpl optionsDialogDelegate;

		public static enum Menu
		{
			INIT,
			DIRECTORY,
			FLEET_REQUEST,
			REMOTE_COMM,
			REMOTE_SUSPEND_AUTONOMY,
			INTEL_SCREEN,
			//COLONY_SCREEN,
			EXIT
		}

		@Override
		public void init(InteractionDialogAPI dialog)
		{
			FleetInteractionDialogPluginImpl.inConversation = false;
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();

			//dialog.setTextWidth(Display.getWidth() * .9f);

			//dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
			initMenu();
		}

		void initMenu()
		{
			options.clearOptions();
			options.addOption(StringHelper.getString("exerelin_factions", "factionDirectoryOption"), Menu.DIRECTORY);
			options.addOption(StringHelper.getString("nex_fleetRequest", "fleetRequest", true), Menu.FLEET_REQUEST);
			//options.addOption(StringHelper.getString("exerelin_markets", "remoteCommDirectory"), Menu.REMOTE_COMM);
			options.addOption(StringHelper.getString("exerelin_markets", "remoteSuspendAutonomy"), Menu.REMOTE_SUSPEND_AUTONOMY);
			options.addOption(StringHelper.getString("exerelin_misc", "intelScreen"), Menu.INTEL_SCREEN);
			options.addOption(Misc.ucFirst(StringHelper.getString("close")), Menu.EXIT);
			options.setShortcut(Menu.INTEL_SCREEN, Keyboard.KEY_E, false, false, false, true);
			options.setShortcut(Menu.EXIT, Keyboard.KEY_ESCAPE, false, false, false, true);
			dialog.setPromptText(StringHelper.getString("options", true) + ":");
		}

		// NOTE: we use FleetInteractionDialogPluginImpl.inConversation to tell whether we're currently delegating stuff to the RuleBasedInteractionDialogPlugin

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			if (optionData == null) return;
			if (FleetInteractionDialogPluginImpl.inConversation) {
				if (optionsDialogDelegate == null)
				{
					optionsDialogDelegate = new RuleBasedInteractionDialogPluginImpl();
					optionsDialogDelegate.setEmbeddedMode(true);
					optionsDialogDelegate.init(dialog);
				}

				optionsDialogDelegate.optionSelected(optionText, optionData);
				if (!FleetInteractionDialogPluginImpl.inConversation || optionData == Menu.INIT) {
					FleetInteractionDialogPluginImpl.inConversation = false;
					optionSelected(null, Menu.INIT);
				}
				return;
			}
			else if (optionText != null) {
				text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
			}
			
			if (optionData == Menu.DIRECTORY || optionData == Menu.FLEET_REQUEST 
					|| optionData == Menu.REMOTE_COMM || optionData == Menu.REMOTE_SUSPEND_AUTONOMY) 
			{
				FleetInteractionDialogPluginImpl.inConversation = true;

				optionsDialogDelegate = new RuleBasedInteractionDialogPluginImpl();
				optionsDialogDelegate.setEmbeddedMode(true);
				optionsDialogDelegate.init(dialog);

				MemoryAPI mem = optionsDialogDelegate.getMemoryMap().get(MemKeys.LOCAL);
				mem.set("$nex_specialDialog", true, 0);
			}

			if (optionData == Menu.INIT)
			{
				initMenu();
			}
			if (optionData == Menu.DIRECTORY)
			{
				optionsDialogDelegate.fireAll("ExerelinFactionDirectory");
			}
			else if (optionData == Menu.FLEET_REQUEST)
			{
				optionsDialogDelegate.fireAll("Nex_FleetRequest");
			}
			else if (optionData == Menu.REMOTE_COMM)
			{
				optionsDialogDelegate.fireAll("Nex_RemoteComm");
			}
			else if (optionData == Menu.REMOTE_SUSPEND_AUTONOMY)
			{
				optionsDialogDelegate.fireAll("Nex_RemoteSuspendAutonomy");
			}
			else if (optionData == Menu.INTEL_SCREEN)
			{
				dialog.getVisualPanel().showCore(CoreUITabId.INTEL, null, this);
			}
			else if (optionData == Menu.EXIT)
			{
				FleetInteractionDialogPluginImpl.inConversation = false;
				dialog.dismiss();
			}
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

		@Override
		public void coreUIDismissed() {

		}
	}
}
