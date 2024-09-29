package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.*;
import lombok.Getter;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InterventionIntel extends TimedDiplomacyIntel {

    @Getter protected String recipientFactionId;
    @Getter protected String friendId;
    @Getter protected DiplomacyIntel diploEvent;

    public InterventionIntel(StrategicAction action, String factionId, String recipientFactionId, String friendId) {
        super(MathUtils.getRandomNumberInRange(20, 30));
        this.factionId = factionId;
        this.recipientFactionId = recipientFactionId;
        this.friendId = friendId;
        this.strategicAction = action;
    }

    public void init() {
        // if recipient is not a player-ruled faction, an AI method handles response
        // else, add this intel to intel manager and let player decide
        if (Nex_IsFactionRuler.isRuler(recipientFactionId)) {
            Global.getSector().getIntelManager().addIntel(this);
            Global.getSector().addScript(this);
        } else {
            boolean resist = pickAIResponse();
            if (resist) {
                intervene();
                this.state = -1;
            } else {
                makePeace();
                this.state = 1;
            }
            if (strategicAction != null) {
                diploEvent.setStrategicAction(strategicAction);
                //strategicAction.setDelegate(diploEvent);
            }
        }
    }

    public void intervene() {
        FactionAPI us = Global.getSector().getFaction(factionId);
        FactionAPI recipient = Global.getSector().getFaction(recipientFactionId);
        diploEvent = DiplomacyManager.createDiplomacyEventV2(us, recipient, "declare_war", null);
        if (diploEvent != null) {
            repResult = diploEvent.reputation;
        }
        else repResult = new ExerelinReputationAdjustmentResult(0);
    }

    public void makePeace() {
        FactionAPI recipient = Global.getSector().getFaction(recipientFactionId);
        FactionAPI friend = Global.getSector().getFaction(friendId);

        boolean peaceTreaty = false;    // if false, only ceasefire
        // can't peace treaty if vengeful, only ceasefire
        if (recipient.isAtWorst(friend, RepLevel.HOSTILE))
        {
            peaceTreaty = Math.random() < DiplomacyManager.PEACE_TREATY_CHANCE;
        }
        String eventId = peaceTreaty ? "peace_treaty" : "ceasefire";

        diploEvent = DiplomacyManager.createDiplomacyEventV2(recipient, friend, eventId, null);
        if (diploEvent != null) {
            repResult = diploEvent.reputation;
        }
        else repResult = new ExerelinReputationAdjustmentResult(0);
    }

    /**
     * @return True if the recipient faction decides to resist, false otherwise.
     */
    public boolean pickAIResponse() {
        if (!NexFactionConfig.canCeasefire(recipientFactionId, friendId)) {
            return true;
        }
        float friendWeariness = DiplomacyManager.getWarWeariness(friendId, true);
        if (friendWeariness < NexConfig.minWarWearinessForPeace)
            return true;
        float enemyWeariness = DiplomacyManager.getWarWeariness(recipientFactionId, true);
        if (enemyWeariness < NexConfig.minWarWearinessForPeace)
            return true;

        float warDecisionRating = DiplomacyManager.getManager().getDiplomacyBrain(recipientFactionId).getWarDecisionRating(factionId);
        if (warDecisionRating > DiplomacyBrain.DECISION_RATING_FOR_WAR + MathUtils.getRandomNumberInRange(-5, 5)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onExpire() {
        if (NexConfig.acceptCeasefiresOnTimeout)
            accept();
        else {
            setState(-1);
            intervene();
        }
    }

    @Override
    protected void acceptImpl() {
        makePeace();
    }

    @Override
    protected void rejectImpl() {
        intervene();
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
        NexUtilsFaction.addFactionNamePara(info, 0, tc, Global.getSector().getFaction(friendId));
    }

    @Override
    public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {
        Color h = Misc.getHighlightColor();

        FactionAPI issuer = Global.getSector().getFaction(factionId);
        FactionAPI recipient = Global.getSector().getFaction(recipientFactionId);
        FactionAPI friend = Global.getSector().getFaction(friendId);

        // image
        info.addImages(width, 96, opad, opad, issuer.getCrest(), recipient.getCrest(), friend.getCrest());

        Map<String, String> replace = new HashMap<>();

        // first description para
        String theIssuerName = issuer.getDisplayNameWithArticle();
        String issuerName = issuer.getDisplayNameWithArticleWithoutArticle();
        String theFriendName = friend.getDisplayNameWithArticle();
        String friendName = friend.getDisplayNameWithArticleWithoutArticle();
        replace.put("$theFaction", theIssuerName);
        replace.put("$TheFaction", Misc.ucFirst(theIssuerName));
        replace.put("$theFriend", theFriendName);
        replace.put("$TheFriend", Misc.ucFirst(theFriendName));
        replace.put("$hasOrHave", issuer.getDisplayNameHasOrHave());
        replace.put("$isOrAre", issuer.getDisplayNameIsOrAre());
        //StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);

        String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelInterveneDesc", replace);
        LabelAPI label = info.addPara(str, opad);
        label.setHighlight(theIssuerName, theFriendName);
        label.setHighlightColors(issuer.getBaseUIColor(), friend.getBaseUIColor());
    }

    @Override
    public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {
        FactionAPI faction = Global.getSector().getFaction(factionId);

        boolean accepted = state == 1;
        String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
        Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
        String str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
        str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
        info.addPara(str, opad, hl, acceptOrReject);

        if (diploEvent != null) {
            // display relationship change from event, and relationship following event
            Color deltaColor = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
            String delta = (int)Math.abs(repResult.delta * 100) + "";
            String newRel = NexUtilsReputation.getRelationStr(storedRelation);
            String fn = NexUtilsFaction.getFactionShortName(accepted ? friendId : factionId);
            str = StringHelper.getString("exerelin_diplomacy", accepted? "intelRepResultPositivePlayer" : "intelRepResultNegativePlayer");
            str = StringHelper.substituteToken(str, "$faction", fn);
            str = StringHelper.substituteToken(str, "$deltaAbs", delta);
            str = StringHelper.substituteToken(str, "$newRelationStr", newRel);

            LabelAPI para = info.addPara(str, opad);
            para.setHighlight(fn, delta, newRel);
            para.setHighlightColors(faction.getBaseUIColor(),
                    deltaColor, NexUtilsReputation.getRelColor(storedRelation));

            // days ago
            info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
        }
    }

    @Override
    protected String getName() {
        String str = StringHelper.getString("exerelin_diplomacy", "intelInterveneTitle");
        if (listInfoParam == EXPIRED_UPDATE)
            str += " - " + StringHelper.getString("expired");
        else if (state == 1)
            str += " - " + StringHelper.getString("accepted");
        else if (state == -1)
            str += " - " + StringHelper.getString("rejected");

        return str;
    }

    @Override
    public String getIcon() {
        if (state == 1) return Global.getSettings().getSpriteName("intel", "peace");
        if (state == -1) return Global.getSettings().getSpriteName("intel", "war");
        return getFactionForUIColors().getCrest();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(factionId);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_AGREEMENTS);
        tags.add(StringHelper.getString("diplomacy", true));
        tags.add(factionId);
        tags.add(recipientFactionId);
        tags.add(friendId);
        return tags;
    }

    @Override
    public String getStrategicActionName() {
        return this.getName();
    }
}
