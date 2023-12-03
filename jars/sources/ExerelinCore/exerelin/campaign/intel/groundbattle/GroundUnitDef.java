package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GroundUnitDef implements Comparable<GroundUnitDef> {

    //public static final String DEF_FILE = null;
    @Getter protected static final List<GroundUnitDef> UNIT_DEFS = new ArrayList<>();
    @Getter protected static final Map<String, GroundUnitDef> UNIT_DEFS_BY_ID = new HashMap<>();

    static {
        boolean bla = GBDataManager.abilityDefs.isEmpty(); // trigger GBDataManager's definition load in static block
    }

    public static final String MARINE = "marine";
    public static final String HEAVY = "heavy";
    public static final String MILITIA = "militia";
    public static final String REBEL = "rebel";

    public static final String TAG_MILITIA = "militia";

    public String id;
    public String name;
    public GroundUnit.ForceType type;
    public String pluginClass;
    public String playerMemKeyToShow;
    public boolean playerCanCreate;
    public float strength;
    public float unitSizeMult;
    public float moraleMult;
    public float dropCostMult;
    public float offensiveStrMult;
    public float damageTakenMult;
    public float crampedStrMult;
    public String sprite;
    public Set<String> tags = new HashSet<>();
    public float sortOrder;
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

    public String getCommodityIdForIcon() {
        if (equipment != null) return equipment.commodityId;
        if (personnel != null) return personnel.commodityId;
        return Commodities.MARINES;
    }

    public String getSprite() {
        if (sprite != null) return sprite;
        return GroundBattleIntel.getCommoditySprite(getCommodityIdForIcon());
    }

    public boolean shouldShow() {
        if (playerMemKeyToShow == null) return true;
        return Global.getSector().getCharacterData().getMemoryWithoutUpdate().getBoolean(playerMemKeyToShow);
    }

    @Override
    public int compareTo(@NotNull GroundUnitDef other) {
        return Float.compare(sortOrder, other.sortOrder);
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
