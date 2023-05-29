package exerelin.campaign.intel.groundbattle.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.plugins.AbilityPlugin;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

public class AbilityDialogPlugin implements InteractionDialogPlugin {

	public static enum OptionId {
		ACTIVATE,
		ACTIVATE_CONFIRM,
		CANCEL
	}
	
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI textPanel;
	protected OptionPanelAPI options;
	protected VisualPanelAPI visual;
	
	protected AbilityPlugin ability;
	protected Industry target;
	protected IntelUIAPI ui;
	
	protected boolean didAnything;
	
	public AbilityDialogPlugin(AbilityPlugin ability, IntelUIAPI ui) 
	{
		this.ability = ability;
		this.ui = ui;
	}

	@Override
	public void init(InteractionDialogAPI dialog) {
		this.dialog = dialog;
		textPanel = dialog.getTextPanel();
		options = dialog.getOptionPanel();
		visual = dialog.getVisualPanel();
		
		visual.setVisualFade(0.25f, 0.25f);
		//visual.showImagePortion("illustrations", "quartermaster", 640, 400, 0, 0, 480, 300);
		if (ability.getDef().illustration != null) {
			visual.showImagePortion("illustrations", ability.getDef().illustration, 640, 400, 0, 0, 480, 300);
		} else {
			visual.showPlanetInfo(ability.getIntel().getMarket().getPrimaryEntity());
		}
		try {
			ability.dialogAddVisualPanel(dialog);
		} catch (Throwable t) {
			Global.getLogger(this.getClass()).error("Failed to add visual panel for ability " + ability.getId(), t);
			textPanel.setFontSmallInsignia();
			textPanel.addPara("Error adding ability visual panel, mod may need update for Nexerelin v0.11.0", Misc.getNegativeHighlightColor());
			textPanel.setFontInsignia();
		}
		printInit();
	}
	
	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		// no combat here, so this won't get called
	}
	
	public void printInit() {
		populateOptions();
		ability.dialogAddIntro(dialog);
	}
	
	public void populateOptions() {
		ability.addDialogOptions(dialog);
		addLeaveOption(false);
	}
	
	public void populateConfirmOptions() {
		ability.addDialogOptions(dialog);
	}
	
	public void addLeaveOption(boolean clear) {
		if (clear) options.clearOptions();
		String cancelText = StringHelper.getString(didAnything ? "leave": "cancel", true);
		options.addOption(cancelText, OptionId.CANCEL, null);
		options.setShortcut(OptionId.CANCEL, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	public void activate() {
		ability.playUISound();
		options.clearOptions();
		ability.activate(dialog, Global.getSector().getPlayerPerson());
		if (ability.shouldCloseDialogOnActivate()) {
			leave(true);
		} else {
			didAnything = true;
			addLeaveOption(false);
		}
	}
	
	public void activateOptionPicked() {
		if (ability.targetsIndustry()) {
			/*
			List<Industry> targets = GBUtils.convertIFBListToIndustryList(ability.getTargetIndustries());
			dialog.showIndustryPicker(getString("dialogIndustryPickerHeader"), 
						StringHelper.getString("select", true), ability.getIntel().getMarket(), 
						targets, new IndustryPickerListener() {
					public void pickedIndustry(Industry industry) {
						target = industry;
						ability.setTarget(industry);
						activate();
					}
					public void cancelledIndustryPicking() {
						options.clearOptions();
						populateOptions();
					}
				});

			 */
			List<IndustryForBattle> targets = ability.getTargetIndustries();
			GBIndustryPickerDialogDelegate delegate = new GBIndustryPickerDialogDelegate(ability.getIntel(), targets ) {
				@Override
				protected String getHeaderString() {
					return GroundBattleIntel.getString("actionSelectTargetHeader");
				}

				@Override
				public void customDialogConfirm() {
					if (selectedIndustry == null) {
						customDialogCancel();
						return;
					}
					target = selectedIndustry.getIndustry();
					ability.setTarget(selectedIndustry);
					activate();
				}

				@Override
				public void customDialogCancel() {
					options.clearOptions();
					populateOptions();
				}
			};
			int[] dimensions = delegate.getWantedDimensions();
			dialog.showCustomDialog(dimensions[0] + 16, dimensions[1] + dimensions[4], delegate);
		} else {
			activate();
		}
	}
		
	@Override
	public void optionSelected(String text, Object optionData) {
		if (optionData == null) return;
		
		OptionId option = (OptionId) optionData;
		
		if (text != null) {
			//textPanel.addParagraph(text, Global.getSettings().getColor("buttonText"));
			dialog.addOptionSelectedText(option);
		}
		
		boolean consume = ability.processDialogOption(dialog, optionData);
		if (consume) return;
		
		switch (option) {
			case ACTIVATE_CONFIRM:
				activate();
				break;
			case ACTIVATE:
				activateOptionPicked();
				break;
			case CANCEL:
				leave(didAnything);
				break;
		}
	}
	
	protected void leave(boolean didAnything) {
		ability.dialogOnDismiss(dialog);
		dialog.dismiss();
		if (didAnything) ui.updateUIForItem(ability.getIntel());	
	}
	
	@Override
	public void optionMousedOver(String optionText, Object optionData) {
		
	}
	
	@Override
	public void advance(float amount) {
		
	}
	
	@Override
	public Object getContext() {
		return null;
	}
}



