package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercFleetGenPlugin;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsGUI.CustomPanelGenResult;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.log4j.Log4j;

// no use this anymore, instead create a blank SF event instead and do everything from there
@Log4j
@Deprecated
public class Nex_SplitFleet extends BaseCommandPlugin {
	
	public static final String MEM_KEY_DATA = "$nex_splitFleet_data";
	public static final String NO_SPLIT_FLEET_BUFF_ID = "nex_noSplitFleetBuff";
	public static final float SHIP_ICON_SIZE_MAIN = 64;
	public static final float PORTRAIT_SIZE_MAIN = 40;
	public static final float PORTRAIT_SIZE_PICKER = 48;
	public static final float BTN_HEIGHT = 16;
	
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "shouldShow":
				return true;
			case "canCreate":
				return true;
			case "start":
				start(dialog, memoryMap);
				return true;
			case "setSFMode":
				setSFMode(memoryMap, params.get(1).getBoolean(memoryMap));
				return true;
			case "setMemory":	// TBD
				//setMemory(memoryMap, params.get(1).getString(memoryMap), params.get(2));
				return true;
			case "mainMenu":
				showMain(dialog, memoryMap);
				return true;
			case "pickShips":
				pickShips(dialog, memoryMap);
				return true;
			case "pickOfficers":
				pickOfficers(dialog, memoryMap);
				return true;
			case "pickCargo":
				pickCargo(dialog, memoryMap);
				return true;
			case "confirm":
				confirm(dialog, memoryMap);
				return true;
			case "cancel":
				cancel(dialog, memoryMap);
				return true;
			case "canConfirm":
				return canConfirm(memoryMap);
				
