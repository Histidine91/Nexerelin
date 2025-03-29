package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.PerseanLeagueHostileActivityFactor;

/**
 * Same as vanilla but with faction check for Kazeron.
 */
@Deprecated
public class NexPerseanLeagueHostileActivityFactor extends PerseanLeagueHostileActivityFactor {

    public NexPerseanLeagueHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (getKazeron(true) == null) {
            return 0;
        }
        return super.getProgress(intel);
    }
}
