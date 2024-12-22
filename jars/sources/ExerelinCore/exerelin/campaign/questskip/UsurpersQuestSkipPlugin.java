package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Missions;
import com.fs.starfarer.api.impl.campaign.ids.People;
import exerelin.campaign.ExerelinSetupData;

public class UsurpersQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;

        PersonAPI hyder = Global.getSector().getImportantPeople().getPerson(People.HYDER);
        hyder.getMemoryWithoutUpdate().set("$trust", 2);
        //hyder.getRelToPlayer().setRel(-0.01f);

        PersonAPI macario = Global.getSector().getImportantPeople().getPerson(People.MACARIO);
        macario.getRelToPlayer().setRel(0.01f);

        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        addShipToPlayerFleet("executor_Hull");
        pf.getCargo().addWeapons("kineticblaster", 3);
        pf.getCargo().addWeapons("gigacannon", 1);

        String id = Missions.THE_USURPERS;
        makeNonStoryCritical("sindria", id);
        makeNonStoryCritical("volturn", id);
        makeNonStoryCritical("umbra", id);
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
