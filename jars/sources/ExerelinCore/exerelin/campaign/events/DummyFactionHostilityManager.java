package exerelin.campaign.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;

/**
 * Creates no hostility events. Added because the Diktat Civil War does stuff with the hostility manager.
 */
public class DummyFactionHostilityManager extends FactionHostilityManager {

	public DummyFactionHostilityManager() {
		super();
	}

	@Override
	protected EveryFrameScript createEvent() {
		// do nothing
		return null;
	}
}
