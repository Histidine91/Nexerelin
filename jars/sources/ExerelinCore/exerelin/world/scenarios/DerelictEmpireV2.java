package exerelin.world.scenarios;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.ExerelinConstants;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtils;
import exerelin.world.ExerelinProcGen;
import exerelin.world.NexMarketBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DerelictEmpireV2 extends Scenario {

    public static boolean USE_OWN_ECON_GROUP = true;    // false kills their economy due to hostilities penalty to access
    public static final String ECON_GROUP_ID = "nex_derelictEmpire";

    @Override
    public void afterEconomyLoad(SectorAPI sector) {
        if (!SectorManager.getManager().isCorvusMode()) {
            Scenario DEv1 = new DerelictEmpire();
            DEv1.afterEconomyLoad(sector);
        }
    }

    @Override
    public void afterTimePass(SectorAPI sector) {

        String factionId = "nex_derelict";
        FactionAPI derelict = sector.getFaction(factionId);
        //boolean skip = false;

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction == derelict || faction.getId().equals(Factions.DERELICT)) continue;
            derelict.setRelationship(faction.getId(), DiplomacyManager.STARTING_RELATIONSHIP_HOSTILE);
        }

        if (!SectorManager.getManager().isCorvusMode()) {
            Scenario DEv1 = new DerelictEmpire();
            DEv1.afterTimePass(sector);
            return;
        }

        Random random = new Random(NexUtils.getStartingSeed());

        List<MarketAPI> transferred = new ArrayList<>();

        // give the free colonies at start to derelict faction
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (!market.getMemoryWithoutUpdate().getBoolean("$nex_randomStartingColony")) continue;
            if (market.getContainingLocation().hasTag(Tags.THEME_CORE)) continue;
            if (market.getContainingLocation().hasTag(Tags.THEME_CORE_POPULATED)) continue; // workaround for Legio system which lacks the pure core tag

            /*
            if (skip) {
                skip = false;
                continue;
            }
            */

            SectorManager.transferMarket(market, derelict, market.getFaction(),
                    false, false, null, 0, true);
            PersonAPI currAdmin = market.getAdmin();
            market.setAdmin(null);
            ColonyManager.replaceDisappearedAdmin(market, currAdmin);
            // unlike DE v1 we don't keep the human factions as market's original faction, since we don't need to incentivize conquering derelict markets
            market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, factionId);

            if (USE_OWN_ECON_GROUP) {
                market.setEconGroup(ECON_GROUP_ID);
            }

            if (random.nextBoolean()) {
                ColonyManager.getManager().upsizeMarket(market, false);
                //ColonyManager.buildIndustries(market, true, true, true, random);  // leave some space for market builder to work
            }

            transferred.add(market);
            //skip = true;
        }

        // apply industries and bonuses from the faction file
        if (USE_OWN_ECON_GROUP) {
            List<ExerelinProcGen.ProcGenEntity> entities = new ArrayList<>();
            for (MarketAPI market : transferred) {
                ExerelinProcGen.ProcGenEntity ent = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
                entities.add(ent);
            }

            ExerelinProcGen procGen = new ExerelinProcGen();
            NexMarketBuilder builder = new NexMarketBuilder(procGen);
            for (ExerelinProcGen.ProcGenEntity ent : entities) {
                procGen.getProcGenEntitiesByToken().put(ent.entity, ent);
            }
            builder.getMarketsByFactionId().put(factionId, entities);

            builder.addKeyIndustriesForFaction(factionId);
            builder.addFactionBonuses(factionId);
            for (ExerelinProcGen.ProcGenEntity ent : entities) {
                NexMarketBuilder.addMilitaryStructures(ent, true, random);
            }
        }

        sector.getListenerManager().addListener(new DerelictEmpireCaptureListener(), false);
    }

    @Override
    public void onSelect(InteractionDialogAPI dialog) {
        ExerelinSetupData setup = ExerelinSetupData.getInstance();
        if (setup.randomColonies <= 0) {
            setup.randomColonies = 24;
        }
    }
}
