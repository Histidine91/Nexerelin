package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.RaidListener;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MilitaryAIModule extends StrategicAIModule implements RaidListener {

    @Getter public List<RaidRecord> recentRaids = new LinkedList<>();

    public MilitaryAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        super(ai, module);
    }

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder) {
        float pad = 3;
        float opad = 10;
        //tooltip.addPara("TBD", opad);
        for (StrategicConcern concern : currentConcerns) {
            concern.createTooltip(tooltip, holder, pad);
        }
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
                raidIter.remove();
            }
        }
    }

    @Override
    public void reportRaidEnded(RaidIntel intel, FactionAPI attacker, FactionAPI defender, MarketAPI target, boolean success) {
        if (attacker != ai.faction || defender != ai.faction) return;

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

        RaidRecord record = new RaidRecord(intel, name, type, attacker, defender, target, success, impact);
        recentRaids.add(record);
    }

    public static class RaidRecord {
        public static final float MAX_AGE = 120;

        public transient RaidIntel intelTransient;
        public Class intelClass;
        public String name;
        public FactionAPI attacker;
        public FactionAPI defender;
        public MarketAPI target;
        public String type;
        public boolean success;
        public float impact;

        public float age;

        public RaidRecord(RaidIntel intel, String name, String type, FactionAPI attacker,
                          @Nullable FactionAPI defender, @Nullable MarketAPI target, boolean success, float impact) {
            this.intelTransient = intel;
            intelClass = intel.getClass();
            this.name = name;
            this.type = type;
            this.attacker = attacker;
            this.defender = defender;
            this.target = target;
            this.success = success;
            this.impact = impact;
        }
    }
}
