package exerelin.campaign.ui;

import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import exerelin.world.factionsetup.FactionSetupHandler;
import exerelin.world.factionsetup.FactionSetupItemPlugin;
import java.awt.Color;
import java.util.List;

/**
 * When picking the own faction start, present some options to customize player planet
 */
public class OwnFactionSetupScript extends DelayedDialogScreenScript
{
	public static final int MAX_POINTS = 100;
	
	@Override
	protected void showDialog() {
		Global.getSector().getCampaignUI().showInteractionDialog(new FactionSetupDialog(), null);
	}

	protected static class FactionSetupDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		protected InteractionDialogAPI dialog;
		protected TextPanelAPI text;
		protected OptionPanelAPI options;
		//protected boolean picked = false;
		protected SectorEntityToken playerHome = getPlayerHome();

		protected enum Menu
		{
			OPTION_OPEN_PICKER,
			OPTION_VIEW_PLANET,
			//DONE
		}

		protected void populateOptions()
		{
			options.clearOptions();
			
			options.addOption(getString("dialogOpenPicker"), Menu.OPTION_OPEN_PICKER);
			//if (picked) options.setEnabled(Menu.OPTION_OPEN_PICKER, false);
			options.addOption(getString("dialogViewPlanet"), Menu.OPTION_VIEW_PLANET);
			//options.addOption("Open picker", Menu.DONE);
		}
		
		protected SectorEntityToken getPlayerHome() {
			SectorEntityToken playerHome = null;
			List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
			if (!markets.isEmpty()) playerHome = markets.get(0).getPrimaryEntity();
			
			return playerHome;
		}
		
		protected void selectItems() 
		{
			CargoAPI copy = Global.getFactory().createCargo(false);
			//copy.addAll(cargo);
			for (FactionSetupHandler.FactionSetupItemDef def : FactionSetupHandler.DEFS) {
				SpecialItemData special = new SpecialItemData("nex_factionSetupItem", def.id);
				copy.addSpecial(special, def.count);
			}
			copy.sort();
			
			final float sideWidth = 210f;
			final float screenWidth = Global.getSettings().getScreenWidth() * 3/4;
			final float screenHeight = Global.getSettings().getScreenHeight() * 4/5;
			dialog.showCargoPickerDialog(getString("pickerHeader"), 
					Misc.ucFirst(StringHelper.getString("confirm")), 
					Misc.ucFirst(StringHelper.getString("cancel")),
							false, sideWidth, screenWidth, screenHeight,  
							copy, new CargoPickerListener() {
				public void pickedCargo(CargoAPI cargo) {
					FactionSetupHandler.clearSelectedItems();
					cargo.sort();
					for (CargoStackAPI stack : cargo.getStacksCopy()) {
						SpecialItemData data = stack.getSpecialDataIfSpecial();
						FactionSetupHandler.addSelectedItem(data);
					}
					FactionSetupHandler.applyItems();
					//picked = true;
					dialog.dismiss();
				}
				public void cancelledCargoSelection() {
					
				}
				public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {

					int cost = getCargoCost(combined);
					int max = MAX_POINTS;

					float pad = 3f;
					float small = 5f;
					float opad = 10f;
					
					String str = getString("pickerFancyText");
					panel.setParaFontVictor14();
					panel.addPara(str, opad);
					panel.setParaFontDefault();
					panel.addImage(Global.getSector().getPlayerFaction().getLogo(), 205, 3);

					str = getString("pickerCost");
					Color color = cost > max ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
					panel.addPara(str, 	opad * 1f, color, "" + cost, "" + max);

					str = getString("pickerTip");
					panel.addPara(str, Misc.getGrayColor(), opad);
				}
				
				public int getCargoCost(CargoAPI cargo) {
					int cost = 0;
					for (CargoStackAPI stack : cargo.getStacksCopy()) {
						FactionSetupItemPlugin plugin = (FactionSetupItemPlugin)stack.getPlugin();
						cost += plugin.getItem().getCost() * stack.getSize();
					}
					return cost;
				}
			});
		}
		
		protected String getString(String id)
		{
			return StringHelper.getString("nex_factionSetup", id);
		}
		
		@Override
		public void init(InteractionDialogAPI dialog)
		{
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();
			
			String str = String.format(getString("dialogIntro1"), 
					Global.getSector().getPlayerPerson().getNameString(),
					Global.getSector().getPlayerFaction().getDisplayNameWithArticle());
			text.addParagraph(str, Global.getSector().getPlayerFaction().getBaseUIColor());
			
			str = getString("dialogIntro2");			
			text.addPara(str, ((PlanetAPI)playerHome).getSpec().getIconColor(), playerHome.getName());
			
			populateOptions();
			dialog.setPromptText(Misc.ucFirst(StringHelper.getString("options")));
		}

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			if (optionText != null) {
					text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
			}

			if (optionData == Menu.OPTION_OPEN_PICKER)
			{
				selectItems();
			}
			else if (optionData == Menu.OPTION_VIEW_PLANET)
			{
				dialog.getVisualPanel().showCore(CoreUITabId.CARGO, playerHome, this);
			}
			
			/*
			else if (optionData == Menu.DONE)
			{
				dialog.dismiss();
				return;
			}
			*/
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
