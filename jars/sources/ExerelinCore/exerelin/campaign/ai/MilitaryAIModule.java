package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.RaidListener;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsMath;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Log4j
public class MilitaryAIModule extends StrategicAIModule implements RaidListener {

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
            tooltip.addPara(StrategicAI.getString("intelPara_fleetPool"), nextPad, hl, (int)pool + "", (int)poolMax + "");
            nextPad = pad;
        }
        tooltip.addPara(StrategicAI.getString("intelPara_invasionPoints"), nextPad, hl, (int) InvasionFleetManager.getManager().getSpawnCounter(factionId) + "");
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

    public static class RaidRecord {
        public static final float MAX_AGE = 240;
        public static final float IMPACT_MULT_AT_MAX_AGE = 0.5f;

        public transient RaidIntel intelTransient;
        public Class intelClass;
        public String name;
        public FactionAPI attacker;
        public FactionAPI defender;
        public MarketAPI target;
        public MarketAPI origin;
        public String type;
        public boolean success;
        public float impact;

        public float age;

        public RaidRecord(RaidIntel intel, String name, String type, FactionAPI attacker, @Nullable FactionAPI defender,
                          @Nullable MarketAPI target, @Nullable MarketAPI origin, boolean success, float impact) {
            this.intelTransient = intel;
            intelClass = intel.getClass();
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
