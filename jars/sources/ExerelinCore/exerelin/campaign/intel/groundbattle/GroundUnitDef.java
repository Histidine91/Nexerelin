package exerelin.campaign.intel.groundbattle;

import lombok.Getter;

import java.util.*;

public class GroundUnitDef {

    //public static final String DEF_FILE = null;
    @Getter protected static final List<GroundUnitDef> UNIT_DEFS = new ArrayList<>();
    @Getter protected static final Map<String, GroundUnitDef> UNIT_DEFS_BY_ID = new HashMap<>();

    public static final String MARINE = "marine";
    public static final String HEAVY = "heavy";
    public static final String MILITIA = "militia";
    public static final String REBEL = "rebel";

    public static final String TAG_MILITIA = "militia";

    public String id;
    public String name;
    public GroundUnit.ForceType type;
    public boolean playerCanCreate;
    public float strength;
    public float unitSizeMult;
    public float moraleMult;
    public float dropCostMult;
    public float offensiveStrMult;
    public float crampedStrMult;
    public Set<String> tags = new HashSet<>();
    public GroundUnitCommodity personnel;
    public GroundUnitCommodity equipment;

    public GroundUnitDef(String id, String name, GroundUnit.ForceType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public static void addUnitDef(GroundUnitDef def) {
        UNIT_DEFS.add(def);
        UNIT_DEFS_BY_ID.put(def.id, def);
    }

    public static GroundUnitDef getUnitDef(String id) {
        return UNIT_DEFS_BY_ID.get(id);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public static class GroundUnitCommodity {
        public String commodityId;
        public String crewReplacerJobId;
        public int mult;

        public GroundUnitCommodity(String commodityId, String crewReplacerJobId, int mult) {
            this.commodityId = commodityId;
            this.crewReplacerJobId = crewReplacerJobId;
            this.mult = mult;
        }
    }
}
