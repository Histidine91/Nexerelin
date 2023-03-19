package exerelin.campaign.ai.action;

import com.fs.starfarer.api.combat.MutableStat;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.SAIUtils;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits;
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
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), true, priority);
            }
            if (def.hasTag(SAIConstants.TAG_UNFRIENDLY)) {
                SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), false, priority);
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
        if (isEnded) return;
        StrategicActionDelegate.ActionStatus currStatus = delegate.getStrategicActionStatus();
        if (status != currStatus) {
            if (currStatus.isEnded()) {
                end(currStatus);
                return;
            }
            status = currStatus;
            concern.notifyActionUpdate(this, status);
        }
    }

    @Override
    public void end(StrategicActionDelegate.ActionStatus status) {
        isEnded = true;
        this.status = status;
        concern.notifyActionUpdate(this, status);
    }

    @Override
    public String getName() {
        if (delegate != null) return delegate.getName();
        return getDef().name;
    }

    @Override
    public String getIcon() {
        if (delegate != null) return delegate.getIcon();
        return null;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return false;
    }

    @Override
    public StrategicDefManager.StrategicActionDef getDef() {
        return StrategicDefManager.getActionDef(id);
    }
}
