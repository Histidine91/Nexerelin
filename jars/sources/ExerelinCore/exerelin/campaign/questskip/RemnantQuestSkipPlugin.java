package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMission;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.loading.PersonMissionSpec;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.intel.SpecialContactIntel;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;
import exerelin.campaign.intel.missions.remnant.RemnantSalvation;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Log4j
public class RemnantQuestSkipPlugin extends BaseQuestSkipPlugin {

    @Override
    public void onNewGameAfterTimePass() {
        if (!ExerelinSetupData.getInstance().corvusMode) return;

        Map<String, Boolean> quests = chain.getEnabledQuestMap();
        FactionAPI remnants = Global.getSector().getFaction(Factions.REMNANTS);
        PersonAPI dissonant = null;

        if (chain.isQuestEnabled("nex_captiveCores", quests)) {
            addDissonant();
            dissonant = RemnantQuestUtils.getDissonant();
            dissonant.getRelToPlayer().setRel(0.07f);
            setMissionDone("nex_remM1", dissonant);
            remnants.setRelationship(Factions.PLAYER, RepLevel.INHOSPITABLE);
            remnants.setRelationship(Factions.PLAYER, -0.49f);
        }

        if (chain.isQuestEnabled("nex_fragments", quests)) {
            setRepLevelFixed(RemnantQuestUtils.getDissonant(), RepLevel.WELCOMING);
            addRandomOmegaWeapons();
            setMissionDone("nex_remFragments", dissonant);
            dissonant.getMemoryWithoutUpdate().set("$nex_convo1seen", true);
        }
        if (chain.isQuestEnabled("nex_lostSci", quests)) {
            setRepLevelFixed(RemnantQuestUtils.getDissonant(), RepLevel.WELCOMING);
            setMissionDone("nex_remLostSci", dissonant);
            dissonant.getMemoryWithoutUpdate().set("$nex_convo1Seen", true);
        }

        if (chain.isQuestEnabled("nex_showdown", quests)) {
            setRepLevelFixed(RemnantQuestUtils.getDissonant(), RepLevel.FRIENDLY);
            setMissionDone("nex_remBrawl", dissonant);
            dissonant.setImportance(PersonImportance.VERY_HIGH);
            remnants.setRelationship(Factions.PLAYER, RepLevel.SUSPICIOUS);
            dissonant.getMemoryWithoutUpdate().set("$nex_convo2Seen", true);
        }

        if (chain.isQuestEnabled("nex_salvation", quests)) {
            setRepLevelFixed(RemnantQuestUtils.getDissonant(), RepLevel.COOPERATIVE);
            PersonAPI towering = RemnantQuestUtils.getOrCreateTowering();
            RemnantQuestUtils.enhanceTowering();
            addKnight();

            boolean haveBoggled = Global.getSettings().getModManager().isModEnabled("Terraforming & Station Construction");
            CargoAPI player = Global.getSector().getPlayerFleet().getCargo();
            player.addSpecial(new SpecialItemData(haveBoggled ? "boggled_planetkiller" : Items.PLANETKILLER, null), 1f);

            //addSilverlightOmegaWeapons();
            String variantId = "nex_silverlight_Hull";
            String variantId2 = "nex_silverlight_Ascendant";
            FleetMemberAPI member = BaseQuestSkipPlugin.addShipToPlayerFleet(variantId);

            // copy over Ascendant variant, except for the shards
            ShipVariantAPI temp = Global.getSettings().getVariant(variantId2).clone();
            temp.getStationModules().clear();
            temp.setSource(VariantSource.REFIT);
            member.setVariant(temp, false, true);

            ShipVariantAPI variant = member.getVariant();
            variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE);
            variant.addTag(Tags.SHIP_UNIQUE_SIGNATURE);

            setMissionDone("nex_remSalvation", dissonant);
            Global.getSector().getMemoryWithoutUpdate().set("$nex_remSalvation_missionDone", true);

            remnants.setRelationship(Factions.PLAYER, RepLevel.NEUTRAL);
        }
    }

    protected void addDissonant() {
        MarketAPI market = Global.getSector().getEconomy().getMarket("nex_prismFreeport");
        if (market == null) {
            List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.INDEPENDENT, true);
            if (!markets.isEmpty()) market = NexUtilsFaction.getFactionMarkets(Factions.INDEPENDENT, true).get(0);
        }
        if (market == null) market = Global.getSector().getEconomy().getMarketsCopy().get(0);

        PersonAPI dissonant = RemnantQuestUtils.createDissonant(market);
        dissonant.getName().setFirst(RemnantQuestUtils.getString("dissonantName1"));
        dissonant.getName().setLast(RemnantQuestUtils.getString("dissonantName2"));
        market.getCommDirectory().addPerson(dissonant);
        BaseMissionHub.set(dissonant, new BaseMissionHub(dissonant));
        dissonant.getMemoryWithoutUpdate().set(BaseMissionHub.NUM_BONUS_MISSIONS, 1);
        SpecialContactIntel intel = new SpecialContactIntel(dissonant, market);
        Global.getSector().getIntelManager().addIntel(intel, true, null);
    }

    protected void addKnight() {
        Random random = new Random(NexUtils.getStartingSeed());
        PersonMissionSpec spec = Global.getSettings().getMissionSpec("nex_remSalvation");
        PersonAPI dissonant = RemnantQuestUtils.getDissonant();
        HubMission mission = spec.createMission();
        try {
            mission.setPersonOverride(dissonant);
            mission.setGenRandom(random);
            mission.createAndAbortIfFailed(dissonant.getMarket(), false);

            MarketAPI target = ((RemnantSalvation)mission).getTarget();
            if (target == null) target = Global.getSector().getEconomy().getMarket("gilead");
            if (target == null) target = NexUtilsFaction.getFactionMarkets(Factions.LUDDIC_CHURCH, true).get(0);
            if (target == null) return;

            PersonAPI knight = RemnantQuestUtils.getOrCreateArgent();
            target.addPerson(knight);
            target.getCommDirectory().addPerson(knight);
        } catch (Exception ex) {
            log.warn("Failed to add knight to market", ex);
        }
        mission.abort();
    }

    protected void addRandomOmegaWeapons() {
        Random random = new Random(NexUtils.getStartingSeed());
        List<SalvageEntityGenDataSpec.DropData> drop = new ArrayList<>();

        SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
        d.chances = 3;
        d.group = "omega_weapons_small";
        drop.add(d);

        d = new SalvageEntityGenDataSpec.DropData();
        d.chances = 1;
        d.group = "omega_weapons_medium";
        drop.add(d);

        CargoAPI temp = SalvageEntity.generateSalvage(random, 1, 1, 1, null, drop);
        Global.getSector().getPlayerFleet().getCargo().addAll(temp);
    }

    protected void addSilverlightOmegaWeapons() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        ShipVariantAPI variant = Global.getSettings().getVariant("nex_silverlight_Ascendant");
        for (String slot : variant.getNonBuiltInWeaponSlots()) {
            String weaponId = variant.getWeaponId(slot);
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
            if (!spec.hasTag("omega")) continue;
            cargo.addWeapons(weaponId, 1);
        }
    }
}
