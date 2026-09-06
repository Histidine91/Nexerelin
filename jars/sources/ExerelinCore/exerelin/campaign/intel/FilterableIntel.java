package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;

public interface FilterableIntel {
	boolean shouldNotify();

	/**
	 * @return True if event should be queued for delivery when player is near comm relay; false to deliver immediately.
	 */
	default boolean shouldQueue() {
		if (NexConfig.nexIntelQueued <= 0) return false;
		if (NexConfig.nexIntelQueued >= 2) return true;
		return shouldQueueImpl();
	}

	boolean shouldQueueImpl();

	default boolean addIntelWithFilterChecks() {
		if (!(this instanceof BaseIntelPlugin bip)) return false;
		return addIntelWithFilterChecks(bip);
	}

	/**
	 * @return True if event was added or queued, false if filtered out.
	 */
	default boolean addIntelWithFilterChecks(BaseIntelPlugin intel) {

		boolean notify = this.shouldNotify();
		boolean queue = this.shouldQueue();

		if (notify) {
			if (queue) {
				Global.getSector().getIntelManager().queueIntel(intel);
			} else {
				Global.getSector().getIntelManager().addIntel(intel, false);
			}
			return true;
		} else {

		}

		if (ExerelinModPlugin.isNexDev) {
			if (!notify) {
				String str = String.format("Suppressed notification for [%s] (%s) due to filter level", intel.getSmallDescriptionTitle(), intel.getClass().getName());
				Global.getSector().getCampaignUI().addMessage(str);
			}
			else if (notify && queue) {
				String str = String.format("Queueing intel notification for [%s] (%s)", intel.getSmallDescriptionTitle(), intel.getClass().getName());
				Global.getSector().getCampaignUI().addMessage(str);
			}
		}

		return false;
	}
}
