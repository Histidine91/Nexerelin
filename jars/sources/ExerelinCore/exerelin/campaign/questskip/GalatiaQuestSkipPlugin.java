package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.academy.GAFCReplaceArchon;
import com.fs.starfarer.api.impl.campaign.missions.academy.GAFindingCoureuse;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.loading.PersonMissionSpec;
import exerelin.campaign.AcademyStoryVictoryScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.utilities.NexUtils;

import java.util.Map;
import java.util.Random;

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
        PersonAPI cotton = Global.getSector().getImportantPeople().getPerson(People.COTTON);

        boolean tj = chain.isQuestEnabled("transverseJump", quests);
        boolean kallichore = chain.isQuestEnabled("kallichore", quests);
        boolean coureuse = chain.isQuestEnabled("coureuse", quests);

        if (tj) {
            market.getCommDirectory().getEntryForPerson(baird).setHidden(false);
            market.getCommDirectory().getEntryForPerson(seb).setHidden(false);
            seb.getMemoryWithoutUpdate().set("$metAlready", true);
            setRepLevelFixed(seb, RepLevel.FRIENDLY);

            setMissionDone("gaTJ", seb);
            if (!kallichore && !coureuse) {
                Global.getSector().getMemoryWithoutUpdate().set("$asebSayBairdWantsToTalk", true);  // enable meeting with Baird
            }
        }

        if (kallichore) {
            garg.getMarket().getCommDirectory().removePerson(garg);
            garg.getMarket().removePerson(garg);
            market.addPerson(garg);
            market.getCommDirectory().addPerson(garg);
            StartSetupPostTimePass.addStoryContact(People.ARROYO);
        }

        if (coureuse) {
            cour.getMarket().getCommDirectory().removePerson(cour);
            cour.getMarket().removePerson(cour);
            market.addPerson(cour);
            market.getCommDirectory().addPerson(cour);
            zal.getMemoryWithoutUpdate().set("$metAlready", true);
            new GAFCReplaceArchon().run();  // archon replacement

            Global.getSector().getMemoryWithoutUpdate().set("$gaFC_pickedBranchFikenhild", true);
        }

        if (kallichore || coureuse) {
            setRepLevelFixed(seb, RepLevel.COOPERATIVE);
        }

        // if we skipped either KA or FC but not the other, start that other mission
        if (kallichore && !coureuse) {
            HubMission mission = startAcademyMission("gaFC");
            MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
            global.set("$gaFC_pickedBranchFikenhild", true);
            GAFindingCoureuse fc = (GAFindingCoureuse)mission;
            fc.checkStageChangesAndTriggers(null, null);
        }
        if (!kallichore && coureuse) {
            startAcademyMission("gaKA");
        }

        if (chain.isQuestEnabled("ziggurat", quests)) {
            seb.getRelToPlayer().setRel(1);
            cour.getMemoryWithoutUpdate().set("$askedAboutBaird", true);
            cour.getMemoryWithoutUpdate().set("$askedAboutGargoyle", true);
            cour.getRelToPlayer().adjustRelationship(0.05f, RepLevel.COOPERATIVE);
            //StartSetupPostTimePass.addStoryContact(People.IBRAHIM);   // normally she only becomes available after returning the Hamatsu, which isn't skipped
        }

        if (chain.isQuestEnabled("atTheGates", quests)) {
            int cottonCount = 0;
            if (player.contains("$satWithCottonCount")) cottonCount = player.getInt("$satWithCottonCount");
            player.set("$satWithCottonCount", cottonCount + 1);

            seb.getMemoryWithoutUpdate().set("$gotGAATGpay", true);
            seb.getMemoryWithoutUpdate().set("$askedProvostUpset", true);
            garg.getMemoryWithoutUpdate().set("$askedProvostThrown", true);
            cour.getMemoryWithoutUpdate().set("$askedAboutZal", true);
            cour.getRelToPlayer().adjustRelationship(0.25f, RepLevel.COOPERATIVE);
            cour.getMarket().getCommDirectory().getEntryForPerson(cour).setHidden(true);
            cotton.getRelToPlayer().setRel(0.01f);
            zal.getRelToPlayer().setRel(0.2f);
            Global.getSector().getImportantPeople().getPerson(People.KANTA).getRelToPlayer().setRel(-0.15f);

            scanGateInSystem(Global.getSector().getStarSystem("Galatia"));
            scanGateInSystem(Global.getSector().getStarSystem("Magec"));

            //StartSetupPostTimePass.addStoryContact(People.HORUS_YARIBAY); // go talk to him yourself

            Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(Items.JANUS, null), 1);
        }
    }

    public void scanGateInSystem(StarSystemAPI system) {
        if (system == null) return;
        for (SectorEntityToken gate : system.getEntitiesWithTag(Tags.GATE)) {
            gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
            GateCMD.notifyScanned(gate);
        }
    }

    public void processStoryProtection(Map<String, Boolean> quests) {
        String id;
        if (chain.isQuestEnabled("kallichore", quests)) {
            id = Missions.KALLICHORE;
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("port_tse", id);
            makeNonStoryCritical("new_maxios", id);
            makeNonStoryCritical("coatl", id);
        }

        if (chain.isQuestEnabled("coureuse", quests)) {
            id = Missions.COUREUSE;
            makeNonStoryCritical("laicaille_habitat", id);
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("fikenhild", id);
            makeNonStoryCritical("station_kapteyn", id);
        }

        if (chain.isQuestEnabled("ziggurat", quests)) {
            id = Missions.ZIGGURAT;
            makeNonStoryCritical("culann", id);
            makeNonStoryCritical("donn", id);
            makeNonStoryCritical("agreus", id);
            makeNonStoryCritical("eochu_bres", id);
            makeNonStoryCritical("port_tse", id);
        }

        if (chain.isQuestEnabled("atTheGates", quests)) {
            id = Missions.GATES;
            makeNonStoryCritical("kazeron", id);
            makeNonStoryCritical("chicomoztoc", id);
            makeNonStoryCritical("epiphany", id);
            makeNonStoryCritical("fikenhild", id);
            makeNonStoryCritical("kantas_den", id);
        }
    }

    public void processOtherSettings(Map<String, Boolean> quests) {
        if (chain.isQuestEnabled("transverseJump", quests)) {
            Global.getSector().getCharacterData().addAbility(Abilities.TRANSVERSE_JUMP);
            Global.getSector().getCharacterData().addAbility(Abilities.GRAVITIC_SCAN);
        }
        // Academy victory only available if doing from the start
        else {
            new AcademyStoryVictoryScript().init();
        }

        if (chain.isQuestEnabled("ziggurat", quests)) {
            StartSetupPostTimePass.generateAlphaSiteIntel();
        }
    }

    @Override
    public void applyMemKeys() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;
        super.applyMemKeys();
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
                if (quest.id.equals("abyssalSpaceOddity")) continue;
                quest.isEnabled = true;
            }
        }
    }

    public HubMission startAcademyMission(String missionId) {
        PersonAPI person = Global.getSector().getImportantPeople().getPerson(People.BAIRD);
        PersonMissionSpec spec = Global.getSettings().getMissionSpec(missionId);
        HubMission mission = spec.createMission();
        mission.setPersonOverride(person);
        mission.setGenRandom(new Random(NexUtils.getStartingSeed()));
        mission.createAndAbortIfFailed(person.getMarket(), false);
        mission.accept(null, null);
        return mission;
    }

    @Override
    public boolean shouldShow(QuestSkipEntry entry) {
        if (entry.id.equals("abyssalSpaceOddity")) return true;
        return ExerelinSetupData.getInstance().corvusMode;
    }
}
