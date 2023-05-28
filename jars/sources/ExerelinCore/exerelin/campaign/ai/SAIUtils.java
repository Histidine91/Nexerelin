package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import lombok.extern.log4j.Log4j;
import org.apache.log4j.Logger;

import java.util.Collection;

@Log4j
public class SAIUtils {

    /**
     * If {@code wantPositive} is true, high disposition increases the action priority. If it is false, low disposition increases priority.
     * @param aiFactionId
     * @param wantPositive
     * @param stat
     */
    public static void applyPriorityModifierForDisposition(String aiFactionId, String otherFactionId, boolean wantPositive, MutableStat stat) {

        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(aiFactionId);
        if (brain.getDisposition(aiFactionId) != null) {
            float disposition = brain.getDisposition(otherFactionId).disposition.getModifiedValue();
            boolean isPositive;
            if (disposition <= DiplomacyBrain.DISLIKE_THRESHOLD) isPositive = false;
            else if (disposition >= DiplomacyBrain.LIKE_THRESHOLD) isPositive = true;
            else return;

            //log.info(String.format("Is positive: %s, want positive: %s", isPositive, wantPositive));

            String desc = StrategicAI.getString(isPositive ? "statDispositionPositive" : "statDispositionNegative", true);
            float mult = isPositive == wantPositive ? SAIConstants.POSITIVE_DISPOSITION_MULT : SAIConstants.NEGATIVE_DISPOSITION_MULT;
            String source = isPositive ? "disposition_positive" : "disposition_negative";

            stat.modifyMult(source, mult, desc);
        }
    }

    public static void applyPriorityModifierForAlignment(String aiFactionId, MutableStat stat, Alliance.Alignment alignment) {
        float alignValue = NexConfig.getFactionConfig(aiFactionId).getAlignments().get(alignment).getModifiedValue();
        //log.info("Align value: " + alignValue);
        stat.modifyMult("alignment_" + alignment.getName(), 1 + SAIConstants.MAX_ALIGNMENT_MODIFIER_FOR_PRIORITY * alignValue,
                StrategicAI.getString("statAlignment", true) + ": " + Misc.ucFirst(alignment.getName()));
    }

    public static void applyPriorityModifierForTrait(String aiFactionId, MutableStat stat, String trait, float mult, boolean force) {
        if (!DiplomacyTraits.hasTrait(aiFactionId, trait) && !force)
            return;

        DiplomacyTraits.TraitDef traitDef = DiplomacyTraits.getTrait(trait);
        stat.modifyMult("trait_" + trait, mult, StrategicAI.getString("statTrait", true) + ": " + traitDef.name);
    }

    public static void applyPriorityModifierForTraits(Collection<String> tags, String aiFActionId, MutableStat stat) {
        for (String tag : tags) {
            if (tag.startsWith("trait_")) {
                String traitId = tag.substring("trait_".length());
                applyPriorityModifierForTrait(aiFActionId, stat, traitId, SAIConstants.TRAIT_POSITIVE_MULT, false);
            }
            else if (tag.startsWith("!trait_")) {
                String traitId = tag.substring("!trait_".length());
                applyPriorityModifierForTrait(aiFActionId, stat, traitId, SAIConstants.TRAIT_NEGATIVE_MULT, false);
            }
        }
    }

    public static void logDebug(Logger log, String msg) {
        boolean logThis = ExerelinModPlugin.isNexDev && SAIConstants.DEBUG_LOGGING;
        if (!logThis) return;
        log.info(msg);
    }

    public static void reportStrategyMeetingHeld(StrategicAI ai)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportStrategyMeetingHeld(ai);
        }
    }

    public static boolean allowConcern(StrategicAI ai, StrategicConcern concern)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            boolean allowed = x.allowConcern(ai, concern);
            if (!allowed) return false;
        }
        return true;
    }

    public static void reportConcernAdded(StrategicAI ai, StrategicConcern concern)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportConcernAdded(ai, concern);
        }
    }

    public static void reportConcernUpdated(StrategicAI ai, StrategicConcern concern)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportConcernUpdated(ai, concern);
        }
    }

    public static void reportConcernRemoved(StrategicAI ai, StrategicConcern concern)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportConcernRemoved(ai, concern);
        }
    }

    public static boolean allowAction(StrategicAI ai, StrategicAction action)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            boolean allowed = x.allowAction(ai, action);
            if (!allowed) return false;
        }
        return true;
    }

    public static void reportActionAdded(StrategicAI ai, StrategicAction action)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportActionAdded(ai, action);
        }
    }

    public static void reportActionPriorityUpdated(StrategicAI ai, StrategicAction action)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportActionPriorityUpdated(ai, action);
        }
    }

    public static void reportActionUpdated(StrategicAI ai, StrategicAction action, StrategicActionDelegate.ActionStatus status)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportActionUpdated(ai, action, status);
        }
    }

    public static void reportActionCancelled(StrategicAI ai, StrategicAction action)
    {
        for (StrategicAIListener x : Global.getSector().getListenerManager().getListeners(StrategicAIListener.class)) {
            x.reportActionCancelled(ai, action);
        }
    }
}
