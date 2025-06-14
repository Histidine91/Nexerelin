package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import lombok.extern.log4j.Log4j;

import java.util.Map;

@Log4j
public class HyperspaceTopographySkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        Map<String, Boolean> quests = chain.getEnabledQuestMap();

        if (chain.isQuestEnabled("topography_700", quests)) {
            HyperspaceTopographyEventIntel intel = getOrCreateIntel();
            intel.setProgress(HyperspaceTopographyEventIntel.PROGRESS_5);
        }
        else if (chain.isQuestEnabled("topography_400", quests)) {
            HyperspaceTopographyEventIntel intel = getOrCreateIntel();
            intel.setProgress(HyperspaceTopographyEventIntel.PROGRESS_3);
        }
        else if (chain.isQuestEnabled("topography_250", quests)) {
            HyperspaceTopographyEventIntel intel = getOrCreateIntel();
            intel.setProgress(HyperspaceTopographyEventIntel.PROGRESS_2);
        }
    }

    @Override
    public void init() {
        if (Global.getSettings().getBoolean("nex_skipStoryDefault")) {
            if (chain == null) return;
            chain.quests.get(0).isEnabled = true;
        }
    }

    protected HyperspaceTopographyEventIntel getOrCreateIntel() {
        if (HyperspaceTopographyEventIntel.get() == null) {
            new HyperspaceTopographyEventIntel(null, false);
        }
        return HyperspaceTopographyEventIntel.get();
    }
}
