package exerelin.campaign.ui;

import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinSetupData.StartRelationsMode;
import exerelin.campaign.SectorManager;
import exerelin.utilities.StringHelper;
import java.util.Locale;

/**
 * When adding Nexerelin into an existing save, run this to reconfigure some options
 */
public class ReinitScreenScript extends DelayedDialogScreenScript
{
	@Override
	protected void showDialog() {
		Global.getSector().getCampaignUI().showInteractionDialog(new ReinitDialog(), null);
	}

	protected static class ReinitDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		protected InteractionDialogAPI dialog;
		protected TextPanelAPI text;
		protected OptionPanelAPI options;
		protected boolean allowRespawn = SectorManager.getManager().isRespawnFactions();
		protected boolean allowRespawnNonOriginal = !SectorManager.getManager().isOnlyRespawnStartingFactions();
		protected StartRelationsMode relationsMode = StartRelationsMode.DEFAULT;
		protected boolean relationsApplyPirate;
		protected boolean hardMode = SectorManager.getManager().isHardMode();

		protected enum Menu
		{
			OPTION_RESPAWN,
			OPTION_RESPAWN_NON_ORIGINAL,
			OPTION_RESET_RELATIONSHIPS,
			OPTION_RESET_RELATIONSHIPS_PIRATE,
			OPTION_HARD_MODE,
			DONE
		}

		protected void populateOptions()
		{
			options.clearOptions();
			
			options.addOption(Misc.ucFirst(getString("allowRespawn")) + ": " 
					+ StringHelper.getString(String.valueOf(allowRespawn)), Menu.OPTION_RESPAWN);
			if (allowRespawn)
				options.addOption(Misc.ucFirst(getString("allowRespawnNonOriginal")) + ": " 
						+ StringHelper.getString(String.valueOf(allowRespawnNonOriginal)), Menu.OPTION_RESPAWN_NON_ORIGINAL);
			
			options.addOption(Misc.ucFirst(getString("startRelationsMode")) + ": " 
					+ getRandomizeString(), Menu.OPTION_RESET_RELATIONSHIPS);
			if (!relationsMode.isDefault())
				options.addOption(Misc.ucFirst(getString("startRelationsModePirate")) + ": " 
					+ StringHelper.getString(String.valueOf(relationsApplyPirate)), Menu.OPTION_RESET_RELATIONSHIPS_PIRATE);
			options.addOption(Misc.ucFirst(getString("hardMode")) + ": " 
					+ StringHelper.getString(String.valueOf(hardMode)), Menu.OPTION_HARD_MODE);
			options.addOption(Misc.ucFirst(StringHelper.getString("done")), Menu.DONE);
		}
		
		protected String getString(String id)
		{
			return StringHelper.getString("exerelin_reinitScreen", id);
		}
		
		protected String getRandomizeString() {
			return getString("startRelationsMode_" + relationsMode.toString().toLowerCase(Locale.ENGLISH));
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
			else if (optionData == Menu.OPTION_RESET_RELATIONSHIPS)
			{
				int index = relationsMode.ordinal() + 1;
				if (index >= StartRelationsMode.values().length)
					index = 0;
				relationsMode = StartRelationsMode.values()[index];
			}
			else if (optionData == Menu.OPTION_RESET_RELATIONSHIPS_PIRATE)
			{
				relationsApplyPirate = !relationsApplyPirate;
			}
			else if (optionData == Menu.OPTION_HARD_MODE)
			{
				hardMode = !hardMode;
			}
			else if (optionData == Menu.DONE)
			{
				SectorManager.setAllowRespawnFactions(allowRespawn, allowRespawnNonOriginal);
				SectorManager.getManager().setHardMode(hardMode);
				if (!relationsMode.isDefault())
				{
					DiplomacyManager.getManager().setStartRelationsMode(relationsMode);
					DiplomacyManager.getManager().setApplyStartRelationsModeToPirates(relationsApplyPirate);
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
