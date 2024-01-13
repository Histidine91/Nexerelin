package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.RaidListener;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexUtilsMath;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Log4j
public class MilitaryAIModule extends StrategicAIModule implements RaidListener, ColonyPlayerHostileActListener, InvasionListener {

    @Getter public List<RaidRecord> recentRaids = new LinkedList<>();

    public MilitaryAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        super(ai, module);
    }

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
        float pad = 3, opad = 10;
        Color hl = Misc.getHighlightColor();
        String factionId = ai.getFactionId();

        float nextPad = opad;
        if (FleetPoolManager.USE_POOL) {
            float pool = FleetPoolManager.getManager().getCurrentPool(factionId);
            float poolMax = FleetPoolManager.getManager().getMaxPool(factionId);
            float poolIncr = FleetPoolManager.getManager().getPointsLastTick(ai.getFaction());
            String poolIncrStr = String.format("%.1f", poolIncr);
            tooltip.addPara(StrategicAI.getString("intelPara_fleetPool"), nextPad, hl, (int)pool + "", (int)poolMax + "", poolIncrStr);
            nextPad = pad;
        }
        {
            float points = InvasionFleetManager.getManager().getSpawnCounter(factionId);
            float pointsMax = InvasionFleetManager.getMaxInvasionPoints(ai.getFaction());
            float pointsIncr = InvasionFleetManager.getPointsLastTick(ai.getFaction());
            String pointsStr = Misc.getWithDGS(points);
            String pointsMaxStr = Misc.getWithDGS(pointsMax);
            String pointsIncrStr = "" + Math.round(pointsIncr);  //String.format("%.1f", pointsIncr);
            tooltip.addPara(StrategicAI.getString("intelPara_invasionPoints"), nextPad, hl, pointsStr, pointsMaxStr, pointsIncrStr);
        }

        super.generateReport(tooltip, holder, width);
    }

    @Override
    public void init() {
        super.init();
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public void advance(float days) {
        super.advance(days);

        Iterator<RaidRecord> raidIter = recentRaids.listIterator();
        while (raidIter.hasNext()) {
            RaidRecord record = raidIter.next();
            record.age += days;
            if (record.age > RaidRecord.MAX_AGE) {
                SAIUtils.logDebug(log, String.format("Removing raid record %s, age %s", record.name, record.age));
                raidIter.remove();
            }
        }
    }

    @Override
    public void reportRaidEnded(RaidIntel intel, FactionAPI attacker, FactionAPI defender, MarketAPI target, boolean success) {
        boolean logThis = ExerelinModPlugin.isNexDev && SAIConstants.DEBUG_LOGGING;
        if (logThis) {
            //log.info(ai.getFaction().getId() + " strategic AI reports raid ended");
        }
        if (logThis && defender == ai.getFaction()) {
            //log.info(String.format("Raid against %s ended: %s; attacker is AI %s, defender is AI %s", defender.getDisplayName(),
            //        intel.getName(), attacker == ai.faction, defender == ai.faction));
        }
        if (attacker != ai.faction && defender != ai.faction) {
            return;
        }

        MarketAPI origin = null;
        if (intel instanceof OffensiveFleetIntel) {
            origin = ((OffensiveFleetIntel)intel).getMarketFrom();
        } else {
            origin = intel.getOrganizeStage() != null ? intel.getOrganizeStage().getMarket() : null;
        }

        String name = intel.getName();
        String type = "raid";

        if (intel instanceof OffensiveFleetIntel) {
            OffensiveFleetIntel off = (OffensiveFleetIntel)intel;
            name = off.getBaseName();
            type = off.getType();
            if ("colony".equals(type)) return;
        }
        float impact = 1;
        if (target != null) {
            impact = target.getSize();
        }
        if ("invasion".equals(type))
            impact *= 2;
        else if ("satbomb".equals(type))
            impact *= 3;

        RaidRecord record = new RaidRecord(intel, name, type, attacker, defender, target, origin, success, impact);
        log.info("Adding recent raid to raid record: " + intel.getName());
        recentRaids.add(record);
    }

    public void reportPlayerHostileAction(String name, String type, MarketAPI target, float impactMult, boolean success) {
        FactionAPI attacker = PlayerFactionStore.getPlayerFaction();
        FactionAPI defender = target.getFaction();
        if (attacker != ai.faction && defender != ai.faction) {
            return;
        }

        float impact = 1;
        if (target != null) {
            impact = target.getSize();
        }
        impact *= impactMult;

        RaidRecord record = new RaidRecord(null, name, type, attacker, defender, target, null, success, impact);
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
        if (actionData.secret || !Global.getSector().getPlayerFleet().isTransponderOn()) return;
        String name = StrategicAI.getString("playerRaid", true);
        reportPlayerHostileAction(name, "raid", market, 1, true);
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {
        if (actionData.secret || !Global.getSector().getPlayerFleet().isTransponderOn()) return;
        String name = StrategicAI.getString("playerRaid", true);
        reportPlayerHostileAction(name, "raid", market, 1, true);
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        String name = StrategicAI.getString("playerBombardment", true);
        reportPlayerHostileAction(name, "bombardment", market, 1.5f, true);
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        String name = StrategicAI.getString("playerRaid", true);
        reportPlayerHostileAction(name, "bombardment", market, 3f, true);
    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {
        String name = StrategicAI.getString("playerInvasion", true);
        reportPlayerHostileAction(name, "invasion", market, 3f, success);
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {}

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {}

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved,
                                       boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {}

    public static class RaidRecord {
        public static final float MAX_AGE = 240;
        public static final float IMPACT_MULT_AT_MAX_AGE = 0.5f;

        @Nullable public transient RaidIntel intelTransient;
        @Nullable public Class intelClass;
        public String name;
        public FactionAPI attacker;
        @Nullable public FactionAPI defender;
        @Nullable public MarketAPI target;
        @Nullable public MarketAPI origin;
        public String type;
        public boolean success;
        public float impact;

        public float age;

        public RaidRecord(@Nullable RaidIntel intel, String name, String type, FactionAPI attacker, @Nullable FactionAPI defender,
                          @Nullable MarketAPI target, @Nullable MarketAPI origin, boolean success, float impact) {
            this.intelTransient = intel;
            if (intel != null) intelClass = intel.getClass();
            this.name = name;
            this.type = type;
            this.attacker = attacker;
            this.defender = defender;
            this.target = target;
            this.origin = origin;
            this.success = success;
            this.impact = impact;
        }

        public float getAgeAdjustedImpact() {
            float ageProportion = age/MAX_AGE;
            float mult = NexUtilsMath.lerp(IMPACT_MULT_AT_MAX_AGE, 1, ageProportion);
            return impact * mult;
        }
    }
}
