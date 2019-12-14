package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

/**
 * Interaction dialog to apply debugging commands to special task groups.
 */
public class SpecialForcesDebugDialog implements InteractionDialogPlugin {

	protected InteractionDialogAPI dialog;
	protected IntelUIAPI ui;
	protected SpecialForcesIntel intel;
	
	public SpecialForcesDebugDialog(SpecialForcesIntel intel, IntelUIAPI ui) {
		this.intel = intel;
		this.ui = ui;
		
	}
	
	@Override
	public void init(InteractionDialogAPI dialog) {
		this.dialog = dialog;
		intel.debugDialog = dialog;
		showOptions();
	}
	
	public void showOptions() {
		OptionPanelAPI options = dialog.getOptionPanel();
		options.addOption("Check orders", Options.CHECK_TASKS);
		options.addOption("Assign new task", Options.PICK_NEW_TASK);
		options.addOption("Patrol random market", Options.PATROL_RANDOM);
		//options.addOption("Reset route location", Options.RESET_ROUTE_LOCATION);
		options.addOption("Reconstitute fleet", Options.REBUILD);
		options.addOption("Validate route segment", Options.VALIDATE_ROUTE);
		options.addOption("Reset route custom object", Options.RESET_ROUTE_CUSTOM);
		options.addOption("Delete fleet and refund points", Options.DELETE);
		options.addOption("Exit", Options.EXIT);
		options.setShortcut(Options.EXIT, Keyboard.KEY_ESCAPE, false, false, false, false);
	}

	@Override
	public void optionSelected(String optionText, Object optionData) {
		if (optionText != null)
			dialog.getTextPanel().addPara(optionText);
		
		switch ((Options)optionData) {
			case CHECK_TASKS:
				intel.routeAI.updateTaskIfNeeded();
				break;
			case PICK_NEW_TASK:
				intel.routeAI.pickTask(false);
				break;
			case PATROL_RANDOM:
				assignRandomPatrolTask();
				break;
			case RESET_ROUTE_LOCATION:
				RouteSegment segment = new RouteSegment(0.1f, Global.getSector().getHyperspace().createToken(0, 0));
				intel.route.setCurrent(segment);
				break;
			case REBUILD:
				intel.orderFleetRebuild(true);
				break;
			case VALIDATE_ROUTE:
				dialog.getTextPanel().setFontSmallInsignia();
				
				Object custom = intel.route.getCurrent().custom;
				if (false && custom.equals(SpecialForcesRouteAI.ROUTE_IDLE_SEGMENT)) {
					dialog.getTextPanel().addPara("Idle segment found, why is it not moving on?");
					float intervalTime = intel.interval.getElapsed();
					dialog.getTextPanel().addPara("Current interval elapsed: " + intervalTime,
							Misc.getHighlightColor(), intervalTime + "");
				} else {
					dialog.getTextPanel().addPara("Route segment OK, custom: " + custom);
				}
				
				dialog.getTextPanel().setFontInsignia();
				break;
			case RESET_ROUTE_CUSTOM:
				dialog.getTextPanel().setFontSmallInsignia();
				intel.route.getCurrent().custom = SpecialForcesRouteAI.ROUTE_IDLE_SEGMENT;
				dialog.getTextPanel().addPara("Resetting idle segment custom object");
				dialog.getTextPanel().setFontInsignia();
				break;
			case DELETE:
				intel.endEvent();
				SpecialForcesManager.getManager().incrementPoints(intel.faction.getId(), SpecialForcesManager.POINTS_TO_SPAWN);
				dialog.dismiss();
				break;
			case EXIT:
				ui.updateUIForItem(intel);
				intel.debugDialog = null;
				dialog.dismissAsCancel();
				break;
		}
	}
	
	protected void assignRandomPatrolTask() {
		dialog.getTextPanel().addPara("Looking for random market to patrol");
		SpecialForcesRouteAI routeAI = intel.routeAI;
		WeightedRandomPicker<SpecialForcesRouteAI.SpecialForcesTask> picker = new WeightedRandomPicker<>();
		List<MarketAPI> alliedMarkets = routeAI.getAlliedMarkets();
		for (MarketAPI market : alliedMarkets) {
			float priority = routeAI.getPatrolPriority(market);
			picker.add(routeAI.generatePatrolTask(market, priority), priority);
		}
		if (picker.isEmpty()) {
			dialog.getTextPanel().addPara("No patrol targets found", Misc.getNegativeHighlightColor());
			return;
		}
		
		routeAI.assignTask(picker.pick());
	}

	@Override
	public void optionMousedOver(String arg0, Object arg1) {
			
	}

	@Override
	public void advance(float amount) {
		
	}

	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		
	}

	@Override
	public Object getContext() {
		return null;
	}

	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
	protected enum Options {
		CHECK_TASKS, PICK_NEW_TASK, PATROL_RANDOM, RESET_ROUTE_LOCATION, REBUILD, 
		VALIDATE_ROUTE, RESET_ROUTE_CUSTOM, DELETE, EXIT
	}
}
