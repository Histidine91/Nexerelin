package exerelin.campaign.intel.groundbattle.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.impl.campaign.rulecmd.newgame.Nex_NGCProcessSectorGenerationSliders;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.*;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.CrewReplacerUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;

public class UnitOrderDialogPlugin implements InteractionDialogPlugin {

	private static enum OptionId {
		INIT,
		INIT_RESHOW_PANEL,
		LEAVE,
		
		// shared commands
		MERGE_DIALOG,
		SPLIT,
		TRANSFER_CONFIRM,
		
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
	
	protected static final String TRANSFER_SELECTOR_ID = "transferSelector";
	
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI textPanel;
	protected OptionPanelAPI options;
	protected VisualPanelAPI visual;
	
	protected GroundBattleIntel intel;
	protected GroundUnit unit;
	protected IntelUIAPI ui;
	
	protected IndustryForBattle deployTarget;
	protected GroundUnit unitForTransfer;
	
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
			selectMoveOrDeployDestination(!unit.isDeployed());
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
		float strRatio = unit.getAttackStrength()/GroundUnit.getBaseStrengthForAverageUnit(intel.getUnitSize(), GroundUnitDef.MARINE);
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

			boolean canDisrupt = unit.getLocation().heldByAttacker == unit.isAttacker();
			if (canDisrupt) {
				options.addOption(getString("actionDisrupt", true), OptionId.DISRUPT, null);
				if (!unit.isAttacker()) {
					boolean isOwnerUnit = unit.getFaction() == intel.getSide(false).getFaction();
					if (!isOwnerUnit) {
						options.setEnabled(OptionId.DISRUPT, false);
						options.setTooltip(OptionId.DISRUPT, getString("actionDisruptTooltipNotOwner"));
					}
				}
			}
			
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
		
		options.addOption(getString("actionMergeOrTransfer", true), OptionId.MERGE_DIALOG, null);
		options.addOption(getString("actionSplit", true), OptionId.SPLIT, null);
		
		if (unit.getSize() < intel.getUnitSize().getMinSizeForType(unit.getUnitDefId()) * 2)
		{
			options.setEnabled(OptionId.SPLIT, false);
			options.setTooltip(OptionId.SPLIT, getString("actionSplitTooSmallTooltip"));
		}
		else if (intel.getPlayerData().getUnits().size() >= GroundBattleIntel.MAX_PLAYER_UNITS) 
		{
			options.setEnabled(OptionId.SPLIT, false);
			options.setTooltip(OptionId.SPLIT, getString("actionSplitMaxUnitsTooltip"));
		}
		
		boolean canMerge = false;
		for (GroundUnit other : intel.getPlayerData().getUnits()) {
			if (other != unit && other.getLocation() == unit.getLocation()) {
				canMerge = true;
				break;
			}
		}
		if (!canMerge) {
			options.setEnabled(OptionId.MERGE_DIALOG, false);
			options.setTooltip(OptionId.MERGE_DIALOG, getString("actionMergeNoUnitsTooltip"));
		}
		
		addLeaveOption(false);
	}
	
	protected void addLeaveOption(boolean clear) {
		if (clear) options.clearOptions();
		options.addOption(StringHelper.getString("leave", true), OptionId.LEAVE, null);
		options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	public static int getMaxCountForResize(GroundUnit unit, int curr, int absoluteMax) {
		GroundUnitDef def = unit.getUnitDef();
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		int max = absoluteMax;

		// check available commodities
		{
			if (unit.getUnitDef().equipment != null) {
				max = Math.min(max, curr + (int)CrewReplacerUtils.getAvailableCommodity(fleet, def.equipment.commodityId, def.equipment.crewReplacerJobId));
			}
			max = Math.min(max, curr + (int)CrewReplacerUtils.getAvailableCommodity(fleet, def.personnel.commodityId, def.personnel.crewReplacerJobId));
		}
		
		return max;
	}
	
	protected void showResizeScreen() {
		options.clearOptions();
		int min = intel.getUnitSize().getMinSizeForType(unit.getUnitDefId());
		int curr = unit.getSize();
		int max = getMaxCountForResize(unit, curr, intel.getUnitSize().getMaxSizeForType(unit.getUnitDefId()));
		if (min > curr) min = curr;
		
		options.addSelector(getString("selectorUnitCount", true), "unitSizeSelector", Color.GREEN, 
				256, 48, min, max, ValueDisplayMode.VALUE, null);
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
		
		if (quickMove) {
			dialog.getPlugin().optionSelected(null, OptionId.DEPLOY_CONFIRM);
			return;
		}
		
		options.clearOptions();
		String str = getString("actionDeployInfo");
		int deployCost = unit.getDeployCost();
		//int currSupplies = getCurrentSupplies();
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
		if (!canAfford) options.setEnabled(OptionId.DEPLOY_CONFIRM, false);
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

	public List<IndustryForBattle> getIFBs() {
		List<IndustryForBattle> industries = new ArrayList<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb == unit.getLocation()) continue;
			industries.add(ifb);
		}
		return industries;
	}
	
