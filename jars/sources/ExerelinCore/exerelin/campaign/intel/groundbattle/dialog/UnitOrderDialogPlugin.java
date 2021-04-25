package exerelin.campaign.intel.groundbattle.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.IndustryPickerListener;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;

public class UnitOrderDialogPlugin implements InteractionDialogPlugin {

	private static enum OptionId {
		INIT,
		LEAVE,
		
		// commands for undeployed units
		DEPLOY,
		RESIZE,
		DISBAND,
		
		// commands for deployed units
		MOVE,
		WITHDRAW,
		DISRUPT
	}
	
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI textPanel;
	protected OptionPanelAPI options;
	protected VisualPanelAPI visual;
	
	protected GroundBattleIntel intel;
	protected GroundUnit unit;
	protected IntelUIAPI ui;
	
	protected boolean didAnything;
	
	public UnitOrderDialogPlugin(GroundBattleIntel intel, GroundUnit unit, IntelUIAPI ui) 
	{
		this.intel = intel;
		this.unit = unit;
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
		visual.showPlanetInfo(intel.getMarket().getPrimaryEntity());
	
		dialog.setOptionOnEscape(StringHelper.getString("cancel", true), OptionId.LEAVE);
		
		optionSelected(null, OptionId.INIT);
	}
	
	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		// no combat here, so this won't get called
	}
	
	public int getCurrentSupplies() {
		return (int)Global.getSector().getPlayerFleet().getCargo().getSupplies();
	}
		
	protected void addChoiceOptions() {
		options.clearOptions();
		
		String confirm = StringHelper.getString("confirm", true);
		String cancel = StringHelper.getString("cancel", true);
		
		boolean deployed = unit.getLocation() != null;
		if (deployed) {
			options.addOption(getString("actionMove", true), OptionId.MOVE, null);
			options.addOption(getString("actionWithdraw", true), OptionId.WITHDRAW, null);
			// TODO
			//options.addOption(getString("actionDisrupt", true), OptionId.DISRUPT, null);
			
			options.addOptionConfirmation(OptionId.WITHDRAW, 
					getString("actionWithdrawConfirm"), 
					confirm, cancel);
			options.addOptionConfirmation(OptionId.DISRUPT, 
					"This will disrupt the %s for %s days, and takes effect immediately. The unit will reorganize for one turn.", 
					confirm, cancel);
		} else {
			options.addOption(getString("actionDeploy", true), OptionId.DEPLOY, null);
			options.addOption(getString("actionResize", true), OptionId.RESIZE, null);
			options.addOption(getString("actionDisband", true), OptionId.DISBAND, null);
			
			int deployCost = unit.getDeployCost();
			int currSupplies = getCurrentSupplies();
			if (deployCost > currSupplies) {
				options.setEnabled(OptionId.DEPLOY, false);
				options.setTooltip(OptionId.DEPLOY, String.format(getString("actionDeployNotEnoughTooltip"),
						deployCost, currSupplies));
				options.setTooltipHighlights(OptionId.DEPLOY, deployCost + "", currSupplies + "");
			}
			
			options.addOptionConfirmation(OptionId.DEPLOY, 
					String.format(getString("actionDeployConfirm"),	deployCost), 
					confirm, cancel);
			options.addOptionConfirmation(OptionId.DISBAND, 
					String.format(getString("actionDisbandConfirm"),	deployCost), 
					confirm, cancel);
		}
		
		options.addOption("Cancel", OptionId.LEAVE, null);
		options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected List<Industry> getIndustries() {
		List<Industry> industries = new ArrayList<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			industries.add(ifb.getIndustry());
		}
		return industries;
	}
	
	protected void selectMoveOrDeployDestination(final boolean deploy) {
		List<Industry> industries = getIndustries();
		dialog.showIndustryPicker(getString("dialogIndustryPickerHeader"), 
				StringHelper.getString("select", true), intel.getMarket(),
				industries, new IndustryPickerListener() {
			@Override
			public void pickedIndustry(Industry industry) {
				if (deploy) 
					unit.setLocation(intel.getIndustryForBattleByIndustry(industry));
				else
					unit.setDestination(intel.getIndustryForBattleByIndustry(industry));
				leave(true);
			}
			@Override
			public void cancelledIndustryPicking() {
				addChoiceOptions();
			}
		});
	}
	
	public void showUnitPanel() {
		CustomPanelAPI panel = visual.showCustomPanel(GroundUnit.PANEL_WIDTH, GroundUnit.PANEL_HEIGHT, null);
		panel.addUIElement(unit.createUnitCard(panel));
	}
	
	public void printInit() {
		showUnitPanel();
		addChoiceOptions();
	}
	
	@Override
	public void optionSelected(String text, Object optionData) {
		if (optionData == null) return;
		
		OptionId option = (OptionId) optionData;
		
		if (text != null) {
			//textPanel.addParagraph(text, Global.getSettings().getColor("buttonText"));
			dialog.addOptionSelectedText(option);
		}
		
		switch (option) {
		case INIT:
			printInit();
			addChoiceOptions();
			break;
		case MOVE:
			selectMoveOrDeployDestination(false);
			break;
		case DISRUPT:
			// FIXME
			break;
		case WITHDRAW:
			unit.orderWithdrawal();
			leave(true);
			break;
		case DEPLOY:
			selectMoveOrDeployDestination(true);
			break;
		case DISBAND:
			unit.removeUnit(true);
			leave(true);
			break;
		case LEAVE:
			leave(didAnything);
			break;
		}
	}
	
	protected void leave(boolean didAnything) {
		dialog.dismiss();
		if (didAnything) ui.updateUIForItem(intel);	
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



