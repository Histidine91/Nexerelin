package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.bases.VayraRaiderActivityCondition;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.NexConfig;
import lombok.Getter;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class GeneralWarfareConcern extends BaseStrategicConcern {

    public static final float BASE_PRIORITY = 50;
    public static final float PRIORITY_PER_DAY = 0.5f;

    @Getter protected Set<String> hostileFactions = new HashSet<>();
    @Getter protected float priorityFromTime = 0;

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;
        update();

        return !hostileFactions.isEmpty();
    }

    @Override
    public void update() {

        hostileFactions.clear();
        hostileFactions.addAll(DiplomacyManager.getFactionsAtWarWithFaction(ai.getFactionId(), NexConfig.allowPirateInvasions, true, false));
        if (hostileFactions.isEmpty()) return;

        priority.modifyFlat("base", BASE_PRIORITY, StrategicAI.getString("statBase", true));

        if (isAwaitingAction()) {
            float days = ai.getDaysSinceLastUpdate();
            float strengthRatio = 1;
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
            if (brain != null) {
                float them = brain.getEnemyStrength();
                float us = brain.getOurStrength();
                if (us < 1) us = 1;
                strengthRatio = them/us;
                if (strengthRatio > 2) strengthRatio = 2;

                float priorityThisUpdate = PRIORITY_PER_DAY * days;
                Global.getLogger(this.getClass()).info(String.format("Priority increment this update: %.1f", priorityThisUpdate));
                priorityFromTime += priorityThisUpdate;
                priority.modifyFlat("priorityFromTime", priorityFromTime, StrategicAI.getString("statPriorityOverTime", true));
            }
        }

        reapplyPriorityModifiers();
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        String str = getDef().desc;
        Color hl = Misc.getHighlightColor();
        return tooltip.addPara(str, pad, hl, hostileFactions.size() + "");
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof GeneralWarfareConcern) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return !hostileFactions.isEmpty();
    }

    @Override
    public void notifyActionUpdate(StrategicAction action, StrategicActionDelegate.ActionStatus newStatus) {
        super.notifyActionUpdate(action, newStatus);
        if (newStatus == StrategicActionDelegate.ActionStatus.STARTING) {
            priorityFromTime -= action.getDef().cooldown;
            priority.modifyFlat("priorityFromTime", priorityFromTime, StrategicAI.getString("statPriorityOverTime", true));
        }
    }
}