	protected void selectMoveOrDeployDestination(final boolean deploy) {
		/*
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
		*/
		List<IndustryForBattle> industries = getIFBs();
		GBIndustryPickerDialogDelegate delegate = new GBIndustryPickerDialogDelegate(intel, industries) {
			@Override
			protected String getHeaderString() {
				return getString("actionSelectDestinationHeader");
			}

			@Override
			public void customDialogConfirm() {
				if (selectedIndustry == null) {
					customDialogCancel();
					return;
				}

				if (deploy) {
					showDeploymentConfirmScreen(selectedIndustry);
				}
				else {
					unit.setDestination(selectedIndustry);
					playSound("move");
					leave(true);
				}
			}

			@Override
			public void customDialogCancel() {
				if (quickMove) leave(true);
				else addChoiceOptions();
			}
		};
		int[] dimensions = delegate.getWantedDimensions();
		dialog.showCustomDialog(dimensions[0] + 16, dimensions[1] + dimensions[4], delegate);
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
	
	protected void mergeUnit(GroundUnit other) {
		int ourSize = unit.getSize(), theirSize = other.getSize();
		float theirMorale = other.getMorale() * theirSize;
		float ourMorale = unit.getMorale() * unit.getSize();
		int totalSize = ourSize + theirSize;
		
		unit.setSize(ourSize + theirSize, false);
		unit.setMorale((ourMorale + theirMorale)/totalSize);
		other.removeUnit(false);
		didAnything = true;
	}
	
	protected void transfer(GroundUnit other, float sliderValue) {
		int ourSizeNew = (int)Math.round(sliderValue);
		
		int ourSize = unit.getSize(), theirSize = other.getSize();
		
		int delta = ourSizeNew - ourSize;
		
		if (delta == 0) return;
		
		GroundUnit recipient = delta > 0 ? unit : other;
		GroundUnit donor = delta < 0 ? unit : other;
		
		float donorMorale = Math.abs(delta) * donor.getMorale();
		float recipientMorale = recipient.getSize() * recipient.getMorale();
		
		other.setSize(theirSize - delta, false);
		unit.setSize(ourSize + delta, false);
		
		//dialog.getTextPanel().addPara(String.format("Recipient is %s, otherUnit is %s", recipient.getName(), other.getName
		/*
		dialog.getTextPanel().addPara(String.format("Recipient morale: %s", recipientMorale));
		dialog.getTextPanel().addPara(String.format("Donor morale: %s", donorMorale));
		dialog.getTextPanel().addPara(String.format("New recipient size: %s, division result: %s", 
				recipient.getSize(), (donorMorale + recipientMorale)/recipient.getSize()));
		*/
		recipient.setMorale((donorMorale + recipientMorale)/recipient.getSize());
		didAnything = true;
		unitForTransfer = null;
	}
	
	protected int[] getMinAndMaxForTransfer(GroundUnit other) {
		int min = intel.getUnitSize().getMinSizeForType(unit.getUnitDefId());
		int max = intel.getUnitSize().getMaxSizeForType(unit.getUnitDefId());
		
		int sizeSum = unit.getSize() + other.getSize();
		// cannot take so many troops that the other unit drops below min size
		max = Math.min(sizeSum - min, max);
		// cannot give so many that other unit exceeds max size
		min = Math.max(sizeSum - max, min);
		
		return new int[] {min, max};
	}
	
	protected void showTransferDialog(GroundUnit other) {
		OptionPanelAPI opts = dialog.getOptionPanel();
		try {
			unitForTransfer = other;
			int ours = unit.getSize();
			int theirs = other.getSize();
			int total = ours + theirs;

			// TODO: calc min/max items
			int[] minAndMax = getMinAndMaxForTransfer(other);
			int min = minAndMax[0];
			int max = minAndMax[1];

			
			opts.clearOptions();
			String selectorText = String.format(getString("actionTransferSlider"), unit.getName());

			opts.addSelector(selectorText, TRANSFER_SELECTOR_ID, 
						Color.GREEN, Nex_NGCProcessSectorGenerationSliders.BAR_WIDTH, 
						48, min, max, ValueDisplayMode.VALUE, 
						getString("actionTransferSliderTooltip"));
			opts.setSelectorValue(TRANSFER_SELECTOR_ID, ours);

			opts.addOption(getString("actionTransfer", true), OptionId.TRANSFER_CONFIRM);
		} catch (Exception ex) {
			dialog.getTextPanel().addPara("Error displaying transfer slider, see starsector.log: " + ex);
			Global.getLogger(this.getClass()).error("Error displaying unit transfer slider", ex);
		}
		opts.addOption(StringHelper.getString("cancel", true), OptionId.MERGE_DIALOG);
	}
	
	/**
	 * Generates a custom panel row showing another unit and its merge & transfer buttons.
	 * @param other
	 */
	protected void showUnitForMerge(final GroundUnit other) {
		float pad = 3;
		float buttonWidth = 96;
		boolean self = unit == other;
		
		int size = unit.getSize();
		
		TooltipMakerAPI panelTooltip = Nex_VisualCustomPanel.getTooltip();
		
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		CustomPanelGenResult panelGen = NexUtilsGUI.addPanelWithFixedWidthImage(panel, 
				null, panel.getPosition().getWidth(), 48, null, 200, 3 * 3, 
				other.getType().getCommoditySprite(), 48, pad, null, false, null);
		
		CustomPanelAPI info = panelGen.panel;
		//TooltipMakerAPI img = (TooltipMakerAPI)panelGen.elements.get(0);
		TooltipMakerAPI text = (TooltipMakerAPI)panelGen.elements.get(1);

		//text.setParaSmallInsignia();
		text.addPara(other.getName(), self ? Misc.getHighlightColor() : other.getFaction().getBaseUIColor(), pad);
		int curr = other.getSize();
		int max = intel.getUnitSize().getMaxSizeForType(unit.getUnitDefId());
		LabelAPI label = text.addPara(String.format("%s/%s", curr, max), 0);
		if (curr < intel.getUnitSize().getMinSizeForType(unit.getUnitDefId())) {
			label.setHighlight(curr + "");
			label.setHighlightColor(Misc.getNegativeHighlightColor());
		}
		String str = String.format("%.0f", other.getAttackStrength());
		String morale = StringHelper.toPercent(other.getMorale());
		label = text.addPara(String.format(getString("industryPanel_tooltipUnitInfo"), 
				str, morale), 0);
		label.setHighlight(str, morale);
		label.setHighlightColors(Misc.getHighlightColor(), GroundUnit.getMoraleColor(other.getMorale()));
		
		
		if (!self) {
			String buttonId = "transfer_" + other.id;
			TooltipMakerAPI buttonTransferHolder = info.createUIElement(buttonWidth, 48, false);
			ButtonAPI buttonTransfer = buttonTransferHolder.addButton(getString("actionTransfer", true), buttonId, buttonWidth, 40, 4);
			boolean allowTransfer = size + other.getSize() > intel.getUnitSize().getMinSizeForType(unit.getUnitDefId());
			
			InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(buttonTransfer, buttonId) 
			{
				@Override
				public void onToggle() {
					showTransferDialog(other);
				}
			};
			if (!allowTransfer) {
				buttonTransfer.setEnabled(false);
				buttonTransferHolder.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(
						getString("actionTransferTooSmallTooltip"), 240), TooltipMakerAPI.TooltipLocation.BELOW);
			} else {
				buttonTransferHolder.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(
						getString("actionTransferTooltip"), 360), TooltipMakerAPI.TooltipLocation.BELOW);
			}
			
			
			Nex_VisualCustomPanel.getPlugin().addButton(entry);
			info.addUIElement(buttonTransferHolder).rightOfTop(text, pad);

			boolean allowMerge = unit.getSize() + other.getSize() <= intel.getUnitSize().getMaxSizeForType(unit.getUnitDefId());

			buttonId = "merge_" + other.id;
			TooltipMakerAPI buttonMergeHolder = info.createUIElement(buttonWidth, 48, false);
			ButtonAPI buttonMerge = buttonMergeHolder.addButton(getString("actionMerge", true), buttonId, buttonWidth, 40, 4);
			entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(buttonMerge, buttonId) 
			{
				@Override
				public void onToggle() {
					mergeUnit(other);
					optionSelected(null, OptionId.INIT_RESHOW_PANEL);
				}
			};

			if (!allowMerge) {
				buttonMerge.setEnabled(false);
				buttonMergeHolder.addTooltipToPrevious(NexUtilsGUI.createSimpleTextTooltip(
						getString("actionMergeTooLargeTooltip"), 240), TooltipMakerAPI.TooltipLocation.BELOW);
			}
			Nex_VisualCustomPanel.getPlugin().addButton(entry);
			info.addUIElement(buttonMergeHolder).rightOfTop(buttonTransferHolder, pad);
		}
		
