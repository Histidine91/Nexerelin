package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.People;
import com.fs.starfarer.api.impl.campaign.missions.RecoverAPlanetkiller;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.PK_CMD;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;

@Log4j
public class ScytheQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        PersonAPI skiron = Global.getSector().getImportantPeople().getPerson(People.SKIRON);
        MemoryAPI mem = skiron.getMemoryWithoutUpdate();
        mem.set("$gainedSomeTrust", true);
        mem.set("$gaveDatapads", true);
        mem.set("$askedWhereYouFrom", true);
        mem.set("$knowMoreIsGoingOn", true);
        mem.set("$askedProtocols", true);
        mem.set("$askedAboutStarships", true);

        Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(Items.PLANETKILLER, null), 1);

        StarSystemAPI pkSystem = RecoverAPlanetkiller.getPKSystem();
        if (pkSystem == null) {
            log.error("Failed to find Sentinel's system");
            return;
        }
        SectorEntityToken cache = null;
        for (SectorEntityToken entity : pkSystem.getAllEntities()) {
            if (entity.getMemoryWithoutUpdate().getBoolean(MiscellaneousThemeGenerator.PK_CACHE_KEY)) {
                cache = entity;
                break;
            }
        }
        if (cache == null) {
            log.error("Failed to find planetkiller cache in system");
            return;
        }
        final SectorEntityToken cache2 = cache;

        Global.getSector().addScript(new DelayedActionScript(0) {
            @Override
            public void doAction() {
                boolean createdDialog = Global.getSector().getCampaignUI().showInteractionDialog(cache2);
                if (!createdDialog) {
                    log.error("Failed to create interaction dialog with cache, is another dialog already open? How annoying");
                    return;
                }

                InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
                List<Misc.Token> params = Misc.tokenize("convertSentinelToColony");
                new PK_CMD().execute(null, dialog, params, dialog.getPlugin().getMemoryMap());
                params = Misc.tokenize("removePKDefenses");
                new PK_CMD().execute(null, dialog, params, dialog.getPlugin().getMemoryMap());
                dialog.dismiss();
                cache2.setExpired(true);

                String str = StringHelper.getString("exerelin_ngc", "msgSkipStoryScythe");
                Color color = Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();
                Global.getSector().getCampaignUI().addMessage(str, color);
            }
        });
    }
}
