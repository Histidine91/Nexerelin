package exerelin.campaign.ai.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.intel.diplomacy.AllianceOfferIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import lombok.Getter;
import lombok.Setter;

public class EnterAllianceAction extends DiplomacyAction implements StrategicActionDelegate {

    public static final boolean ALLIANCE_DEBUGGING = false;

    @Getter @Setter protected Alliance alliance;

    /*
        note: when applying to join an existing alliance, 'faction' will be the AI faction
        when inviting other faction to our alliance, 'faction' will be the invitee (not us)
        when creating new alliance, 'faction' will be our proposed new ally
     */

    @Override
    public boolean generate() {
        String afid = ai.getFactionId();
        boolean isPlayerControlled = faction != null && Nex_IsFactionRuler.isRuler(faction);
        if (alliance != null) {
            if (isPlayerControlled) {
                AllianceOfferIntel offer = new AllianceOfferIntel(afid, alliance);
                setDelegate(offer);
                offer.init();
                return true;
            } else {
                AllianceManager.joinAllianceStatic(faction.getId(), alliance);
            }

        } else {
            if (isPlayerControlled) {
                AllianceOfferIntel offer = new AllianceOfferIntel(afid, AllianceManager.getFactionAlliance(afid));
                setDelegate(offer);
                offer.init();
                return true;
            } else {
                alliance = AllianceManager.createAlliance(ai.getFactionId(), this.faction.getId());
            }
        }
        if (alliance == null) return false;

        delegate = this;
        return true;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!NexConfig.enableDiplomacy) return false;
        if (!NexConfig.enableAlliances) return false;
        if (NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy) return false;

        if (!ALLIANCE_DEBUGGING && Global.getSector().getMemoryWithoutUpdate().getBoolean(MEM_KEY_GLOBAL_COOLDOWN))
            return false;

        if (NexUtilsFaction.isPirateFaction(ai.getFactionId())) return false;

        if (alliance != null) {
            // check if the not currently allied faction can actually join the alliance
            if (alliance.getMembersCopy().contains(ai.getFactionId()) && !alliance.canJoin(faction)) return false;
            if (alliance.getMembersCopy().contains(faction) && !alliance.canJoin(ai.getFaction())) return false;
        }
        else if (faction != null) {
            if (NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy) return false;
            if (!AllianceManager.getManager().canAlly(ai.getFactionId(), faction.getId())) return false;
            if (NexUtilsFaction.isPirateFaction(faction.getId())) return false;

            FactionAPI comm = Misc.getCommissionFaction();
            if (faction.isPlayerFaction() && comm != null) return false;    // don't deal with player while commissioned
            boolean playerRuled = Nex_IsFactionRuler.isRuler(faction);
            if (playerRuled && !NexConfig.npcAllianceOffers) return false;
            if (playerRuled && faction.getMemoryWithoutUpdate().getBoolean(AllianceOfferIntel.MEM_KEY_COOLDOWN)) return false;
        }

        if (!ALLIANCE_DEBUGGING && NexUtils.getTrueDaysSinceStart() < NexConfig.allianceGracePeriod) return false;
        return concern.getDef().hasTag("canAlly") || concern.getDef().hasTag("canCoalition");
    }

    @Override
    public String getName() {
        if (delegate != null && delegate instanceof AllianceOfferIntel) {
            return ((AllianceOfferIntel)delegate).getSmallDescriptionTitle();
        }
        if (alliance == null) {
            return "Alliance - error";
        }
        return alliance.getName();
    }

    @Override
    public String getIcon() {
        if (delegate != null && delegate instanceof AllianceOfferIntel) {
            return ((AllianceOfferIntel)delegate).getIcon();
        }
        if (alliance != null && alliance.getIntel() != null) return alliance.getIntel().getIcon();
        return Global.getSettings().getSpriteName("intel", "alliance");
    }

    @Override
    public ActionStatus getStrategicActionStatus() {
        return ActionStatus.SUCCESS;
    }

    @Override
    public float getStrategicActionDaysRemaining() {
        return 0;
    }

    @Override
    public String getStrategicActionName() {
        return this.getName();
    }

    @Override
    public StrategicAction getStrategicAction() {
        return this;
    }

    @Override
    public void setStrategicAction(StrategicAction action) {

    }

    @Override
    public void abortStrategicAction() {
        // action insta-completes so can't abort
    }
}
