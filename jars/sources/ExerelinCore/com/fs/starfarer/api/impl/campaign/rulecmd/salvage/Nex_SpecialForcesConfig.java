package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.rulecmd.*;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercFleetGenPlugin;
import exerelin.campaign.intel.specialforces.PlayerSpecialForcesIntel;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class Nex_SpecialForcesConfig extends BaseCommandPlugin {
	
	public static final String MEM_KEY_TRANSFER = "$nex_psf_transfer";
	public static final float SHIP_ICON_SIZE_MAIN = 64;
	public static final float PORTRAIT_SIZE_MAIN = 40;
	public static final float PORTRAIT_SIZE_PICKER = 48;
	public static final float BTN_HEIGHT = 16;
	public static final int MAX_SKILL_LEVEL = 3;
	public static final float MAX_REVIVE_DISTANCE = 500;
	
	public static final Map<String, Boolean> SKILL_AVAILABLE_OVERRIDES = new HashMap<>();
	public static final Map<String, Integer> SKILL_VALUES = new HashMap<>();
	public static final List<SkillSpecAPI> SORTED_SKILLS;
	
	static {
		//SKILL_AVAILABLE_OVERRIDES.put(Skills.AUTOMATED_SHIPS, true);
		SKILL_AVAILABLE_OVERRIDES.put(Skills.DERELICT_CONTINGENT, true);
		SKILL_AVAILABLE_OVERRIDES.put(Skills.OFFICER_TRAINING, false);
		SKILL_AVAILABLE_OVERRIDES.put(Skills.BEST_OF_THE_BEST, false);
		SKILL_AVAILABLE_OVERRIDES.put(Skills.CYBERNETIC_AUGMENTATION, false);
		
		SORTED_SKILLS = getSortedSkillList();
		
		for (SkillSpecAPI skill : SORTED_SKILLS) {
			int value = 1;
			if (skill.getReqPointsPer() > 0)
				value = 2;
			
			SKILL_VALUES.put(skill.getId(), value);
		}
	}
	
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		SectorEntityToken token = dialog.getInteractionTarget();
		CampaignFleetAPI fleet = token instanceof CampaignFleetAPI ? (CampaignFleetAPI)token : null;
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "canCreate":
				return canCreate(memoryMap.get(MemKeys.LOCAL));
			case "beginCreate":
				beginCreate(dialog, memoryMap);
				return true;
			case "mainMenu":
				showMain(dialog, memoryMap);
				return true;
			//case "shipMenu":
			//	addShipOpts(dialog, memoryMap);
			//	return true;
			case "showOfficerTransfer":
				showOfficersForTransfer(dialog, memoryMap);
				return true;
			case "showOfficerAssignment":
				showOfficersForAssignment(dialog);
				return true;
			case "takeShips":
				transferShips(fleet, player, dialog, memoryMap);
				return true;
			case "giveShips":
				transferShips(player, fleet, dialog, memoryMap);
				return true;
			case "canRevive":
				return canRevive(fleet, memoryMap.get(MemKeys.LOCAL));
			case "reviveShips":
				pickShipsForRevive(fleet, dialog, memoryMap);
				return true;
			case "reviveConfirm":
				confirmShipRevive(fleet, dialog, memoryMap);
				return true;
			case "transferOfficers":
				transferOfficers(dialog, memoryMap);
				return true;
			case "assignAndRefit":
				assignAndRefit(dialog, memoryMap);
				return true;
			case "commanderSkillsView":
				setCommanderSkills(dialog, memoryMap);
				return true;
			case "hasTooManyOfficers":
			{
				if (fleet == null) return false;
				int num = fleet.getFleetData().getOfficersCopy().size();
				int max = getMaxOfficers(fleet);
				memoryMap.get(MemKeys.LOCAL).set("$nex_numOfficers", num, 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_maxOfficers", max, 0);
				return num > max;
			}
			case "hasTooManyShips":
			{
				if (fleet == null) return false;
				int num = fleet.getFleetData().getNumMembers();
				int max = Global.getSettings().getMaxShipsInFleet();
				memoryMap.get(MemKeys.LOCAL).set("$nex_numShips", num, 0);
				memoryMap.get(MemKeys.LOCAL).set("$nex_maxShips", max, 0);
				return num > max;
			}

			case "commanderSkillsConfirm":
				applySkillChanges(dialog, memoryMap.get(MemKeys.LOCAL));
				return true;
			case "hasCommanderAndFlagship":
				SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
				//dialog.getTextPanel().addPara("Flagship is " + sf.getFlagship());
				return sf.getCommander() != null && sf.getFlagship() != null;
			case "swapCargo":
				transferCargo(player, fleet, dialog, memoryMap);
				return true;
			case "done":
				done(dialog, memoryMap);
				return true;
			case "cancel":
				cancelCreation(dialog, memoryMap);
				return true;
			case "disband":
				disband(dialog, memoryMap);
				return true;
		}
		
		return false;
	}
	
	protected boolean canCreate(MemoryAPI mem) {
		/*
		if (PlayerSpecialForcesIntel.getActiveIntelCount() > 0) {
			mem.set("$nex_psf_noCreateReason", SpecialForcesIntel.getString("dialogTooltipMaxPSF"), 0);
			return false;
		}
		*/
		return true;
	}
	
	protected boolean canRevive(CampaignFleetAPI fleet, MemoryAPI mem) {
		PlayerSpecialForcesIntel intel = (PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(fleet);
		if (intel.getDeadMembers().isEmpty()) {
			mem.set("$nex_psf_noReviveReason", SpecialForcesIntel.getString("dialogTooltipNoShipsToRevive"), 0);
			return false;
		}
		if (intel.getRoute().getActiveFleet() == null) {
			mem.set("$nex_psf_noReviveReason", SpecialForcesIntel.getString("dialogTooltipFleetNull"), 0);
			return false;
		}
		BattleAPI bat = intel.getRoute().getActiveFleet().getBattle();
		if (bat != null && !bat.isPlayerInvolved()) {
			mem.set("$nex_psf_noReviveReason", SpecialForcesIntel.getString("intelTooltipCommandInBattle"), 0);
			return false;
		}
		boolean haveMarket = false;
		for (MarketAPI market : Misc.getMarketsInLocation(fleet.getContainingLocation())) {
			if (market.getFaction().isAtBest(fleet.getFaction(), RepLevel.INHOSPITABLE))
				continue;
			if (MathUtils.getDistance(market.getPrimaryEntity(), fleet) > MAX_REVIVE_DISTANCE)
				continue;
				
			haveMarket = true;
			break;
		}
		if (!haveMarket) {
			mem.set("$nex_psf_noReviveReason", SpecialForcesIntel.getString("dialogTooltipNoMarketForRevive"), 0);
			return false;
		}
		
		return true;
	}
	
	protected void beginCreate(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		SectorEntityToken origTarget = dialog.getInteractionTarget();
		MarketAPI market = origTarget.getMarket();
		PlayerSpecialForcesIntel psf = new PlayerSpecialForcesIntel(market, Global.getSector().getPlayerFaction());
		dialog.setInteractionTarget(psf.createTempFleet());
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();		
		memoryMap.get(MemKeys.LOCAL).set("$nex_psf_create", true, 0);
		memoryMap.get(MemKeys.LOCAL).set("$nex_psf_origInteractionTarget", origTarget, 0);
		
		showMain(dialog, memoryMap);
	}
	
	protected void showMain(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		new ShowDefaultVisual().execute(null, dialog, new ArrayList<Misc.Token>(), memoryMap);
		populateMainOptions(dialog, memoryMap);
	}
		
	protected void populateMainOptions(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		FireAll.fire(null, dialog, memoryMap, "Nex_ConfigSFOptions");
	}
	
	protected void showOfficersForAssignment(final InteractionDialogAPI dialog) 
	{
		float pad = 3;
		float height = PORTRAIT_SIZE_PICKER;
				
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		FactionAPI player = Global.getSector().getPlayerFaction();
		final CampaignFleetAPI fleet = getFleet(dialog);	
		
		
		tooltip.setParaSmallOrbitron();
		tooltip.addPara(getString("dialog_headerOfficerAssign"), 0).setAlignment(Alignment.MID);
		tooltip.setParaFontDefault();
		
		// list officers
		for (OfficerDataAPI od : fleet.getFleetData().getOfficersCopy()) {
			final PersonAPI officer = od.getPerson();
			boolean isCommander = officer == fleet.getCommander();
			final FleetMemberAPI member = getShipCommandedBy(fleet, officer);
			TooltipCreator offTT = new NexUtilsGUI.ShowPeopleOfficerTooltip(officer);
			
			CustomPanelAPI row = panel.createCustomPanel(Nex_VisualCustomPanel.PANEL_WIDTH - 4, height, null);
			TooltipMakerAPI shipHolder = null;
			if (member != null) {
				shipHolder = NexUtilsGUI.createFleetMemberImageForPanel(row, member, height, height);
				row.addUIElement(shipHolder).inTL(0, 0);
			}
			TooltipMakerAPI portraitHolder = NexUtilsGUI.createPersonImageForPanel(row, officer, height, height);
			if (member != null) {
				row.addUIElement(portraitHolder).rightOfTop(shipHolder, 2);
			} else {
				row.addUIElement(portraitHolder).inTL(height + 2, 0);
			}
			portraitHolder.addTooltipToPrevious(offTT, TooltipMakerAPI.TooltipLocation.BELOW);
			
			TooltipMakerAPI text = row.createUIElement(120, height, false);
			
			Color color = officer == fleet.getCommander() ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			text.addPara(NexUtilsGUI.getAbbreviatedName(officer), color, 0);
			text.addTooltipToPrevious(offTT, TooltipMakerAPI.TooltipLocation.BELOW);
			text.addPara(getString("dialog_level", true), pad, Misc.getHighlightColor(), 
					officer.getStats().getLevel() + "");
			
			row.addUIElement(text).rightOfTop(portraitHolder, 2);
			
			// add buttons
			TooltipMakerAPI btnHolder = row.createUIElement(100, BTN_HEIGHT, false);
			ButtonAPI selButton = btnHolder.addButton(StringHelper.getString("nex_specialForces", "dialogButtonAssign"), 
					"assign_" + officer.getId(), 80, BTN_HEIGHT, 0);
			plugin.addButton(new CustomPanelPluginWithInput.ButtonEntry(
					selButton, "nex_assignOfficer_" + officer.getId()) 
			{
				@Override
				public void onToggle() {
					assignOfficerToShip(dialog, fleet, officer);
				}
			});

			ButtonAPI comButton = btnHolder.addAreaCheckbox(getString("dialog_commander", true), 
					"commander_" + officer.getId(), 
					player.getBaseUIColor(), player.getDarkUIColor(), player.getBrightUIColor(), 
					100, BTN_HEIGHT, pad);
			comButton.setChecked(isCommander);
			plugin.addButton(new CustomPanelPluginWithInput.RadioButtonEntry(
					comButton, "nex_selectCommander_" + officer.getId()) 
			{
				@Override
				public void onToggle() {
					SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
					setCommander(fleet, sf, officer);
					setFlagship(fleet, sf, member);
					showOfficersForAssignment(dialog);
				}
			});			
			
			row.addUIElement(btnHolder).rightOfTop(text, pad);
			
			tooltip.addCustom(row, pad);
		}
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	protected void assignOfficerToShip(final InteractionDialogAPI dialog, final CampaignFleetAPI fleet, final PersonAPI officer) 
	{
		List<FleetMemberAPI> ships = new ArrayList<>();
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (Misc.isUnremovable(member.getCaptain())) continue;
			if (Misc.isAutomated(member)) continue;
			ships.add(member);
		}
		dialog.showFleetMemberPickerDialog(getString("dialog_headerShipPick"), 
				StringHelper.getString("confirm", true),
				StringHelper.getString("cancel", true),
				5, 9, 96, // 3, 7, 58 or so
				true, false, ships, 
				new FleetMemberPickerListener() {
					public void pickedFleetMembers(List<FleetMemberAPI> members) {
						if (members.isEmpty()) return;
						FleetMemberAPI newShip = members.get(0);
						PersonAPI prevCap = newShip.getCaptain();
						FleetMemberAPI prevShip = getShipCommandedBy(fleet, officer);
						if (prevCap != null && !prevCap.isDefault() && prevShip != null) {
							prevShip.setCaptain(prevCap);
						} else if (prevShip != null) {
							prevShip.setCaptain(null);
						}
						newShip.setCaptain(officer);
						
						if (fleet.getCommander() == officer) {
							SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
							setFlagship(fleet, sf, newShip);
						}
						
						showOfficersForAssignment(dialog);
					}
					public void cancelledFleetMemberPicking() {
						//showMain(dialog, memoryMap);
					}
				});
	}
	
	protected void showOfficersForTransfer(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI other = getFleet(dialog);
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		float width = Nex_VisualCustomPanel.PANEL_WIDTH/2 - 4;
		float height = Nex_VisualCustomPanel.PANEL_HEIGHT - 24;
		
		CustomPanelAPI playerPanel = this.showOfficersForTransfer(player, panel, width, height, dialog, memoryMap);
		panel.addComponent(playerPanel).inTL(2, 2);
		CustomPanelAPI otherPanel = this.showOfficersForTransfer(other, panel, width, height, dialog, memoryMap);
		panel.addComponent(otherPanel).rightOfTop(playerPanel, 2);
		
		//Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	protected CustomPanelAPI showOfficersForTransfer(CampaignFleetAPI fleet, CustomPanelAPI outer, 
			float width, float height,
			final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		float pad = 3;
		
		CustomPanelAPI sub = outer.createCustomPanel(width, height, null);
		TooltipMakerAPI tooltip = sub.createUIElement(width - 4, height, true);
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		List<PersonAPI> officers = new ArrayList<>();
		for (OfficerDataAPI od : fleet.getFleetData().getOfficersCopy()) 
		{
			if (Misc.isMercenary(od.getPerson())) continue;
			if (Misc.isUnremovable(od.getPerson())) continue;
			officers.add(od.getPerson());
		}
		
		// list officers
		tooltip.setParaSmallOrbitron();
		tooltip.addPara(getString(fleet.isPlayerFleet() ? "dialog_playerFleet" : "dialog_otherFleet", true), 0);
		tooltip.setParaFontDefault();
		
		int curr = fleet.getFleetData().getOfficersCopy().size();
		int max = getMaxOfficers(fleet);
		boolean excess = curr > max;
		String str = String.format(getString("dialog_headerOfficers", true));
		tooltip.addPara(str + ": %s/%s", pad, excess ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor(),
				curr + "", max + "");
		
		for (final PersonAPI officer : officers) {
			TooltipCreator offTT = new NexUtilsGUI.ShowPeopleOfficerTooltip(officer);
			
			CustomPanelGenResult cpg = NexUtilsGUI.addPanelWithFixedWidthImage(sub, null, 
					Nex_VisualCustomPanel.PANEL_WIDTH, PORTRAIT_SIZE_PICKER, null, 120, pad, 
					officer.getPortraitSprite(), PORTRAIT_SIZE_PICKER, 
					pad, null, false, offTT);
			CustomPanelAPI row = cpg.panel;
			TooltipMakerAPI text = (TooltipMakerAPI)cpg.elements.get(1);
			
			Color color = officer == fleet.getCommander() ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			text.addPara(NexUtilsGUI.getAbbreviatedName(officer), color, 0);
			text.addPara(getString("dialog_level", true), pad, Misc.getHighlightColor(), 
					officer.getStats().getLevel() + "");
			
			// add buttons
			TooltipMakerAPI btnHolder = row.createUIElement(100, BTN_HEIGHT, false);
			ButtonAPI selButton = btnHolder.addCheckbox(100, BTN_HEIGHT, StringHelper.getString("transfer", true), 
					ButtonAPI.UICheckboxSize.TINY, 0);
			selButton.setChecked(officer.getMemoryWithoutUpdate().getBoolean(MEM_KEY_TRANSFER));
			plugin.addButton(new InteractionDialogCustomPanelPlugin.ButtonEntry(
					selButton, "nex_selectOfficer_" + officer.getId()) 
			{
				@Override
				public void onToggle() {
					officer.getMemoryWithoutUpdate().set(MEM_KEY_TRANSFER, this.state, 0);
				}
			});
			
			row.addUIElement(btnHolder).rightOfTop(text, pad);
			
			tooltip.addCustom(row, pad);
		}
		sub.addUIElement(tooltip).inTL(0, 0);
		
		return sub;
	}
		
	protected void transferShips(final CampaignFleetAPI from, final CampaignFleetAPI to, 
			final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		List<FleetMemberAPI> ships = new ArrayList<>();
		final CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		for (FleetMemberAPI member : from.getFleetData().getMembersListCopy()) {
			if (member.getBuffManager().getBuff(MercFleetGenPlugin.MERC_BUFF_ID) != null)
				continue;
			if (member.getBuffManager().getBuff(Nex_SplitFleet.NO_SPLIT_FLEET_BUFF_ID) != null)
				continue;
			if (member == player.getFlagship())
				continue;
			if (member.getCaptain() != null && !member.getCaptain().isDefault()) {
				if (Misc.isMercenary(member.getCaptain()))
					continue;
			}
			ships.add(member);
		}
		
		final CampaignFleetAPI other = (from == player) ? to : from;
		
		dialog.showFleetMemberPickerDialog(getString("dialog_headerShipPick"), 
				StringHelper.getString("confirm", true),
				StringHelper.getString("cancel", true),
				5, 9, 96, // 3, 7, 58 or so
				true, true, ships, 
				new FleetMemberPickerListener() {
					public void pickedFleetMembers(List<FleetMemberAPI> members) {
						if (members != null) {
							transferShips(members, from, to);
							if (to == player) PlayerSpecialForcesIntel.stripOfficersIfExcess(dialog, members, false);
						}
						dialog.getVisualPanel().showFleetInfo(null, player, null, other);
						other.getMemoryWithoutUpdate().set("$fleetPoints", other.getFleetPoints(), 0);
						//((RuleBasedDialog)dialog.getPlugin()).updateMemory();
						populateMainOptions(dialog, memoryMap);
					}
					public void cancelledFleetMemberPicking() {
						//showMain(dialog, memoryMap);
					}
				});
	}
	
	protected void showReviveConfirmScreen(final CampaignFleetAPI fleet, List<FleetMemberAPI> members,
			final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap)
	{
		TextPanelAPI text = dialog.getTextPanel();
		
		List<String> shipNames = new ArrayList<>();
		for (FleetMemberAPI member : members) {
			shipNames.add(member.getShipName());
		}
		String shipNamesStr = StringHelper.writeStringCollection(shipNames);
		
		text.setFontSmallInsignia();
		text.addPara(SpecialForcesIntel.getString("dialogMsgRevivingShips", true) + ": " + shipNamesStr);
		TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
		NexUtilsGUI.addShipList(tooltip, dialog.getTextWidth(), members, 48, 3);
		text.addTooltip();
		
		// print cost and set to memory
		int cost = PlayerSpecialForcesIntel.getReviveCost(members);
		float have = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		boolean enough = have >= cost;
		String costStr = Misc.getDGSCredits(cost);
		
		text.addPara(StringHelper.getString("cost", true) + ": " + costStr, 
				enough ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), costStr);
		text.addPara(StringHelper.getString("youHave", true), Misc.getHighlightColor(), Misc.getDGSCredits(have));
		
		memoryMap.get(MemKeys.LOCAL).set("$nex_sf_shipsToRevive", members, 0);
		text.setFontInsignia();
		FireAll.fire(null, dialog, memoryMap, "Nex_SFReviveShipsOpts");	
		if (!enough)
			dialog.getOptionPanel().setEnabled("nex_sf_reviveShipConfirm", false);
	}
		
	protected void pickShipsForRevive(final CampaignFleetAPI fleet, 
			final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		PlayerSpecialForcesIntel intel = (PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(fleet);
		List<FleetMemberAPI> ships = new ArrayList<>(intel.getDeadMembers());
				
		dialog.showFleetMemberPickerDialog(getString("dialog_headerShipPick"), 
				StringHelper.getString("confirm", true),
				StringHelper.getString("cancel", true),
				5, 9, 96, // 3, 7, 58 or so
				true, true, ships, 
				new FleetMemberPickerListener() {
					public void pickedFleetMembers(List<FleetMemberAPI> members) {
						showReviveConfirmScreen(fleet, members, dialog, memoryMap);
					}
					public void cancelledFleetMemberPicking() {
						//showMain(dialog, memoryMap);
					}
				});
	}
	
	protected void confirmShipRevive(final CampaignFleetAPI fleet, final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap)
	{
		List<FleetMemberAPI> members = (List<FleetMemberAPI>)memoryMap.get(MemKeys.LOCAL).get("$nex_sf_shipsToRevive");
		int cost = PlayerSpecialForcesIntel.getReviveCost(members);
		
		PlayerSpecialForcesIntel intel = (PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(fleet);
		for (FleetMemberAPI member : members) {
			intel.reviveDeadMember(member);
		}
		
		autopickCommanderIfNeeded(fleet);
		fleet.getFleetData().sort();
		fleet.forceSync();
		
		Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
		AddRemoveCommodity.addCreditsLossText(cost, dialog.getTextPanel());
		
		dialog.getVisualPanel().showFleetInfo(null, Global.getSector().getPlayerFleet(), null, fleet);
		fleet.getMemoryWithoutUpdate().set("$fleetPoints", fleet.getFleetPoints(), 0);
		//((RuleBasedDialog)dialog.getPlugin()).updateMemory();
	}
	
	/**
	 * Execute the transfer of ships between the player and SF fleet.
	 * @param members
	 * @param from
	 * @param to
	 */
	protected void transferShips(List<FleetMemberAPI> members, CampaignFleetAPI from, CampaignFleetAPI to) 
	{		
		for (FleetMemberAPI member : members) {
			PersonAPI cap = member.getCaptain();
			boolean storedInFleetData = cap != null && from.getFleetData().getOfficerData(cap) != null;
			if (storedInFleetData && !cap.isDefault() && !cap.isAICore()) {
				from.getFleetData().removeOfficer(cap);
				to.getFleetData().addOfficer(cap);
				if (cap == from.getCommander()) {
					removeCommanderIfNeeded(cap, from);
				}
			}
			from.getFleetData().removeFleetMember(member);
			to.getFleetData().addFleetMember(member);
		}
		if (from.isPlayerFleet()) {
			autopickCommanderIfNeeded(to);
			to.getFleetData().sort();
			((PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(to)).notifyShipsAdded(members);
		}			
		else {
			autopickCommanderIfNeeded(from);
			((PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(from)).notifyShipsRemoved(members);
		}
			
		
		from.forceSync();
		to.forceSync();
	}
	
	/**
	 * Execute the transfer of officers between the player and SF fleet.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void transferOfficers(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI other = getFleet(dialog);
				
		transferOfficers(player, other);
		transferOfficers(other, player);
		autopickCommanderIfNeeded(other);
		
		player.forceSync();
		other.forceSync();
	}
	
	protected void transferOfficers(CampaignFleetAPI from, CampaignFleetAPI to) 
	{
		for (OfficerDataAPI od : from.getFleetData().getOfficersCopy()) 
		{
			PersonAPI officer = od.getPerson();
			if (!officer.getMemoryWithoutUpdate().getBoolean(MEM_KEY_TRANSFER))
				continue;
			
			FleetMemberAPI member = getShipCommandedBy(from, officer);
			if (member != null) member.setCaptain(null);
			from.getFleetData().removeOfficer(officer);
			to.getFleetData().addOfficer(officer);
			
			officer.getMemoryWithoutUpdate().unset(MEM_KEY_TRANSFER);
			removeCommanderIfNeeded(officer, from);
		}
	}
	
	protected void setCommander(CampaignFleetAPI fleet, SpecialForcesIntel sf, PersonAPI commander) 
	{
		if (commander != null)
			fleet.setCommander(commander);
		sf.setCommander(commander);
	}
	
	protected void setFlagship(CampaignFleetAPI fleet, SpecialForcesIntel sf, FleetMemberAPI member) {
		if (member != null) {
			fleet.getFleetData().setFlagship(member);
			sf.setFlagship(member);
		} else {
			sf.setFlagship(null);
		}
	}
	
	protected void autopickCommanderIfNeeded(CampaignFleetAPI fleet) {
		SpecialForcesIntel intel = SpecialForcesIntel.getIntelFromMemory(fleet);
		if (intel.getCommander() != null) return;
		if (fleet.getFleetData().getOfficersCopy().isEmpty()) return;
		
		PersonAPI com = fleet.getFleetData().getOfficersCopy().get(0).getPerson();
		setCommander(fleet, intel, com);
		setFlagship(fleet, intel, getShipCommandedBy(fleet, com));
	}
		
	// TBD
	@Deprecated
	protected void transferCargo(final CampaignFleetAPI player, final CampaignFleetAPI other, 
			final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		
	}
	
	/**
	 * Switch the player fleet to the special task group, to refit its ships and assign its officers.
	 * @param dialog
	 * @param memoryMap
	 * @deprecated
	 */
	@Deprecated
	protected void assignAndRefit(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		final CampaignFleetAPI playerCurr = Global.getSector().getPlayerFleet();
		final CampaignFleetAPI other = getFleet(dialog);
		Global.getSector().setPlayerFleet(other);
		dialog.getVisualPanel().showCore(CoreUITabId.FLEET, null, new CoreInteractionListener(){
			@Override
			public void coreUIDismissed() {
				Global.getSector().setPlayerFleet(playerCurr);
				other.forceSync();
			}
		});
	}
	
	/**
	 * Gets a sorted list of commander skills for picking from.
	 * @return
	 */
	public static List<SkillSpecAPI> getSortedSkillList() {
		List<SkillSpecAPI> results = new ArrayList<>();
		
		for (String skillId : Global.getSettings().getSkillIds()) {
			SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
			if (SKILL_AVAILABLE_OVERRIDES.containsKey(skillId)) {
				Boolean canUse = SKILL_AVAILABLE_OVERRIDES.get(skillId);
				if (!canUse) continue;
			} else {
				if (skill.hasTag(Skills.TAG_DEPRECATED)) continue;
				if (skill.hasTag(Skills.TAG_PLAYER_ONLY)) continue;
				if (skill.hasTag(Skills.TAG_NPC_ONLY)) continue;
				if (!skill.isAdmiralSkill()) continue;
			}
			results.add(skill);
		}
				
		final List<String> aptitudesOrdered = new ArrayList<>(Arrays.asList(new String[]{
			Skills.APT_COMBAT,
			Skills.APT_LEADERSHIP,
			Skills.APT_TECHNOLOGY,
			Skills.APT_INDUSTRY
		}));
		
		Collections.sort(results, new Comparator<SkillSpecAPI>(){
			@Override
			public int compare(SkillSpecAPI s1, SkillSpecAPI s2) {
				int result = Integer.compare(
						aptitudesOrdered.indexOf(s1.getGoverningAptitudeId()), 
						aptitudesOrdered.indexOf(s2.getGoverningAptitudeId()));
				if (result != 0) return result;
				
				result = Integer.compare(s1.getTier(), s2.getTier());
				return result;
			}		
		});
		return results;
	}
	
	/**
	 * Opens the custom panel for picking the commander's skills.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void setCommanderSkills(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		float pad = 3, opad = 10;
		float textWidth = 200;
		float imageWidth = 40;
		float skillPanelWidth = imageWidth + textWidth + 6;
		Color h = Misc.getHighlightColor();
		
		CampaignFleetAPI fleet = getFleet(dialog);
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		PersonAPI commander = sf.getCommander();
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		// headers
		tooltip.setParaSmallOrbitron();
		tooltip.addPara(getString("dialog_headerTrainCommander"), 0).setAlignment(Alignment.MID);
		tooltip.setParaFontDefault();
		
		// some logic
		List<SkillSpecAPI> skills = SORTED_SKILLS;
		final Set<String> wantedSkills = new HashSet<>();
		for (SkillSpecAPI skill : skills) {
			if (commander.getStats().getSkillLevel(skill.getId()) <= 0) continue;
			wantedSkills.add(skill.getId());
		}
		
		int pointsUsed = 0;
		for (String skill : wantedSkills) {
			pointsUsed += SKILL_VALUES.get(skill);
		}
		
		// officer image, name, and skill points used
		TooltipMakerAPI img = tooltip.beginImageWithText(commander.getPortraitSprite(), 64);
		img.setParaSmallInsignia();
		img.addPara(commander.getNameString(), pad);
		final LabelAPI totalLbl = img.addPara(getString("dialog_maxSkillPoints") + ": %s/%s", pad, h, 
				pointsUsed + "", MAX_SKILL_LEVEL + "");
		tooltip.addImageWithText(opad);
		TooltipCreator offTT = new NexUtilsGUI.ShowPeopleOfficerTooltip(commander);
		tooltip.addTooltipToPrevious(offTT, TooltipMakerAPI.TooltipLocation.BELOW);
				
		memoryMap.get(MemKeys.LOCAL).set("$nex_psf_wantedSkills", wantedSkills, 0);
		memoryMap.get(MemKeys.LOCAL).set("$nex_psf_originalSkills", new HashSet<>(wantedSkills), 0);
		memoryMap.get(MemKeys.LOCAL).set("$nex_psf_skillsChangedAny", false, 0);
		
		final Map<String, ButtonAPI> skillCheckboxes = new HashMap<>();
		int numPerRow = (int)Math.floor(Nex_VisualCustomPanel.PANEL_WIDTH/skillPanelWidth);
		int numRows = skills.size()/numPerRow + 1;
		//dialog.getTextPanel().addPara(String.format("%s per row, %s rows", numPerRow, numRows));
		
		CustomPanelAPI skillsHolder = panel.createCustomPanel(Nex_VisualCustomPanel.PANEL_WIDTH, numRows * (imageWidth + pad), null);
		
		// per-skill display:
		int numThisRow = 0;
		CustomPanelAPI lastPanel = null;
		CustomPanelAPI firstOfLastRow = null;
		for (final SkillSpecAPI skill : skills) {
			String skillId = skill.getId();
			int cost = SKILL_VALUES.get(skillId);
			
			CustomPanelGenResult cpg = NexUtilsGUI.addPanelWithFixedWidthImage(panel, null, 
					skillPanelWidth, 48, skill.getName(), textWidth, pad, 
					skill.getSpriteName(), 48, 
					0, skill.getGoverningAptitudeColor(), false, null);
			CustomPanelAPI skillPanel = cpg.panel;
			TooltipMakerAPI text = (TooltipMakerAPI)cpg.elements.get(1);
			text.addPara(StringHelper.getString("cost", true) + ": %s", 0, h, cost + "");			
			
			ButtonAPI check = text.addCheckbox(textWidth, 14, StringHelper.getString("enable", true), 
					ButtonAPI.UICheckboxSize.TINY, pad);
			skillCheckboxes.put(skillId, check);
			check.setChecked(wantedSkills.contains(skillId));
			
			boolean canAfford = pointsUsed + cost <= MAX_SKILL_LEVEL;
			check.setEnabled(check.isChecked() || canAfford);
			plugin.addButton(new InteractionDialogCustomPanelPlugin.ButtonEntry(
					check, "nex_selectSkill_" + skill.getId()) 
			{
				@Override
				public void onToggle() {
					skillButtonPressed(button, skill.getId(), wantedSkills, skillCheckboxes, totalLbl);
					checkAnySkillChanges(wantedSkills, dialog, memoryMap.get(MemKeys.LOCAL));
				}
			});
			
			if (numThisRow >= numPerRow) {
				// move to next row
				skillsHolder.addComponent(skillPanel).belowLeft(firstOfLastRow, 3);
				firstOfLastRow = skillPanel;
				numThisRow = 0;
			} else if (firstOfLastRow == null) {
				// first in list
				skillsHolder.addComponent(skillPanel).inTL(0, pad);
				firstOfLastRow = skillPanel;
			} else {
				skillsHolder.addComponent(skillPanel).rightOfTop(lastPanel, 3);
			}
			lastPanel = skillPanel;			
			numThisRow++;
		}
		tooltip.addCustom(skillsHolder, 3);
		
		Nex_VisualCustomPanel.addTooltipToPanel();
		// we just opened the window, so no skills can be changed
		dialog.getOptionPanel().setEnabled("nex_configSF_commanderSkillsConfirm", false);
	}
	
	/**
	 * Adds/removes the skill ID to wanted skills as needed, and updats the checkbox state.
	 * @param button
	 * @param skillId
	 * @param wantedSkills
	 * @param checkboxes
	 * @param totalLabel
	 */
	protected void skillButtonPressed(ButtonAPI button, String skillId, Set<String> wantedSkills, 
			Map<String, ButtonAPI> checkboxes, LabelAPI totalLabel) 
	{
		if (button.isChecked()) wantedSkills.add(skillId);
		else wantedSkills.remove(skillId);
		
		int pointsUsed = 0;
		for (String skill : wantedSkills) {
			pointsUsed += SKILL_VALUES.get(skill);
		}
		
		for (String otherSkillId : checkboxes.keySet()) {
			ButtonAPI otherButton = checkboxes.get(otherSkillId);
			boolean canAfford = pointsUsed + SKILL_VALUES.get(otherSkillId) <= MAX_SKILL_LEVEL;
			if (button == otherButton) continue;
			if (otherButton.isChecked()) continue;
			otherButton.setEnabled(canAfford);
		}
		
		totalLabel.setText(String.format(getString("dialog_maxSkillPoints") + ": %s/%s", pointsUsed, MAX_SKILL_LEVEL));
		totalLabel.setHighlight(pointsUsed + "", MAX_SKILL_LEVEL + "");
	}
	
	protected int getTotalSkillValue(Collection<String> skills) {
		int value = 0;
		for (String skill : skills) {
			value += SKILL_VALUES.get(skill);
		}
		return value;
	}
	
	/**
	 * Check if the existing and wanted skill lists differ, set the confirm option enabled state accordingly.
	 * @param wantedSkills
	 * @param dialog
	 * @param local
	 */
	protected void checkAnySkillChanges(Set<String> wantedSkills, InteractionDialogAPI dialog, MemoryAPI local) 
	{	
		Set<String> original = (Set<String>)local.get("$nex_psf_originalSkills");
		boolean changed = !original.equals(wantedSkills);
		
		local.set("$nex_psf_skillsChangedAny", changed, 0);
		dialog.getOptionPanel().setEnabled("nex_configSF_commanderSkillsConfirm", changed);
	}
	
	/**
	 * Remove existing skills, apply new ones.
	 * @param dialog
	 * @param local
	 */
	protected void applySkillChanges(InteractionDialogAPI dialog, MemoryAPI local) 
	{		
		CampaignFleetAPI fleet = getFleet(dialog);
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		PersonAPI commander = sf.getCommander();
		
		Set<String> original = (Set<String>)local.get("$nex_psf_originalSkills");
		Set<String> updated = (Set<String>)local.get("$nex_psf_wantedSkills");
		for (String toRemove : original) {
			commander.getStats().setSkillLevel(toRemove, 0);
			//dialog.getTextPanel().addPara("Removing skill " + toRemove);
		}
		for (String toAdd : updated) {
			commander.getStats().setSkillLevel(toAdd, 1);
			//dialog.getTextPanel().addPara("Adding skill " + toAdd);
		}
		
		local.unset("$nex_psf_wantedSkills");
		local.unset("$nex_psf_originalSkills");
		local.unset("$nex_psf_skillsChangedAny");
	}
	
	/**
	 * Create the special task group (if appropriate) and return to previous dialog.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void done(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		if (memoryMap.get(MemKeys.LOCAL).getBoolean("$nex_psf_create")) {
			CampaignFleetAPI fleet = getFleet(dialog);
			SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
			sf.init(fleet.getCommander());
			Global.getSector().getIntelManager().addIntelToTextPanel(sf, dialog.getTextPanel());
			memoryMap.get(MemKeys.LOCAL).unset("$nex_psf_create");
			SectorEntityToken origTarget = memoryMap.get(MemKeys.LOCAL).getEntity("$nex_psf_origInteractionTarget");
			dialog.setInteractionTarget(origTarget);
			((RuleBasedDialog)dialog.getPlugin()).updateMemory();
			FireBest.fire(null, dialog, memoryMap, "PickGreeting");
		}
		
		Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);
	}
	
	/**
	 * Cancels special task group creation.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void cancelCreation(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		CampaignFleetAPI fleet = getFleet(dialog);
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		sf.endImmediately();
		memoryMap.get(MemKeys.GLOBAL).unset("$nex_psf_create");
		this.disband(dialog, memoryMap);
		
		SectorEntityToken origTarget = memoryMap.get(MemKeys.LOCAL).getEntity("$nex_psf_origInteractionTarget");
		dialog.setInteractionTarget(origTarget);
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();
		Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);
		FireBest.fire(null, dialog, memoryMap, "PickGreeting");
	}
	
	/**
	 * Disbands the special task group and adds its constituent units to the player fleet.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void disband(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI other = (CampaignFleetAPI)dialog.getInteractionTarget();		
		PlayerSpecialForcesIntel intel = (PlayerSpecialForcesIntel)SpecialForcesIntel.getIntelFromMemory(other);
		
		intel.disband();
	}
	
	/**
	 * If @{code person} is the fleet's commander, unset fleet commander in the special forces intel item.
	 * @param person
	 * @param fleet
	 */
	protected void removeCommanderIfNeeded(PersonAPI person, CampaignFleetAPI fleet) {
		// note: fleet itself can't not have a commander, will CTD
		//if (fleet.getCommander() == person)
		//	fleet.setCommander(null);
		SpecialForcesIntel intel = SpecialForcesIntel.getIntelFromMemory(fleet);
		if (intel != null && intel.getCommander() == person) {
			setCommander(fleet, intel, null);
			autopickCommanderIfNeeded(fleet);
			
			if (intel.getCommander() == null) {
				setFlagship(fleet, intel, null);
			} else {
				setFlagship(fleet, intel, getShipCommandedBy(fleet, intel.getCommander()));
			}
		}
	}
	
	public int getMaxOfficers(CampaignFleetAPI fleet) {
		PersonAPI commander = fleet.getCommander();
		if (commander == null) return 0;
		return commander.getStats().getOfficerNumber().getModifiedInt();
	}
	
	protected CampaignFleetAPI getFleet(InteractionDialogAPI dialog) {
		return (CampaignFleetAPI)dialog.getInteractionTarget();
	}
	
	protected FleetMemberAPI getShipCommandedBy(CampaignFleetAPI fleet, PersonAPI captain) {
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (member.getCaptain() == captain) return member;
		}
		return null;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_splitFleet", id, ucFirst);
	}
}