		panelTooltip.addCustom(info, pad);
	}
	
	protected void showMergeScreen() {
		Nex_VisualCustomPanel.createPanel(dialog, true);
		showUnitForMerge(unit);
		for (GroundUnit other : intel.getPlayerData().getUnits()) {
			if (other == unit) continue;
			if (other.getLocation() != unit.getLocation()) continue;
			if (other.getType() != unit.getType()) continue;
			showUnitForMerge(other);
		}
		Nex_VisualCustomPanel.addTooltipToPanel();
		options.clearOptions();
		options.addOption(StringHelper.getString("back", true), OptionId.INIT_RESHOW_PANEL, null);
	}
	
	protected void split() {
		GroundUnit otherUnit = intel.createPlayerUnit(unit.getUnitDefId());

		for (String commodityId : unit.getPersonnelMap().keySet()) {
			int count = unit.getPersonnelMap().get(commodityId);
			int halfCount = count/2;
			unit.getPersonnelMap().put(commodityId, halfCount);
			otherUnit.getPersonnelMap().put(commodityId, count - halfCount);
		}
		for (String commodityId : unit.getEquipmentMap().keySet()) {
			int count = unit.getEquipmentMap().get(commodityId);
			int halfCount = count/2;
			unit.getEquipmentMap().put(commodityId, halfCount);
			otherUnit.getEquipmentMap().put(commodityId, count - halfCount);
		}
		
		otherUnit.setMorale(unit.getMorale());
		otherUnit.setLocation(unit.getLocation());
		
		didAnything = true;
	}
	
	public void showUnitPanel() {
		CustomPanelAPI panel = visual.showCustomPanel(GroundUnit.PANEL_WIDTH, GroundUnit.PANEL_HEIGHT, null);
		panel.addComponent(unit.createUnitCard(panel, true)).inTL(0, 0);
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
		case INIT_RESHOW_PANEL:
			showUnitPanel();
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
		case SPLIT:
			split();
			showUnitPanel();
			addChoiceOptions();
			break;
		case MERGE_DIALOG:
			showMergeScreen();
			break;
		case TRANSFER_CONFIRM:
			float val = dialog.getOptionPanel().getSelectorValue(TRANSFER_SELECTOR_ID);
			transfer(unitForTransfer, val);
			showMergeScreen();
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



