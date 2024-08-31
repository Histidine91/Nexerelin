package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class CloseAdversariesConcern extends DiplomacyConcern {

    public static final int MAX_ADVERSARIES_TO_CHECK = 4;

    @Getter protected FactionAPI faction2;

    @Override
    public boolean generate() {
        if (NexUtilsFaction.isPirateFaction(ai.getFactionId())) return false;

        FactionAPI us = ai.getFaction();
        Set alreadyConcerned = getExistingConcernItems();

        WeightedRandomPicker<Pair<FactionAPI, FactionAPI>> picker = new WeightedRandomPicker<>();

        float ourStrength = getFactionStrength(us);

        boolean canPirate = false;  // NexConfig.allowPirateInvasions;

        List<Pair<FactionAPI, Float>> adversaries = new ArrayList<>();
        for (String factionId : getRelevantLiveFactionIds()) {
            if (!canPirate && NexUtilsFaction.isPirateFaction(factionId)) continue;
            if (us.isAtWorst(factionId, RepLevel.NEUTRAL)) continue;
            FactionAPI faction = Global.getSector().getFaction(factionId);
            float theirStrength = getFactionStrength(faction);
            if (theirStrength * 3 < ourStrength) continue;  // too weak to care

            adversaries.add(new Pair<>(faction, theirStrength));
        }
        Collections.sort(adversaries, new NexUtils.PairWithFloatComparator(true));

        int maxToCheck = Math.min(adversaries.size(), MAX_ADVERSARIES_TO_CHECK);
        for (int index1 = 0; index1 < maxToCheck; index1++) {
            FactionAPI faction1 = adversaries.get(index1).one;
            float str1 = adversaries.get(index1).two;
            for (int index2 = index1 + 1; index2 < adversaries.size(); index2++) {
                FactionAPI faction2 = adversaries.get(index2).one;
                float str2 = adversaries.get(index2).two;

                // perma-allied, why bother
                if (AllianceManager.areFactionsPermaAllied(faction1.getId(), faction2.getId()))
                    continue;

                Set<FactionAPI> set = new HashSet<>();
                set.add(faction1);
                set.add(faction2);
                if (alreadyConcerned.contains(set)) continue;

                RepLevel rel = faction1.getRelationshipLevel(faction2);
                if (rel.isAtBest(RepLevel.FAVORABLE)) continue;

                float weight = str1 + str2;
                Pair<FactionAPI, FactionAPI> pair = new Pair<>(faction1, faction2);
                picker.add(pair, weight);
            }
        }

        Pair<FactionAPI, FactionAPI> factions = picker.pick();
        if (factions == null) return false;
        float weight = picker.getWeight(factions);
        faction = factions.one;
        faction2 = factions.two;

        priority.modifyFlat("power", weight, StrategicAI.getString("statFactionPower", true));

        return faction != null;
    }

    @Override
    public void update() {
        if (shouldCancel()) {
            end();
            return;
        }

        float ourStrength = getFactionStrength(ai.getFaction());
        float theirStrength = getFactionStrength(faction);
        if (theirStrength * 3 < ourStrength) {
            end();
            return;
        }
        float theirStrength2 = getFactionStrength(faction2);
        if (theirStrength2 * 3 < ourStrength) {
            end();
            return;
        }
        priority.modifyFlat("power", theirStrength + theirStrength2, StrategicAI.getString("statFactionPower", true));

        super.update();
    }

    protected boolean shouldCancel() {
        FactionAPI us = ai.getFaction();
        if (us.isAtWorst(faction, RepLevel.NEUTRAL)) {
            return true;
        }
        else if (us.isAtWorst(faction2, RepLevel.NEUTRAL)) {
            return true;
        }
        else if (faction.isAtBest(faction2, RepLevel.FAVORABLE)) {
            return true;
        }
        else if (isFactionCommissionedPlayer(faction)) {
            return true;
        }
        else if (isFactionCommissionedPlayer(faction2)) {
            return true;
        }
        return false;
    }

    @Override
    public List<FactionAPI> getFactions() {
        return new ArrayList<>(Arrays.asList(new FactionAPI[] {getFaction(), faction2}));
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        if (faction == null) return null;
        String str = getDef().desc;
        str = StringHelper.substituteFactionTokens(str, faction);
        str = StringHelper.substituteFactionTokens(str, "other", faction2);
        LabelAPI label = tooltip.addPara(str, pad);
        label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), faction2.getDisplayNameWithArticleWithoutArticle());
        label.setHighlightColors(faction.getBaseUIColor(), faction2.getBaseUIColor());
        return label;
    }

    @Override
    public String getName() {
        return super.getName() + ", " + (faction2 != null ? faction2.getDisplayName() : "<error>");
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof CloseAdversariesConcern) {
            CloseAdversariesConcern cac = (CloseAdversariesConcern)otherConcern;
            return (cac.faction == this.faction && cac.faction2 == this.faction2)
                    || (cac.faction == this.faction2 && cac.faction2 == this.faction);
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<Set<FactionAPI>> factions = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            CloseAdversariesConcern cac = (CloseAdversariesConcern)concern;
            Set<FactionAPI> entry = new HashSet<>();
            entry.add(cac.faction);
            entry.add(cac.faction2);
            factions.add(entry);
        }
        return factions;
    }

    // not sure I want to use this
    protected float getPriorityMult(RepLevel level) {
        switch (level) {
            case SUSPICIOUS:
                return 0.75f;
            case INHOSPITABLE:
                return 1.25f;
            case HOSTILE:
            case VENGEFUL:
                return 1.5f;
            default:
                return 1;
        }
    }

}
