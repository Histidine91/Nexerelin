package exerelin.campaign.intel.groundbattle.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
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
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UnitOrderDialogPlugin implements InteractionDialogPlugin {

	private static enum OptionId {
		INIT,
		LEAVE,
		
		// commands for undeployed units
		DEPLOY,
		DEPLOY_CONFIRM,
		RESIZE,
		RESIZE_CONFIRM,
		DISBAND,
		
		// commands for deployed units
		MOVE,
		WITHDRAW,
		DISRUPT,
		
		CANCEL
	}
	
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI textPanel;
	protected OptionPanelAPI options;
	protected VisualPanelAPI visual;
	
	protected GroundBattleIntel intel;
	protected GroundUnit unit;
	protected IntelUIAPI ui;
	
	protected IndustryForBattle deployTarget;
	
	protected boolean didAnything;
	
	protected boolean quickMove;	// True when clicking on the "fast move button"
	
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
		
		showUnitPanel();
		if (quickMove) {
			selectMoveOrDeployDestination(unit.getLocation() == null);
		} else {
			optionSelected(null, OptionId.INIT);
		}
		
	}
	
	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		// no combat here, so this won't get called
	}
	
	public void setQuickMove(boolean fastMove) {
		this.quickMove = fastMove;
	}
	
	public int getCurrentSupplies() {
		return (int)Global.getSector().getPlayerFleet().getCargo().getSupplies();
	}
	
	public int getDisruptTime() {
		float strRatio = unit.getAttackStrength()/GroundUnit.getBaseStrengthForAverageUnit(intel.getUnitSize(), ForceType.MARINE);
		Industry ind = unit.getLocation().getIndustry();
		float days = ind.getSpec().getDisruptDanger().disruptionDays * strRatio;
		float already = ind.getDisruptedDays();
		days *= days / (days + already); 
		return Math.round(days);
	}
	
	protected void addChoiceOptions() {
		options.clearOptions();
		
		String confirm = StringHelper.getString("confirm", true);
		String cancel = StringHelper.getString("cancel", true);
		
		boolean deployed = unit.getLocation() != null;
		if (deployed) {
			options.addOption(getString("actionMove", true), OptionId.MOVE, null);
			options.addOption(getString("actionWithdraw", true), OptionId.WITHDRAW, null);
			
			if (unit.getLocation().heldByAttacker == unit.isAttacker())
				options.addOption(getString("actionDisrupt", true), OptionId.DISRUPT, null);
			
			if (unit.isReorganizing()) {
				options.setEnabled(OptionId.MOVE, false);
				options.setEnabled(OptionId.DISRUPT, false);
				options.setTooltip(OptionId.MOVE, getString("actionMoveReorganizingTooltip"));
			}
			// out of movement points
			else if (intel.getSide(unit.isAttacker()).getMovementPointsSpent().getModifiedValue() >
					intel.getSide(unit.isAttacker()).getMovementPointsPerTurn().getModifiedValue()) 
			{
				options.setEnabled(OptionId.MOVE, false);
				options.setTooltip(OptionId.MOVE, getString("actionMoveOutOfMovementPointsTooltip"));
			}
			
			if (!intel.isPlayerInRange()) {
				options.setEnabled(OptionId.WITHDRAW, false);
				options.setTooltip(OptionId.WITHDRAW, String.format(getString("actionDeployOutOfRange"),
						(int)GBConstants.MAX_SUPPORT_DIST));
			}			
			
			options.addOptionConfirmation(OptionId.WITHDRAW, 
					getString("actionWithdrawConfirm"), 
					confirm, cancel);
			int disruptTime = getDisruptTime();
			options.addOptionConfirmation(OptionId.DISRUPT, 
					String.format(getString("actionDisruptConfirm"), unit.getLocation().getName(), disruptTime), 
					confirm, cancel);
			
			if (unit.getDestination() != null) {
				options.addOption(getString("actionCancelMove", true), OptionId.CANCEL, null);
			}
			else if (unit.isWithdrawing()) {
				options.addOption(getString("actionCancelWithdraw", true), OptionId.CANCEL, null);
			}
			
		} else {
			options.addOption(getString("actionDeploy", true), OptionId.DEPLOY, null);
			options.addOption(getString("actionResize", true), OptionId.RESIZE, null);
			options.addOption(getString("actionDisband", true), OptionId.DISBAND, null);
			
			int deployCost = unit.getDeployCost();
			int currSupplies = getCurrentSupplies();
			if (!intel.isPlayerInRange()) {
				options.setEnabled(OptionId.DEPLOY, false);
				options.setTooltip(OptionId.DEPLOY, String.format(getString("actionDeployOutOfRange"),
						(int)GBConstants.MAX_SUPPORT_DIST));
			}
			else if (unit.getSize() == 0) {
				options.setEnabled(OptionId.DEPLOY, false);
			}
			else if (deployCost > currSupplies) {
				options.setEnabled(OptionId.DEPLOY, false);
				options.setTooltip(OptionId.DEPLOY, String.format(getString("actionDeployNotEnoughTooltip"),
						deployCost, currSupplies));
				options.setTooltipHighlights(OptionId.DEPLOY, deployCost + "", currSupplies + "");
			}
			else if (intel.getSide(unit.isAttacker()).getMovementPointsSpent().getModifiedValue() >
					intel.getSide(unit.isAttacker()).getMovementPointsPerTurn().getModifiedValue()) 
			{
				options.setEnabled(OptionId.DEPLOY, false);
				options.setTooltip(OptionId.DEPLOY, getString("actionMoveOutOfMovementPointsTooltip"));
			}
			
			//options.addOptionConfirmation(OptionId.DEPLOY, 
			//		String.format(getString("actionDeployConfirm"),	deployCost), 
			//		confirm, cancel);
			options.addOptionConfirmation(OptionId.DISBAND, 
					String.format(getString("actionDisbandConfirm"), deployCost), 
					confirm, cancel);
		}
		
		addLeaveOption(false);
	}
	
	protected void addLeaveOption(boolean clear) {
		if (clear) options.clearOptions();
		options.addOption(StringHelper.getString("leave", true), OptionId.LEAVE, null);
		options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	public static int getMaxCountForResize(GroundUnit unit, int curr, int absoluteMax) {
		ForceType type = unit.getType();
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int max = absoluteMax;
		
		if (unit.getType() == ForceType.HEAVY)
			max /= GroundUnit.HEAVY_COUNT_DIVISOR;
		
		max = Math.min(max, curr + (int)cargo.getCommodityQuantity(type.commodityId));
		if (unit.getType() == ForceType.HEAVY) {
			max = Math.min(max, (unit.getPersonnel() + cargo.getMarines())/GroundUnit.CREW_PER_MECH);
		}
		
		return max;
	}
	
	protected void showResizeScreen() {
		options.clearOptions();
		int min = 0;
		int curr = unit.getSize();
		int max = getMaxCountForResize(unit, curr, intel.getUnitSize().maxSize);
		
		options.addSelector(getString("selectorUnitCount", true), "unitSizeSelector", Color.GREEN, 
				256, 48, 0, max, ValueDisplayMode.VALUE, null);
		options.setSelectorValue("unitSizeSelector", curr);
		
		options.addOption(StringHelper.getString("confirm", true), OptionId.RESIZE_CONFIRM);
		options.addOption(StringHelper.getString("back", true), OptionId.INIT, null);
	}
	
	protected void confirmResize() {
		int wantedSize = (int)options.getSelectorValue("unitSizeSelector");
		unit.setSize(wantedSize, true);
		didAnything = true;
		showUnitPanel();
		optionSelected(null, OptionId.INIT);
	}
	
	protected void showDeploymentConfirmScreen(IndustryForBattle ifb) {
		deployTarget = ifb;
		options.clearOptions();
		String str = getString("actionDeployInfo");
		int deployCost = unit.getDeployCost();
		int currSupplies = getCurrentSupplies();
		dialog.getTextPanel().addPara(str);
		boolean canAfford = dialog.getTextPanel().addCostPanel(null,
					Commodities.SUPPLIES, deployCost, true);
		
		float attrition = intel.getSide(unit.isAttacker()).getDropAttrition().getModifiedValue()/100;
		if (attrition > 0) {
			str = getString("actionDeployInfoAttrition");
			dialog.getTextPanel().addPara(str, Misc.getNegativeHighlightColor(), StringHelper.toPercent(attrition));
		}
		
		options.addOption(StringHelper.getString("confirm", true), OptionId.DEPLOY_CONFIRM);
		options.addOption(StringHelper.getString("back", true), quickMove ? OptionId.LEAVE : OptionId.INIT, null);
	}
	
	protected void confirmDeploy() {
		unit.deploy(deployTarget, dialog);
		float attrition = intel.getSide(unit.isAttacker()).getDropAttrition().getModifiedValue();
		if (attrition > 0) {
			playSound("attrition");
			showUnitPanel();
			didAnything = true;
			addLeaveOption(true);
		} else {
			playSound("deploy");
			leave(true);
		}
	}
	
	public List<Industry> getIndustries() {
		List<Industry> industries = new ArrayList<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb == unit.getLocation()) continue;
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
				if (deploy) {
					showDeploymentConfirmScreen(intel.getIndustryForBattleByIndustry(industry));
				}
				else {
					unit.setDestination(intel.getIndustryForBattleByIndustry(industry));
					playSound("move");
					leave(true);
				}
			}
			@Override
			public void cancelledIndustryPicking() {
				if (quickMove) leave(true);
				else addChoiceOptions();
			}
		});
	}
	
	protected void disrupt() {
		float days = getDisruptTime();
		days *= StarSystemGenerator.getNormalRandom(new Random(), 1f, 1.25f);
		float already = unit.getLocation().getIndustry().getDisruptedDays();
		unit.getLocation().getIndustry().setDisrupted(already + days);
		unit.reorganize(1);
		intel.reapply();
		playSound("disrupt");
	}
	
	public void showUnitPanel() {
		CustomPanelAPI panel = visual.showCustomPanel(GroundUnit.PANEL_WIDTH, GroundUnit.PANEL_HEIGHT, null);
		panel.addComponent(unit.createUnitCard(panel, true));
	}
	
	public void printInit() {
		deployTarget = null;
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
			disrupt();
			leave(true);
			break;
		case WITHDRAW:
			unit.orderWithdrawal();
			playSound("withdraw");
			leave(true);
			break;
		case DEPLOY:
			selectMoveOrDeployDestination(true);
			break;
		case DEPLOY_CONFIRM:
			confirmDeploy();
			break;
		case RESIZE:
			showResizeScreen();
			break;
		case RESIZE_CONFIRM:
			confirmResize();
			break;
		case DISBAND:
			unit.removeUnit(true);
			leave(true);
			break;
		case CANCEL:
			unit.cancelMove();
			playSound("cancelMove");
			showUnitPanel();
			addChoiceOptions();
			didAnything = true;
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
	
	protected void playSound(String event) {
		String id = getSound(event);
		if (id != null)
			Global.getSoundPlayer().playUISound(id, 1, 1);
	}
	
	protected String getSound(String event) {
		switch (event) {
			case "attrition":
				return "nex_sfx_gb_attrition";
			case "move":
			case "withdraw":
			case "cancelMove":
				return "ui_intel_something_posted";
			case "deploy":
				return "ui_raid_prepared";
			case "disrupt":
				return "nex_sfx_deciv_bomb";
			default:
				return null;
		}
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



