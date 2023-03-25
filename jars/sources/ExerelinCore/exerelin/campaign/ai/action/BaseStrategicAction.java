package exerelin.campaign.ai.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.SAIUtils;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits;
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

    /**
     * Called before action generation and every half-day or so.
     * @return
     */
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
            //log.info("Applying diplomacy modifier for concern " + this.getName());
            SAIUtils.applyPriorityModifierForAlignment(ai.getFactionId(), priority, Alignment.DIPLOMATIC);
        }
        if (def.hasTag(SAIConstants.TAG_ECONOMY)) {
            SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.MONOPOLIST, 1.4f, false);
        }
        if (def.hasTag(SAIConstants.TAG_COVERT)) {
            SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.DEVIOUS, 1.4f, false);
        }

        if (concern.getFaction() != null) {
            if (def.hasTag(SAIConstants.TAG_FRIENDLY)) {
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), concern.getFaction().getId(), true, priority);
            }
            if (def.hasTag(SAIConstants.TAG_UNFRIENDLY)) {
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), concern.getFaction().getId(), false, priority);
            }
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
    }

    @Override
    public void init() {
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
    }

    @Override
    public void abort() {
        if (isEnded) return;
        if (delegate != null) delegate.abortStrategicAction();
        end(StrategicActionDelegate.ActionStatus.CANCELLED);
    }

    @Override
    public void end(StrategicActionDelegate.ActionStatus status) {
        Global.getLogger(this.getClass()).info("wololo ending " + getName());
        isEnded = true;
        this.status = status;
        concern.notifyActionUpdate(this, status);
    }

    @Override
    public String getName() {
        if (delegate != null && delegate != this) return delegate.getName();
        return getDef().name;
    }

    @Override
    public String getIcon() {
        if (delegate != null&& delegate != this) return delegate.getIcon();
        return "graphics/icons/intel/reputation.png";
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
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
        iwt.addPara(prio, 0, Misc.getHighlightColor(), prioVal + "");
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
    public StrategicDefManager.StrategicActionDef getDef() {
        return StrategicDefManager.getActionDef(id);
    }
}
