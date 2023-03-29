package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.MilitaryAIModule;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.ai.MilitaryAIModule.RaidRecord;

@Log4j
@Deprecated
public class RetaliationConcern extends BaseStrategicConcern {

    public static final int MAX_RAIDS_FOR_PICKER = 3;
    @Getter protected RaidRecord raid;

    @Override
    public boolean generate() {
        log.info("Trying to generate retaliation concern");
        List<Pair<RaidRecord, Float>> raidsSorted = new ArrayList<>();
        if (!(module instanceof MilitaryAIModule)) return false;

        List<RaidRecord> recentRaids = new ArrayList<>();

        Set<Object> alreadyConcernRaids = getExistingConcernItems();
        Map<String, Integer> numRaidsByFaction = new HashMap<>();

        for (RaidRecord raid : ((MilitaryAIModule)module).getRecentRaids()) {
            log.info("Checking raid " + raid.name);
            if (alreadyConcernRaids.contains(raid)) continue;
            if (raid.defender != ai.getFaction()) continue;
            if (!raid.attacker.isHostileTo(ai.getFaction())) continue;

            recentRaids.add(raid);
            NexUtils.modifyMapEntry(numRaidsByFaction, raid.attacker.getId(), 1);
        }

        for (RaidRecord raid : recentRaids) {
            // multiply each raid's impact by number of raids that faction has in total, so if someone is frequently attacking us we're extra mad
            float impact = raid.impact * numRaidsByFaction.get(raid.attacker.getId());
            raidsSorted.add(new Pair<>(raid, impact));
        }

        Collections.sort(raidsSorted, VALUE_COMPARATOR);
        int max = Math.min(raidsSorted.size(), MAX_RAIDS_FOR_PICKER);

        WeightedRandomPicker<Pair<RaidRecord, Float>> picker = new WeightedRandomPicker<>();
        for (int i=0; i<max; i++) {
            Pair<RaidRecord, Float> pair = raidsSorted.get(i);
            log.info("Adding raid record to picker: " + pair.one.name);
            picker.add(pair, pair.two);
        }
        Pair<RaidRecord, Float> toRespond = picker.pick();
        if (toRespond != null) {
            raid = toRespond.one;
            market = raid.target;
            faction = raid.attacker;
            log.info("Selected raid record: " + toRespond.one.name);
            priority.modifyFlat("rageValue", toRespond.two*5, StrategicAI.getString("statRaidAnger", true));
        }

        return raid != null;
    }

    @Override
    public void update() {
        // nothing to do here?
    }

    @Override
    public boolean isValid() {
        return false;
        //return raid != null && ai.getFaction().isHostileTo(faction);
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        // "We were recently attacked by the dastardly $faction, during the event $event."
        String str = getDef().desc;
        str += " " + StringHelper.getString("nex_strategicAI", "concernDesc_retaliation_" + (raid.success ? "success" : "fail"));
        str = StringHelper.substituteFactionTokens(str, faction);
        str = StringHelper.substituteToken(str, "$event", raid.name);
        Color hl = raid.attacker.getBaseUIColor();

        LabelAPI label = tooltip.addPara(str, pad, hl, NexUtilsFaction.getFactionShortName(raid.attacker), raid.name);
        label.setHighlightColors(hl, Misc.getHighlightColor());
        return label;
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof RetaliationConcern) {
            return ((RetaliationConcern) otherConcern).getRaid() == this.raid;
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<RaidRecord> raids = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            RetaliationConcern rc = (RetaliationConcern)concern;
            raids.add(rc.getRaid());
        }
        return raids;
    }

    public static final Comparator VALUE_COMPARATOR = new NexUtils.PairWithFloatComparator(true);
}