			case "merge":
				merge(dialog, memoryMap);
				return true;
		}
		
		return false;
	}
	
	protected void start(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		memoryMap.get(MemKeys.LOCAL).set(MEM_KEY_DATA, new SplitFleetCreationData());
		
		showMain(dialog, memoryMap);
	}
	
	// show a custom panel with current ships and officers as icons
	protected void showMain(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		showMainInfo(dialog, memoryMap);
		populateMainOptions(dialog, memoryMap);
	}
	
	protected void showMainInfo(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		float pad = 3, opad = 10;
		
		SplitFleetCreationData data = getData(memoryMap);
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.getTooltip();
		
		List<FleetMemberAPI> ships = new ArrayList<>(data.ships);
		
		int iconsPerRow = (int)Math.floor((Nex_VisualCustomPanel.PANEL_WIDTH - 16)/SHIP_ICON_SIZE_MAIN);
		int numRows = (int)Math.ceil(ships.size() / (float)iconsPerRow);
		
		tooltip.setParaSmallOrbitron();
		tooltip.addPara(getString("dialog_headerMain"), 0);
		tooltip.setParaFontDefault();
		
		// list ships
		tooltip.addSectionHeading(getString("dialog_headerShips"), Alignment.MID, opad);
		tooltip.addShipList(iconsPerRow, numRows, SHIP_ICON_SIZE_MAIN, Misc.getBasePlayerColor(), ships, pad);
		
		// list officers
		tooltip.addSectionHeading(getString("dialog_headerOfficers"), Alignment.MID, opad);
		for (PersonAPI officer : data.officers) {
			TooltipMakerAPI row = tooltip.beginImageWithText(officer.getPortraitSprite(), PORTRAIT_SIZE_MAIN);
			Color color = officer == data.commander ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			row.addPara(NexUtilsGUI.getAbbreviatedName(officer), color, 0);
			row.addPara(getString("dialog_level", true), pad, Misc.getHighlightColor(), officer.getStats().getLevel() + "");
			tooltip.addImageWithText(pad);
			tooltip.addTooltipToPrevious(new NexUtilsGUI.ShowPeopleOfficerTooltip(officer), 
					TooltipMakerAPI.TooltipLocation.BELOW);
		}
		
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	protected void populateMainOptions(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) 
	{
		FireAll.fire(null, dialog, memoryMap, "Nex_SplitFleetOptions");
	}
	
	protected boolean canConfirm(Map<String, MemoryAPI> memoryMap) {
		SplitFleetCreationData data = getData(memoryMap);
		return !data.ships.isEmpty() && data.commander != null;
	}
		
	protected void pickShips(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		List<FleetMemberAPI> ships = new ArrayList<>();
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		for (FleetMemberAPI member : player.getFleetData().getMembersListCopy()) {
			if (member.getBuffManager().getBuff(MercFleetGenPlugin.MERC_BUFF_ID) != null)
				continue;
			if (member.getBuffManager().getBuff(NO_SPLIT_FLEET_BUFF_ID) != null)
				continue;
			if (member.getVariant().hasTag(Tags.SHIP_CAN_NOT_SCUTTLE))
				continue;
			if (member == player.getFlagship())
				continue;
			ships.add(member);
		}
		
		dialog.showFleetMemberPickerDialog(getString("dialog_headerShipPick"), 
				StringHelper.getString("confirm", true),
				StringHelper.getString("cancel", true),
				5, 9, 96, // 3, 7, 58 or so
				true, true, ships, 
				new FleetMemberPickerListener() {
					public void pickedFleetMembers(List<FleetMemberAPI> members) {
						if (members != null) {
							SplitFleetCreationData data = getData(memoryMap);
							data.ships = new HashSet<>(members);
							data.officers.clear();
							data.commander = null;
							boolean first = true;
							for (FleetMemberAPI transfer : members) {
								PersonAPI cap = transfer.getCaptain();
								if (cap != null && !cap.isDefault() && !Misc.isMercenary(cap)) {	
									data.officers.add(cap);
									if (first && !cap.isAICore()) {
										first = false;
										data.commander = cap;
									}									
								}
							}
						}
						showMain(dialog, memoryMap);
					}
					public void cancelledFleetMemberPicking() {
						showMain(dialog, memoryMap);
					}
				});
	}
	
	protected void pickOfficers(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		float pad = 3, opad = 10;
		
		final SplitFleetCreationData data = getData(memoryMap);
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		FactionAPI player = Global.getSector().getPlayerFaction();
		
		List<PersonAPI> officers = new ArrayList<>();
		for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) 
		{
			if (Misc.isMercenary(od.getPerson())) continue;
			if (Misc.isUnremovable(od.getPerson())) continue;
			officers.add(od.getPerson());
		}
		
		tooltip.setParaSmallOrbitron();
		tooltip.addPara(getString("dialog_headerOfficerPick"), 0);
		tooltip.setParaFontDefault();
		
		// list officers
		for (final PersonAPI officer : officers) {
			TooltipCreator offTT = new NexUtilsGUI.ShowPeopleOfficerTooltip(officer);
			
			CustomPanelGenResult cpg = NexUtilsGUI.addPanelWithFixedWidthImage(panel, null, 
					Nex_VisualCustomPanel.PANEL_WIDTH, PORTRAIT_SIZE_PICKER, null, 120, pad, 
					officer.getPortraitSprite(), PORTRAIT_SIZE_PICKER, 
					pad, null, false, offTT);
			CustomPanelAPI row = cpg.panel;
			TooltipMakerAPI text = (TooltipMakerAPI)cpg.elements.get(1);
			
			Color color = officer == data.commander ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			text.addPara(NexUtilsGUI.getAbbreviatedName(officer), color, 0);
			text.addPara(getString("dialog_level", true), pad, Misc.getHighlightColor(), 
					officer.getStats().getLevel() + "");
			
			// add buttons
			boolean selected = data.officers.contains(officer);
			TooltipMakerAPI btnHolder = row.createUIElement(100, BTN_HEIGHT, false);
			ButtonAPI selButton = btnHolder.addCheckbox(100, BTN_HEIGHT, StringHelper.getString("select", true), 
					ButtonAPI.UICheckboxSize.TINY, 0);
			selButton.setChecked(selected);
			plugin.addButton(new InteractionDialogCustomPanelPlugin.ButtonEntry(
					selButton, "nex_selectOfficer_" + officer.getId()) 
			{
				@Override
				public void onToggle() {
					if (this.state) data.officers.add(officer);
					else {
						data.officers.remove(officer);
						if (officer == data.commander) data.commander = null;
					}
					pickOfficers(dialog, memoryMap);
				}
			});
			
			// todo: use radio button system for this?
			if (selected) {
				ButtonAPI comButton = btnHolder.addAreaCheckbox(getString("dialog_commander", true), 
						"commander_" + officer.getId(), 
						player.getBaseUIColor(), player.getDarkUIColor(), player.getBrightUIColor(), 
						100, BTN_HEIGHT, pad);
				comButton.setChecked(officer == data.commander);
				plugin.addButton(new InteractionDialogCustomPanelPlugin.RadioButtonEntry(
						comButton, "nex_selectOfficer_" + officer.getId()) 
				{
					@Override
					public void onToggle() {
						data.commander = officer;	
						pickOfficers(dialog, memoryMap);
					}
				});
			}
			
			
			row.addUIElement(btnHolder).rightOfTop(text, pad);
			
			tooltip.addCustom(row, pad);
		}
		Nex_VisualCustomPanel.addTooltipToPanel();
		dialog.getOptionPanel().clearOptions();
		dialog.getOptionPanel().addOption(StringHelper.getString("back", true), "nex_splitFleet_pickOfficersBack");
	}
	
	// TBD
	protected void pickCargo(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		
	}
	
	protected void confirm(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		SplitFleetCreationData data = getData(memoryMap);
		if (data.isSF) {
			/*
			PlayerSpecialForcesIntel sf = new PlayerSpecialForcesIntel(
					dialog.getInteractionTarget().getMarket(), Global.getSector().getPlayerFaction(), 
					data.commander, data.ships, data.officers);
			removeFromPlayerFleet(data);
			sf.init();
			*/
		} else {
			// kinda unsafe to remove before adding, but seems necessary to ensure correct data
			removeFromPlayerFleet(data);
			CampaignFleetAPI fleet = createFleet(data);
		}
	}
	
	protected void cancel(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);
		memoryMap.get(MemKeys.LOCAL).unset(MEM_KEY_DATA);
		memoryMap.get(MemKeys.LOCAL).unset("$nex_splitFleet_mode");
	}
	
	protected CampaignFleetAPI createFleet(SplitFleetCreationData data) {
		CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(Factions.PLAYER, FleetTypes.PATROL_LARGE, null);
		
		for (PersonAPI officer : data.officers) {
			if (officer.isAICore()) continue;
			fleet.getFleetData().addOfficer(officer);
		}
		for (FleetMemberAPI member : data.ships) {
			fleet.getFleetData().addFleetMember(member);
		}
		
		fleet.setCommander(data.commander);
		FleetMemberAPI flagship = getShipCommandedBy(fleet, data.commander);
		if (flagship != null) fleet.getFleetData().setFlagship(flagship);
		
		fleet.setInflated(true);
		fleet.setInflater(null);
		
		fleet.setNoAutoDespawn(true);
		
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		fleet.getMemoryWithoutUpdate().set("$nex_splitFleet", true);
		
		fleet.getFleetData().setSyncNeeded();
		fleet.getFleetData().syncIfNeeded();
		
		fleet.setTransponderOn(false);
		fleet.getAbility(Abilities.GO_DARK).activate();
		fleet.setAI(null);
		
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		player.getContainingLocation().addEntity(fleet);
		fleet.getLocation().x = player.getLocation().x;
		fleet.getLocation().y = player.getLocation().y;		
		
		return fleet;
	}
	
	protected void removeFromPlayerFleet(SplitFleetCreationData data) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		//log.info("Officers to transfer: " + data.officers.toString());
		for (FleetMemberAPI member : data.ships) {
			// if we're moving any ship but not its captain, unset ship's captain
			//log.info("Checking officer " + member.getCaptain());
			if (!data.officers.contains(member.getCaptain())) {
				log.info("Fleet member " + member.getShipName() + " transferring alone");
				member.setCaptain(null);
			}
				
			player.getFleetData().removeFleetMember(member);
		}
		for (PersonAPI officer : data.officers) {
			// if we're moving any captain but not their ship, unset ship's captain
			FleetMemberAPI myShip = getShipCommandedBy(player, officer);
			if (myShip != null && !data.ships.contains(myShip)) {
				log.info("Officer " + officer.getNameString() + " transferring alone");
				myShip.setCaptain(null);
			}
			player.getFleetData().removeOfficer(officer);
 		}
		player.forceSync();
	}
	
	protected void merge(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI other = (CampaignFleetAPI)dialog.getInteractionTarget();
		
		for (OfficerDataAPI officer : other.getFleetData().getOfficersCopy()) {
			if (officer.getPerson().isAICore()) continue;
			player.getFleetData().addOfficer(officer);
		}
		for (FleetMemberAPI member : other.getFleetData().getMembersListCopy()) {
			player.getFleetData().addFleetMember(member);
		}
		
		player.getCargo().addAll(other.getCargo());
		
		other.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
		
		player.forceSync();
	}
	
	protected static FleetMemberAPI getShipCommandedBy(CampaignFleetAPI fleet, PersonAPI captain) {
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (member.getCaptain() == captain) return member;
		}
		return null;
	}
	
	protected void setSFMode(Map<String, MemoryAPI> memoryMap, boolean mode) {
		getData(memoryMap).isSF = mode;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_splitFleet", id, ucFirst);
	}
	
	public SplitFleetCreationData getData(Map<String, MemoryAPI> memoryMap) {
		return (SplitFleetCreationData)memoryMap.get(MemKeys.LOCAL).get(MEM_KEY_DATA);
	}
		
	public static class SplitFleetCreationData {
		public Set<FleetMemberAPI> ships = new LinkedHashSet<>();
		public Set<PersonAPI> officers = new LinkedHashSet<>();
		public PersonAPI commander;
		public Map<String, Object> memData = new HashMap<>();
		public CargoAPI cargo = Global.getFactory().createCargo(true);
		public boolean isSF;
	}
}
