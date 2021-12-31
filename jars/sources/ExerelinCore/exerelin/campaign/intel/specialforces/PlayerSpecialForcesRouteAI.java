package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class PlayerSpecialForcesRouteAI extends SpecialForcesRouteAI {
	
	protected PlayerSpecialForcesIntel psf;
	
	public PlayerSpecialForcesRouteAI(PlayerSpecialForcesIntel sf) {
		super(sf);
		this.psf = sf;
	}
	
	@Override
	public SpecialForcesTask pickTask(boolean priorityDefenseOnly) {
		SpecialForcesTask task;
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
		if (!PlayerSpecialForcesIntel.AI_MODE) {
			if (psf.getMonthsSuppliesRemaining() < 1 || psf.getFuelFractionRemaining() < 0.25f) {
				return generateResupplyTask();
			}
		}
		
		return super.pickTask(priorityDefenseOnly);
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
