package exerelin.campaign;

import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
/**
 * When adding Nexerelin into an existing save, run this to reconfigure some options
 */
public class ReinitScreenScript implements EveryFrameScript
{
	private static final float DAYS_TO_WAIT = 0.1f;
	private boolean isDone = false;
	private float timer = 0;

	public ReinitScreenScript()
	{

	}

	@Override
	public boolean isDone()
	{
		return isDone;
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
		CampaignUIAPI ui = Global.getSector().getCampaignUI();
		if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog() || Global.getCurrentState() == GameState.TITLE)
		{
			return;
		}
		
		timer += Global.getSector().getClock().convertToDays(amount);
		if (timer < DAYS_TO_WAIT) return;
		
		if (!isDone)
		{
			ui.showInteractionDialog(new ReinitDialog(), Global.getSector().getPlayerFleet());
			isDone = true;
		}
	}

	private static class ReinitDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		private InteractionDialogAPI dialog;
		private TextPanelAPI text;
		private OptionPanelAPI options;
		private boolean allowRespawn = SectorManager.getManager().respawnFactions;
		private boolean allowRespawnNonOriginal = !SectorManager.getManager().onlyRespawnStartingFactions;
		private boolean randomizeRelationships = DiplomacyManager.isRandomFactionRelationships();
		private boolean randomizeRelationshipsPirate = DiplomacyManager.getManager().randomFactionRelationshipsPirate;
		private boolean hardMode = SectorManager.getHardMode();

		private enum Menu
		{
			OPTION_RESPAWN,
			OPTION_RESPAWN_NON_ORIGINAL,
			OPTION_RANDOM_RELATIONSHIPS,
			OPTION_RANDOM_RELATIONSHIPS_PIRATE,
			OPTION_HARD_MODE,
			DONE
		}

		private void populateOptions()
		{
			options.clearOptions();
			
			options.addOption(Misc.ucFirst(getString("allowRespawn")) + ": " 
					+ StringHelper.getString(String.valueOf(allowRespawn)), Menu.OPTION_RESPAWN);
			if (allowRespawn)
				options.addOption(Misc.ucFirst(getString("allowRespawnNonOriginal")) + ": " 
						+ StringHelper.getString(String.valueOf(allowRespawnNonOriginal)), Menu.OPTION_RESPAWN_NON_ORIGINAL);
			
			options.addOption(Misc.ucFirst(getString("randomizeRelationships")) + ": " 
					+ StringHelper.getString(String.valueOf(randomizeRelationships)), Menu.OPTION_RANDOM_RELATIONSHIPS);
			if (randomizeRelationships)
				options.addOption(Misc.ucFirst(getString("randomizeRelationshipsPirate")) + ": " 
					+ StringHelper.getString(String.valueOf(randomizeRelationshipsPirate)), Menu.OPTION_RANDOM_RELATIONSHIPS_PIRATE);
			options.addOption(Misc.ucFirst(getString("hardMode")) + ": " 
					+ StringHelper.getString(String.valueOf(hardMode)), Menu.OPTION_HARD_MODE);
			options.addOption(Misc.ucFirst(StringHelper.getString("done")), Menu.DONE);
		}
		
		protected String getString(String id)
		{
			return StringHelper.getString("exerelin_reinitScreen", id);
		}
		
		@Override
		public void init(InteractionDialogAPI dialog)
		{
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();
			
			text.addParagraph(getString("introText"));

			//dialog.setTextWidth(Display.getWidth() * .9f);
			
			populateOptions();
			dialog.setPromptText(Misc.ucFirst(StringHelper.getString("options")));
		}

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			if (optionText != null) {
					text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
			}

			// Option was a menu? Go to that menu
			if (optionData == Menu.OPTION_RESPAWN)
			{
				allowRespawn = !allowRespawn;
			}
			else if (optionData == Menu.OPTION_RESPAWN_NON_ORIGINAL)
			{
				allowRespawnNonOriginal = !allowRespawnNonOriginal;
			}
			else if (optionData == Menu.OPTION_RANDOM_RELATIONSHIPS)
			{
				randomizeRelationships = !randomizeRelationships;
			}
			else if (optionData == Menu.OPTION_RANDOM_RELATIONSHIPS_PIRATE)
			{
				randomizeRelationshipsPirate = !randomizeRelationshipsPirate;
			}
			else if (optionData == Menu.OPTION_HARD_MODE)
			{
				hardMode = !hardMode;
			}
			else if (optionData == Menu.DONE)
			{
				SectorManager.setAllowRespawnFactions(allowRespawn, allowRespawnNonOriginal);
				SectorManager.setHardMode(hardMode);
				if (randomizeRelationships)
				{
					DiplomacyManager.setRandomFactionRelationships(randomizeRelationships, randomizeRelationshipsPirate);
					DiplomacyManager.initFactionRelationships(true);
				}
				dialog.dismiss();
				return;
			}
			populateOptions();
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
