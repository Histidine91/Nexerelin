package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.MilitaryAIModule;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.ai.MilitaryAIModule.RaidRecord;

/**
 * Concern that makes us retaliate against enemy raids. V2 is basically a persistent version that uses all the recent raids, not just the one actually selected.
 */
@Log4j
public class RetaliationConcernV2 extends BaseStrategicConcern {

    public static final float RETALIATION_PRIO_MULT = 15;

    @Getter protected RaidRecord topRaid;

    @Override
    public boolean generate() {
        if (!getExistingConcernItems().isEmpty()) return false;

        //log.info("Trying to generate retaliation concern");
        if (!(module instanceof MilitaryAIModule)) return false;

        update();
        return topRaid != null;
    }

    @Override
    public void update() {
        List<Pair<RaidRecord, Float>> raidsSorted = new ArrayList<>();
        List<RaidRecord> recentRaids = new ArrayList<>();
        Map<String, Integer> numRaidsByFaction = new HashMap<>();

        if (ExerelinModPlugin.isNexDev && SAIConstants.DEBUG_LOGGING) {
            if (!recentRaids.isEmpty())
                log.info(ai.getFaction().getId() + " update for retaliation concern: " + ((MilitaryAIModule)module).getRecentRaids().size());
        }

        float totalImpact = 0;
        for (RaidRecord raid : ((MilitaryAIModule)module).getRecentRaids()) {
            //log.info("Checking raid " + raid.name);
            if (raid.defender != ai.getFaction()) continue;
            if (!raid.attacker.isHostileTo(ai.getFaction())) continue;
            if (!SectorManager.isFactionAlive(raid.attacker.getId())) continue;

            recentRaids.add(raid);
            NexUtils.modifyMapEntry(numRaidsByFaction, raid.attacker.getId(), 1);
            totalImpact += raid.getAgeAdjustedImpact();
        }
        if (recentRaids.isEmpty()) {
            // don't end concern yet if we have a raid en route
            if (currentAction == null || currentAction.isEnded()) {
                end();
            }
            return;
        }

        topRaid = null;

        for (RaidRecord raid : recentRaids) {
            // multiply each raid's impact by number of raids that faction has in total, so if someone is frequently attacking us we're extra mad
            float adjImpact = raid.getAgeAdjustedImpact() * (1 + 0.25f * numRaidsByFaction.get(raid.attacker.getId()));
            raidsSorted.add(new Pair<>(raid, adjImpact));
        }

        Collections.sort(raidsSorted, VALUE_COMPARATOR);

        topRaid = raidsSorted.get(0).one;
        if (topRaid.origin != null && topRaid.origin.getFaction().isHostileTo(ai.getFaction())) {
            market = topRaid.origin;
        }
        faction = topRaid.attacker;
        float adjustedImpact = raidsSorted.get(0).two;
        log.info("Selected raid record: " + topRaid.name + ", " + adjustedImpact);
        priority.modifyFlat("rageValue", (adjustedImpact + totalImpact)*RETALIATION_PRIO_MULT, StrategicAI.getString("statRaidAnger", true));
    }

    @Override
    public boolean isValid() {
        return topRaid != null;
    }

    @Override
    public FactionAPI getFaction() {
        if (topRaid != null) return topRaid.attacker;
        return super.getFaction();
    }

    @Override
    public void advance(float days) {
        super.advance(days);
        // refresh if we're no longer hostile to the top attacker
        if (faction != null && !faction.isHostileTo(ai.getFaction())) {
            update();
        }
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        // "We were attacked by our enemies, the most recent of which was the $event by the dastardly $faction."
        String str = getDef().desc;
        str += " " + StringHelper.getString("nex_strategicAI", "concernDesc_retaliation_" + (topRaid.success ? "success" : "fail"));
        str = StringHelper.substituteFactionTokens(str, faction);
        str = StringHelper.substituteToken(str, "$event", topRaid.name);
        Color hl = topRaid.attacker.getBaseUIColor();

        LabelAPI label = tooltip.addPara(str, pad, hl, topRaid.name, NexUtilsFaction.getFactionShortName(topRaid.attacker));
        label.setHighlightColors(Misc.getHighlightColor(), hl);
        return label;
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof RetaliationConcernV2) {
            return true;    // ((RetaliationConcern) otherConcern).getRaid() == this.raid;
        }
        return false;
    }

    @Override
    public void notifyActionUpdate(StrategicAction action, StrategicActionDelegate.ActionStatus newStatus) {
        super.notifyActionUpdate(action, newStatus);
        if (newStatus == StrategicActionDelegate.ActionStatus.SUCCESS) {
            ((MilitaryAIModule)module).getRecentRaids().remove(topRaid);
        }
    }

    @Override
    public Set getExistingConcernItems() {
        return new HashSet<>(getExistingConcernsOfSameType());
    }

    public static final Comparator VALUE_COMPARATOR = new NexUtils.PairWithFloatComparator(true);
}
