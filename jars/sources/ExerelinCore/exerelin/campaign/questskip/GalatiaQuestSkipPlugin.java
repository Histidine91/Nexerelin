package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Missions;
import com.fs.starfarer.api.impl.campaign.ids.People;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionCreator;
import exerelin.campaign.AcademyStoryVictoryScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.utilities.ReflectionUtils;

import java.util.List;
import java.util.Map;

public class GalatiaQuestSkipPlugin extends BaseQuestSkipPlugin {

    // handled in settings file
    public void processMissionCompletionFlags(Map<String, Boolean> quests) {
    }

    public void processPeople(Map<String, Boolean> quests) {
        SectorEntityToken academy = Global.getSector().getEntityById("station_galatia_academy");
        MarketAPI market = academy.getMarket();
        MemoryAPI player = Global.getSector().getCharacterData().getMemoryWithoutUpdate();

        PersonAPI seb = Global.getSector().getImportantPeople().getPerson(People.SEBESTYEN);
        PersonAPI cour = Global.getSector().getImportantPeople().getPerson(People.COUREUSE);
        PersonAPI baird = Global.getSector().getImportantPeople().getPerson(People.BAIRD);
        PersonAPI garg = Global.getSector().getImportantPeople().getPerson(People.GARGOYLE);
        PersonAPI zal = Global.getSector().getImportantPeople().getPerson(People.ZAL);

        if (Boolean.TRUE.equals(quests.get("transverseJump"))) {
            market.getCommDirectory().getEntryForPerson(baird).setHidden(false);
            market.getCommDirectory().getEntryForPerson(seb).setHidden(false);
            seb.getMemoryWithoutUpdate().set("$metAlready", true);
            seb.getRelToPlayer().setLevel(RepLevel.FRIENDLY);

            // mark Transverse Jump as already done
            BaseMissionHub hub = (BaseMissionHub)BaseMissionHub.get(seb);
            hub.updateMissionCreatorsFromSpecs();
            List<HubMissionCreator> creators = (List<HubMissionCreator>) ReflectionUtils.getIncludingSuperclasses("creators", hub, hub.getClass());
            for (HubMissionCreator creator : creators) {
                if ("gaTJ".equals(creator.getSpecId())) {
                    creator.setNumCompleted(1);
                    break;
                }
            }
        }

        if (Boolean.TRUE.equals(quests.get("kallichore"))) {
            garg.getMarket().getCommDirectory().removePerson(garg);
            garg.getMarket().removePerson(garg);
            market.addPerson(garg);
            market.getCommDirectory().addPerson(garg);
            StartSetupPostTimePass.addStoryContact(People.ARROYO);
        }

        if (Boolean.TRUE.equals(quests.get("coureuse"))) {
            cour.getMarket().getCommDirectory().removePerson(cour);
            cour.getMarket().removePerson(cour);
            market.addPerson(cour);
            market.getCommDirectory().addPerson(cour);
            zal.getMemoryWithoutUpdate().set("$metAlready", true);
        }

        if (Boolean.TRUE.equals(quests.get("ziggurat"))) {
            seb.getRelToPlayer().setLevel(RepLevel.COOPERATIVE);
            cour.getMemoryWithoutUpdate().set("$askedAboutBaird", true);
            cour.getMemoryWithoutUpdate().set("$askedAboutGargoyle", true);
            StartSetupPostTimePass.addStoryContact(People.IBRAHIM);
        }

        if (Boolean.TRUE.equals(quests.get("atTheGates"))) {
            int cottonCount = 0;
            if (player.contains("$satWithCottonCount")) cottonCount = player.getInt("$satWithCottonCount");
            player.set("$satWithCottonCount", cottonCount + 1);

            seb.getMemoryWithoutUpdate().set("$gotGAATGpay", true);
            seb.getMemoryWithoutUpdate().set("$askedProvostUpset", true);
            garg.getMemoryWithoutUpdate().set("$askedProvostThrown", true);

            StartSetupPostTimePass.addStoryContact(People.HORUS_YARIBAY);
        }
    }

    public void processStoryProtection(Map<String, Boolean> quests) {
        String id;
        if (Boolean.TRUE.equals(quests.get("kallichore"))) {
            id = Missions.KALLICHORE;
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("port_tse", id);
            makeNonStoryCritical("new_maxios", id);
            makeNonStoryCritical("coatl", id);
        }

        if (Boolean.TRUE.equals(quests.get("coureuse"))) {
            id = Missions.COUREUSE;
            makeNonStoryCritical("laicaille_habitat", id);
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("fikenhild", id);
            makeNonStoryCritical("station_kapteyn", id);
        }

        if (Boolean.TRUE.equals(quests.get("ziggurat"))) {
            id = Missions.ZIGGURAT;
            makeNonStoryCritical("culann", id);
            makeNonStoryCritical("donn", id);
            makeNonStoryCritical("agreus", id);
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("port_tse", id);
        }

        if (Boolean.TRUE.equals(quests.get("atTheGates"))) {
            id = Missions.GATES;
            makeNonStoryCritical("kazeron", id);
            makeNonStoryCritical("chicomoztoc", id);
            makeNonStoryCritical("epiphany", id);
            makeNonStoryCritical("fikenhild", id);
            makeNonStoryCritical("kantas_den", id);
        }
    }

    public void processOtherSettings(Map<String, Boolean> quests) {
        if (Boolean.TRUE.equals(quests.get("transverseJump"))) {
            Global.getSector().getCharacterData().addAbility(Abilities.TRANSVERSE_JUMP);
            Global.getSector().getCharacterData().addAbility(Abilities.GRAVITIC_SCAN);
        }
        // Academy victory only available if doing from the start
        else {
            new AcademyStoryVictoryScript().init();
        }

        if (Boolean.TRUE.equals(quests.get("ziggurat"))) {
            StartSetupPostTimePass.generateAlphaSiteIntel();
        }
    }

    @Override
    public void onNewGameAfterTimePass() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;

        Map<String, Boolean> quests = chain.getEnabledQuestMap();
        processPeople(quests);
        processStoryProtection(quests);
        processOtherSettings(quests);
    }

    @Override
    public void init() {
        if (Global.getSettings().getBoolean("nex_skipStoryDefault")) {
            if (chain == null) return;
            for (QuestSkipEntry quest : chain.quests) {
                quest.isEnabled = true;
            }
        }
    }

    @Override
    public boolean shouldShow() {
        return ExerelinSetupData.getInstance().corvusMode;
    }
}
