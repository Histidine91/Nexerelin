package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.People;
import exerelin.campaign.ExerelinSetupData;

// for Princess of Persea
public class SOEQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        Global.getSector().getImportantPeople().getPerson(People.NERIENE_RAO).getRelToPlayer().setRel(0.04f);
        Global.getSector().getImportantPeople().getPerson(People.CASPIAN).getRelToPlayer().setRel(-0.2f);
        Global.getSector().getImportantPeople().getPerson(People.MAGNUS).getRelToPlayer().setRel(-0.4f);
    }

    @Override
    public boolean shouldShow() {
        return ExerelinSetupData.getInstance().corvusMode;
    }

    @Override
    public void applyMemKeys() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;
        super.applyMemKeys();
    }
}
