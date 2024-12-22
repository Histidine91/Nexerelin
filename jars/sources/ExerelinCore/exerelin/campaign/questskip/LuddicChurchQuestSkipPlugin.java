package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Missions;
import com.fs.starfarer.api.impl.campaign.ids.People;
import com.fs.starfarer.api.impl.campaign.intel.misc.LuddicShrineIntel;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinSetupData;

public class LuddicChurchQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;

        if (chain.isQuestEnabled("pilgrimsPath")) {
            addShrineIntelAndMarkVisited("beholder_station");
            addShrineIntelAndMarkVisited("hesperus");
            addShrineIntelAndMarkVisited("gilead");
            addShrineIntelAndMarkVisited("jangala");
            addShrineIntelAndMarkVisited("killa");
            addShrineIntelAndMarkVisited("volturn");

            String id = Missions.PILGRIMS_PATH;
            makeNonStoryCritical("jangala", id);
            makeNonStoryCritical("hesperus", id);
            makeNonStoryCritical("gilead", id);
            makeNonStoryCritical("volturn", id);
        }

        if (chain.isQuestEnabled("knightErrant")) {
            String id = Missions.KNIGHT_ERRANT;
            makeNonStoryCritical("gilead", id);
            makeNonStoryCritical("chalcedon", id);
            makeNonStoryCritical("mazalot", id);

            Global.getSector().getImportantPeople().getPerson(People.JASPIS).getRelToPlayer().setRel(0.15f);
            Global.getSector().getImportantPeople().getPerson(People.BORNANEW).getRelToPlayer().setRel(0.04f);
        }
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

    protected static void addShrineIntelAndMarkVisited(String entityId) {
        SectorEntityToken entity = LuddicShrineIntel.getEntity(entityId);
        LuddicShrineIntel.addShrineIntelIfNeeded(entity, null, true);
        LuddicShrineIntel.setVisited(entity, null);
        if (entityId.equals("beholder_station")) {
            Global.getSector().getMemoryWithoutUpdate().set("$visitedShrineBeholderStation", true);
        } else {
            Global.getSector().getMemoryWithoutUpdate().set("$visitedShrine" + Misc.ucFirst(entityId), true);
        }
    }
}
