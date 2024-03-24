package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;

// don't think I need this yet, maybe someday
@Deprecated
public class MutableBaseEventFactor extends BaseEventFactor {

    protected MutableStat progressStat = new MutableStat(0);
    protected MutableStat magnitudeStat = new MutableStat(0);
    protected StatBonus allProgressMultStat = new StatBonus();



    public int getProgress(BaseEventIntel intel) {

        return 0;
    }

    public float getAllProgressMult(BaseEventIntel intel) {
        return 1f;
    }

}
