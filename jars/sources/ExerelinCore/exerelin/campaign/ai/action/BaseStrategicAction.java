package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.SAIUtils;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;

public abstract class BaseStrategicAction implements StrategicAction {

    @Getter @Setter protected String id;
    @Getter @Setter protected StrategicConcern concern;
    protected StrategicAI ai;
    @Getter @Setter protected StrategicActionDelegate delegate;
    @Getter protected MutableStat priority = new MutableStat(0);
    @Getter protected StrategicActionDelegate.ActionStatus status;
    @Getter protected boolean isEnded;
    @Getter @Setter protected int meetingsSinceEnded;
    @Getter @Setter protected FactionAPI faction;

    @Override
    public StrategicAI getAI() {
        return ai;
    }

    @Override
    public void setAI(StrategicAI ai) {
        this.ai = ai;
    }

    @Override
    public boolean generate() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public void applyPriorityModifiers() {
        StrategicDefManager.StrategicActionDef def = getDef();
        if (def.hasTag(SAIConstants.TAG_MILITARY)) {
            SAIUtils.applyPriorityModifierForAlignment(ai.getFactionId(), priority, Alignment.MILITARIST);
        }
        if (def.hasTag(SAIConstants.TAG_DIPLOMACY)) {
            SAIUtils.applyPriorityModifierForAlignment(ai.getFactionId(), priority, Alignment.DIPLOMATIC);
        }

        if (faction != null) {
            if (def.hasTag(SAIConstants.TAG_FRIENDLY)) {
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), faction.getId(), true, priority);
            }
            if (def.hasTag(SAIConstants.TAG_UNFRIENDLY)) {
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), faction.getId(), false, priority);
            }
        }

        if (def.hasTag(SAIConstants.TAG_AGGRESSIVE)) {
            //SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.NEUTRALIST,
            //        SAIConstants.TRAIT_NEGATIVE_MULT, false);
            SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.PACIFIST,
                    SAIConstants.TRAIT_NEGATIVE_MULT, false);
        }
        SAIUtils.applyPriorityModifierForTraits(def.tags, ai.getFactionId(), priority);

        Float factionMult = NexConfig.getFactionConfig(ai.getFactionId()).strategyPriorityMults.get(id);
        if (factionMult != null) {
            priority.modifyMult("faction", factionMult, StrategicAI.getString("statFaction", true));
        }

        priority.modifyFlat("antiRepetition", -ai.getExecModule().getAntiRepetitionValue(id), StrategicAI.getString("statAntiRepetition", true));
    }


    @Override
    public float getPriorityFloat() {
        return priority.getModifiedValue();
    }

    /**
     * Note that unlike concerns, priority is determined _before_ action generation.
     */
    @Override
    public void updatePriority() {
        priority.modifyFlat("base", 100, StrategicAI.getString("statBase", true));
        applyPriorityModifiers();
        SAIUtils.reportActionPriorityUpdated(ai, this);
    }

    @Override
    public void initForConcern(StrategicConcern concern) {
        setAI(concern.getAI());
        setConcern(concern);
        setFaction(concern.getFaction());
    }

    @Override
    public void postGenerate() {
        delegate.setStrategicAction(this);
        status = delegate.getStrategicActionStatus();
    }

    @Override
    public void advance(float days) {
        StrategicActionDelegate.ActionStatus currStatus = delegate.getStrategicActionStatus();
        if (status != currStatus) {
            if (currStatus.ended) {
                end(currStatus);
                return;
            }
            status = currStatus;
            concern.notifyActionUpdate(this, status);
        }

        if (shouldAbort()) {
            abort();
            return;
        }
    }

    protected boolean shouldAbort() {
        FactionAPI faction = this.faction;
        if (faction == null || faction == ai.getFaction()) return false;

        int currRep = ai.getFaction().getRelationshipLevel(faction).ordinal();
        int maxRep = getMaxRelToTarget(faction).ordinal();
        int minRep = getMinRelToTarget(faction).ordinal();

        if (currRep > maxRep)
            return true;
        if (currRep < minRep)
            return true;

        return false;
    }

    @Override
    public void abort() {
        if (isEnded) return;
        if (delegate != null) delegate.abortStrategicAction();
        end(StrategicActionDelegate.ActionStatus.CANCELLED);
        SAIUtils.reportActionCancelled(ai, this);
    }

    @Override
    public void end(StrategicActionDelegate.ActionStatus status) {
        isEnded = true;
        this.status = status;
        concern.notifyActionUpdate(this, status);
    }

    @Override
    public String getName() {
        if (delegate != null && delegate != this) return delegate.getStrategicActionName();
        return getDef().name;
    }

    @Override
    public String getIcon() {
        if (delegate != null && delegate != this) return delegate.getIcon();
        return "graphics/icons/intel/reputation.png";
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        return false;
    }

    @Override
    public RepLevel getMinRelToTarget(FactionAPI target) {
        return RepLevel.VENGEFUL;
    }

    @Override
    public RepLevel getMaxRelToTarget(FactionAPI target) {
        if (getDef().hasTag("wartime")) return RepLevel.HOSTILE;
        if (getDef().hasTag("aggressive")) return RepLevel.NEUTRAL;
        return RepLevel.COOPERATIVE;
    }

    @Override
    public void createPanel(CustomPanelAPI outer, TooltipMakerAPI tooltip) {
        final float pad = 3, opad = 10;
        final StrategicAction action = this;

        TooltipMakerAPI iwt = tooltip.beginImageWithText(this.getIcon(), 32);

        iwt.addPara(getName(), status.color, 0);

        //createTooltipDesc(iwt, holder, 3);

        int prioVal = (int)getPriorityFloat();
        String prio = String.format(StringHelper.getString("priority", true) + ": %s", prioVal);
        LabelAPI prioLbl = iwt.addPara(prio, 0, Misc.getHighlightColor(), prioVal + "");

        if (ai.getDelegateAsIntel(this) != null) {
            //float currHeight = iwt.getPosition().getHeight();
            ButtonAPI toAction = iwt.addButton(StrategicAI.getString("btnGoIntelShort"), this, 64, 0, 0);
            toAction.getPosition().inTR(0, 0);// .rightOfTop((UIComponentAPI) prioLbl, opad);
            toAction.getPosition().setSize(64, 18);
            iwt.setForceProcessInput(true);
            //iwt.getPosition().setSize(iwt.getPosition().getWidth(), currHeight);
        }

        tooltip.addImageWithText(pad);
        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(action.getStatus().getStatusString(), 0, action.getStatus().color, action.getStatus().getStatusName(true));
                if (!action.getStatus().ended) {
                    tooltip.addPara(StrategicAI.getString("actionStatusETA"), pad, Misc.getHighlightColor(),
                            (int)action.getDelegate().getStrategicActionDaysRemaining() + "");
                }
                tooltip.addPara(StringHelper.getString("priority", true), opad);
                tooltip.addStatModGrid(360, 60, 10, 0, priority,
                        true, NexUtils.getStatModValueGetter(true, 0));
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public StrategicDefManager.StrategicActionDef getDef() {
        return StrategicDefManager.getActionDef(id);
    }

    @Override
    public int compareTo(StrategicAction other) {
        return Float.compare(other.getPriorityFloat(), this.getPriorityFloat());
    }
}
