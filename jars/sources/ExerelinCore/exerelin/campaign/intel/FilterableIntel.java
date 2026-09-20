package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;

public interface FilterableIntel {

	/**
	 * @return False to hide the message completely (doesn't appear in intel screen). Can probably return {@code shouldNotify()} for non-ongoing intel items.
	 */
	default boolean shouldSend() {
		return true;
	}

	/**
	 * @return False to keep the message out of the notification section in bottom-left of campaign screen.
	 */
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
	 * @return True if the message was sent, false otherwise.
	 */
	default boolean addIntelWithFilterChecks(BaseIntelPlugin intel) {

		boolean notify = this.shouldNotify();
		boolean queue = this.shouldQueue();
		boolean send = this.shouldSend();

		if (send) {
			if (queue) {
				Global.getSector().getIntelManager().queueIntel(intel);
			} else {
				Global.getSector().getIntelManager().addIntel(intel, !notify);
			}
		}

		if (ExerelinModPlugin.isNexDev) {
			if (!send) {
				String str = String.format("Did not send [%s] (%s) due to filter level", intel.getSmallDescriptionTitle(), intel.getClass().getName());
				Global.getSector().getCampaignUI().addMessage(str);
			}
			else if (!notify) {
				String str = String.format("Avoiding notification for [%s] (%s) due to filter level", intel.getSmallDescriptionTitle(), intel.getClass().getName());
				Global.getSector().getCampaignUI().addMessage(str);
			}
			else if (notify && queue) {
				String str = String.format("Queueing intel notification for [%s] (%s)", intel.getSmallDescriptionTitle(), intel.getClass().getName());
				Global.getSector().getCampaignUI().addMessage(str);
			}
		}

		return send;
	}
}
