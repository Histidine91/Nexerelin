package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.*;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.PlayerSpecialForcesIntel;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.SpecialForcesTask;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;

import java.util.*;

public class Nex_SpecialForcesCommands extends BaseCommandPlugin {
	
	public static final String MEM_KEY_TASK = "$nex_psf_task";
	
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		SectorEntityToken token = dialog.getInteractionTarget();
		CampaignFleetAPI fleet = token instanceof CampaignFleetAPI ? (CampaignFleetAPI)token : null;
		if (fleet == null) return false;
		
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "main":
				showMainMenu(dialog, memoryMap);
				return true;
			case "patrol":
				giveOrderAtLocation(dialog, "patrol", local);
				return true;
			case "orbit":
				giveOrderAtLocation(dialog, "wait_orbit", local);
				return true;
			case "follow":
				giveFollowOrder(dialog, memoryMap);
				return true;
			case "joinRaid":
				showRaidScreen(dialog, memoryMap, fleet);
				return true;			
			case "autoAssign":
				autoAssign(dialog, (CampaignFleetAPI)dialog.getInteractionTarget());
				return true;
			case "wait_player":
				giveWaitOrder(dialog, memoryMap);
				return true;
			case "refreshIntelUI":
				refreshIntelUI(fleet, memoryMap);
				return true;
		}
		
		return false;
	}
	
	public void showMainMenu(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		new ShowDefaultVisual().execute(null, dialog, new ArrayList<Misc.Token>(), memoryMap);
		FireAll.fire(null, dialog, memoryMap, "Nex_CommandSFOptions");
	}
	
	public void autoAssign(InteractionDialogAPI dialog, CampaignFleetAPI fleet) {
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		SpecialForcesTask task = sf.getRouteAI().pickTask(false);
		if (task != null) {
			sf.getRouteAI().assignTask(task);
			printTaskInfo(dialog, sf, task);
		}
	}
	
	public void showRaidScreen(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap, 
			CampaignFleetAPI fleet) 
	{
		float pad = 3;
		float width = 320;
		float height = 80;
		float buttonHeight = 24;	
		float buttonWidth = 60;
				
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI tooltip = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		final SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		
		List<IntelInfoPlugin> allRaids = new ArrayList<>();
		
		final List<IntelInfoPlugin> incomingList = sf.getRouteAI().getActiveRaidsHostile();
		allRaids.addAll(incomingList);
		final List<IntelInfoPlugin> outgoingList = sf.getRouteAI().getActiveEventsFriendly();
		allRaids.addAll(outgoingList);
		
		for (IntelInfoPlugin iip : allRaids) {
			if (!(iip instanceof RaidIntel) && !(iip instanceof FleetGroupIntel)) continue;
			final BaseIntelPlugin intel = (BaseIntelPlugin)iip;

			CustomPanelAPI row = panel.createCustomPanel(Nex_VisualCustomPanel.PANEL_WIDTH - 4, height, null);
			
			TooltipMakerAPI info = row.createUIElement(width, height, false);
			TooltipMakerAPI imgWithText = info.beginImageWithText(intel.getIcon(), height/2);
			intel.createIntelInfo(imgWithText, ListInfoMode.MAP_TOOLTIP);
			info.addImageWithText(pad);
			row.addUIElement(info).inTL(pad, 0);
			
			TooltipMakerAPI btnHolder = row.createUIElement(buttonWidth, height, false);
			
			ButtonAPI selButton = btnHolder.addButton(StringHelper.getString("nex_specialForces", "dialogButtonViewRaid"), 
					"view_" + intel.hashCode(), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
					Alignment.MID, CutStyle.TOP, buttonWidth, buttonHeight, pad);
			plugin.addButton(new InteractionDialogCustomPanelPlugin.ButtonEntry(
					selButton, "nex_view_" + intel.hashCode())
			{
				@Override
				public void onToggle() {
					dialog.getVisualPanel().showCore(CoreUITabId.INTEL, null, intel, new NexUtilsGUI.NullCoreInteractionListener());
				}
			});
			
			ButtonAPI joinButton = btnHolder.addButton(StringHelper.getString("nex_specialForces", "dialogButtonJoinRaid"), 
					"join_" + intel.hashCode(), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
					Alignment.MID, CutStyle.BOTTOM, buttonWidth, buttonHeight, pad);
			plugin.addButton(new InteractionDialogCustomPanelPlugin.ButtonEntry(
					joinButton, "nex_join_" + intel.hashCode())
			{
				@Override
				public void onToggle() {
					boolean offensive = outgoingList.contains(intel);
					joinRaid(dialog, intel, sf, offensive);
					showMainMenu(dialog, memoryMap);
				}
			});
			
			row.addUIElement(btnHolder).rightOfTop(info, pad);
			
			tooltip.addCustom(row, pad);
		}
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	public void joinRaid(InteractionDialogAPI dialog, IntelInfoPlugin raid, SpecialForcesIntel sf, boolean offensive) {
		SpecialForcesRouteAI ai = sf.getRouteAI();
		SpecialForcesTask task;
		if (offensive) task = ai.generateRaidAssistTask(raid, ai.getEventAttackPriority(raid));
		else task = ai.generateRaidDefenseTask(raid, ai.getEventDefendPriority(raid));
		
		ai.assignTask(task);
		printTaskInfo(dialog, sf, task);
	}

	protected List<IntelInfoPlugin.ArrowData> getDestinationArrows() {
		List<IntelInfoPlugin.ArrowData> arrows = new ArrayList<>();
		for (PlayerSpecialForcesIntel psf : PlayerSpecialForcesIntel.getActiveIntelList()) {
			arrows.addAll(psf.getArrowData(null));
		}
		return arrows;
	}
	
	public void giveOrderAtLocation(final InteractionDialogAPI dialog, final String orderType, final MemoryAPI local) 
	{
		List<SectorEntityToken> entities = getTargetsForOrder(orderType);
		
		NexUtilsMarket.pickEntityDestination(dialog, entities, 
				StringHelper.getString("confirm", true), new NexUtilsMarket.CampaignEntityPickerWrapper(){
			@Override
			public void reportEntityPicked(SectorEntityToken token) {
				SpecialForcesTask task = generateTask(orderType, token);
				saveTaskToMemory(task, local);
				SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory((CampaignFleetAPI)dialog.getInteractionTarget());
				sf.getRouteAI().assignTask(task);
				printTaskInfo(dialog, sf, task);
				//dialog.getPlugin().optionSelected(null, "nex_commandSF_main");
			}

			@Override
			public void reportEntityPickCancelled() {
				//dialog.getPlugin().optionSelected(null, "nex_commandSF_main");
			}

			@Override
			public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) 
			{
				Nex_FleetRequest.createInfoTextBasic(info, entity, dialog.getInteractionTarget());
			}
		}, getDestinationArrows());
	}
	
	public void giveWaitOrder(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		SectorEntityToken token = fleet.getContainingLocation().createToken(fleet.getLocation());
		token.addTag("nex_player_location_token");
		SpecialForcesTask task = generateTask("wait_orbit", token);
		task.time = 99999 + 1;
		saveTaskToMemory(task, memoryMap.get(MemKeys.LOCAL));
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory((CampaignFleetAPI)dialog.getInteractionTarget());
		sf.getRouteAI().assignTask(task);
		printTaskInfo(dialog, sf, task);
	}
	
	public void giveFollowOrder(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		// TODO: probably needs its own token
		SpecialForcesTask task = generateTask("follow_player", Global.getSector().getPlayerFleet());
		task.time = 99999 + 1;
		saveTaskToMemory(task, memoryMap.get(MemKeys.LOCAL));
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory((CampaignFleetAPI)dialog.getInteractionTarget());
		sf.getRouteAI().assignTask(task);
		printTaskInfo(dialog, sf, task);
	}
	
	public void printTaskInfo(InteractionDialogAPI dialog, SpecialForcesIntel sf, SpecialForcesTask task) {
		dialog.getTextPanel().addPara(SpecialForcesIntel.getString("dialogMsgTaskAssigned", true) + ": " + task.getText());
		Global.getSector().getIntelManager().addIntelToTextPanel(sf, dialog.getTextPanel());
	}
	
	public void saveTaskToMemory(SpecialForcesTask task, MemoryAPI local) {
		local.set(MEM_KEY_TASK, task, 0);
	}
	
	public SpecialForcesTask getTaskFromMemory(MemoryAPI local) {
		return (SpecialForcesTask)local.get(MEM_KEY_TASK);
	}
	
	public SpecialForcesTask generateTask(String type, SectorEntityToken target) {
		SpecialForcesTask task = new SpecialForcesTask(type, 100f);
		task.playerIssued = true;
		task.system = target.getStarSystem();
		if (target.getMarket() != null)
			task.setMarket(target.getMarket());
		else
			task.setEntity(target);
		return task;
	}
	
	public List<SectorEntityToken> getTargetsForOrder(String type) {
		Set<SectorEntityToken> results = new HashSet<>();
		List<StarSystemAPI> systems = Global.getSector().getStarSystems();
		for (StarSystemAPI system : systems) {
			if ("patrol".equals(type)) {
				results.add(system.getCenter());
			}
			else {
				for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
					if (market.isHidden() && market.getPrimaryEntity().isDiscoverable())
						continue;
					if (market.getPrimaryEntity() == null) continue;
					
					results.add(market.getPrimaryEntity());
				}
				// "orbit player's current location"
				// yucky since setName doesn't work
				/*
				if (system == player.getContainingLocation()) {
					final SectorEntityToken token = system.createToken(player.getLocation());
					token.setName("[temp] Player location");	// does nothing!
					token.addTag("nex_player_location_token");
					results.add(token);
				}
				*/
			}
		}
		return new ArrayList<>(results);
	}
	
	public void refreshIntelUI(CampaignFleetAPI fleet, Map<String, MemoryAPI> memoryMap) {
		SpecialForcesIntel sf = SpecialForcesIntel.getIntelFromMemory(fleet);
		IntelUIAPI ui = (IntelUIAPI)memoryMap.get(MemKeys.LOCAL).get("$nex_uiToRefresh");
		if (ui != null) ui.updateUIForItem(sf);
	}
}
