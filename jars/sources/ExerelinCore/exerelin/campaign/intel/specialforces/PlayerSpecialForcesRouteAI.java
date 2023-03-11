package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class PlayerSpecialForcesRouteAI extends SpecialForcesRouteAI {
	
	protected PlayerSpecialForcesIntel psf;
	
	public PlayerSpecialForcesRouteAI(PlayerSpecialForcesIntel sf) {
		super(sf);
		this.psf = sf;
	}
	
	@Override
	public void notifyRouteFinished() {
		if (!psf.independentMode && currentTask != null && currentTask.type != TaskType.REBUILD && currentTask.raid == null) 
		{
			//Global.getSector().getCampaignUI().addMessage("Repeating current task");
			//log.info("Repeating current task");
			assignTask(currentTask, true);
			return;
		}
		super.notifyRouteFinished();
	}

	@Override
	public SpecialForcesTask pickTask(boolean priorityDefenseOnly, boolean isManualOrder) {
		if (!psf.independentMode && !isManualOrder) {
			Global.getSector().getCampaignUI().addMessage("Warning, attempting to assign task to special task group while not in independent mode");
			Global.getSector().getCampaignUI().addMessage("See starsector.log for more info");
			log.warn("Attempting to assign task to special task group while not in independent mode", new Throwable());
		}
		
		//SpecialForcesTask task;
		/*
		if (!psf.isIndependentMode()) {
			if (currentTask != null) {
				try {
					return (SpecialForcesTask)currentTask.clone();
				} catch (CloneNotSupportedException c) {
					log.error("Task cloning failed", c);
				}
			}
			task = new SpecialForcesTask(TaskType.IDLE, 0);
			task.time = 15;
			return task;
		}
		*/
		if (!PlayerSpecialForcesIntel.AI_MODE) {
			if (psf.getMonthsSuppliesRemaining() < 1 || psf.getFuelFractionRemaining() < 0.25f) {
				return generateResupplyTask();
			}
		}
		
		return super.pickTask(priorityDefenseOnly, isManualOrder);
	}
	
	@Override
	public void updateTaskIfNeeded() {
		if (!psf.independentMode) return;
		super.updateTaskIfNeeded();
	}
	
	public SpecialForcesTask generateResupplyTask() {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.RESUPPLY, 9999f);
		// TODO: find location
		//task.
		return task;
	}
	
	public MarketAPI findFuelVendor() {
		return null;
	}
	
	public MarketAPI findSuppliesVendor() {
		return null;
	}
}
