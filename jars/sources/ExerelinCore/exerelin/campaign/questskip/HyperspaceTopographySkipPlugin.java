package exerelin.campaign.questskip;

import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import exerelin.campaign.ExerelinSetupData;
import lombok.extern.log4j.Log4j;

import java.util.Map;

@Log4j
public class HyperspaceTopographySkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;

        Map<String, Boolean> quests = chain.getEnabledQuestMap();

        if (chain.isQuestEnabled("topography_700", quests)) {
            HyperspaceTopographyEventIntel intel = getOrCreateIntel();
            intel.setProgress(HyperspaceTopographyEventIntel.PROGRESS_5);
        }
        else if (chain.isQuestEnabled("topography_400", quests)) {
            HyperspaceTopographyEventIntel intel = getOrCreateIntel();
            intel.setProgress(HyperspaceTopographyEventIntel.PROGRESS_3);
        }
    }

    protected HyperspaceTopographyEventIntel getOrCreateIntel() {
        if (HyperspaceTopographyEventIntel.get() == null) {
            new HyperspaceTopographyEventIntel(null, false);
        }
        return HyperspaceTopographyEventIntel.get();
    }
}
