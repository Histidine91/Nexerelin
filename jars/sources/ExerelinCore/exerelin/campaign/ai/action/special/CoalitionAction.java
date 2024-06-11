package exerelin.campaign.ai.action.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.action.EnterAllianceAction;
import exerelin.campaign.ai.action.ShimAction;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class CoalitionAction extends BaseStrategicAction implements ShimAction {

    @Override
    public StrategicAction pickShimmedAction() {
        String enemyId = null;
        if (faction != null && concern.getDef().hasTag("coalition_enemy")) {
            enemyId = faction.getId();
        }
        String ourId = ai.getFactionId();

        Set<String> potentialFriends = new LinkedHashSet<>();
        Alliance ourCurrAlliance = AllianceManager.getFactionAlliance(ai.getFactionId());

        // First let's see if we have an alliance we want to join (while not being already in an alliance)
        if (ourCurrAlliance == null) {
            List<Pair<Alliance, Float>> alliances = getEligibleAlliances(enemyId);
            for (Pair<Alliance, Float> entry : alliances) {
                Alliance all = entry.one;
                float allyScore = entry.two;
                if (all.canJoin(ai.getFaction())) {
                    StrategicAction allyAct = joinAlliance(ourId, all);
                    if (allyAct != null && concern.canTakeAction(allyAct) && allyAct.canUse(concern)) {
                        allyAct.getPriority().modifyFlat("allyScore", allyScore, StrategicAI.getString("statAllianceScore", true));
                        return allyAct;
                    }
                } else {
                    potentialFriends.addAll(all.getMembersCopy());
                }
            }
        }

        // see if we can form or merge an alliance
        for (String ofid : SectorManager.getLiveFactionIdsCopy()) {
            if (potentialFriends.contains(ofid)) continue;  // these people are already known to be in an alliance that we can't join yet, save them for later
            if (ofid.equals(ourId)) continue;
            if (ofid.equals(enemyId)) continue;

            Alliance otherAlliance = AllianceManager.getFactionAlliance(ofid);
            if (otherAlliance != null && ourCurrAlliance == otherAlliance) continue; // already allied

            String commId = Misc.getCommissionFactionId();
            if (ofid.equals(Factions.PLAYER) && commId != null) continue;   // don't interact with player while they have a commission
            if (NexUtilsFaction.isPirateFaction(ofid) != NexUtilsFaction.isPirateFaction(ourId)) continue;

            boolean canAlly;
            if (ourCurrAlliance != null) {
                if (otherAlliance != null) canAlly = AllianceManager.canMerge(ourCurrAlliance, otherAlliance);
                else canAlly = ourCurrAlliance.canJoin(Global.getSector().getFaction(ofid));
            }
            else canAlly = AllianceManager.getManager().canAlly(ourId, ofid);

            // try invite, merge, or form new alliance
            if (canAlly) {
                StrategicAction allyAct;
                if (ourCurrAlliance != null && otherAlliance != null)
                    allyAct = mergeAlliance(ofid, ourCurrAlliance, otherAlliance);
                else if (ourCurrAlliance != null)
                    allyAct = joinAlliance(ofid, ourCurrAlliance);
                else
                    allyAct = createAlliance(ofid);

                if (allyAct != null && concern.canTakeAction(allyAct) && allyAct.canUse(concern)) {
                    return allyAct;
                }
            } else {
                potentialFriends.add(ofid);
            }
        }

        // no alliance possible, let's just diplo with one of them
        return pickDiplomacyAction(potentialFriends);
    }

    protected StrategicAction joinAlliance(String candidateId, Alliance all) {
        EnterAllianceAction act = (EnterAllianceAction)createAlliance(candidateId);
        if (act == null) return null;
        act.setAlliance(all);
        return act;
    }

    protected StrategicAction mergeAlliance(String candidateId, Alliance all, Alliance all2) {
        EnterAllianceAction act = (EnterAllianceAction)joinAlliance(candidateId, all);
        if (act == null) return null;
        act.setAlliance2(all2);
        return act;
    }

    protected StrategicAction createAlliance(String otherFactionId) {
        if (!NexConfig.enableAlliances) return null;
        EnterAllianceAction act = (EnterAllianceAction)StrategicDefManager.instantiateAction(StrategicDefManager.getActionDef("enterAlliance"));
        act.initForConcern(concern);
        act.setFaction(Global.getSector().getFaction(otherFactionId));
        act.setDelegate(act);
        return act;
    }

    protected StrategicAction pickDiplomacyAction(Collection<String> factionIds) {
        StrategicConcern temp = StrategicDefManager.instantiateConcern(StrategicDefManager.getConcernDef("testRaiseRelations"));
        temp.setAI(ai, ai.getDiploModule());
        temp.generate();

        StrategicAction bestAction = null;
        float bestPrio = 0;

        for (String factionId : factionIds) {
            temp.setFaction(Global.getSector().getFaction(factionId));
            List<StrategicAction> actions = temp.getUsableActions();
            for (StrategicAction action : actions) {
                float prio = action.getPriorityFloat();
                if (prio > bestPrio) {
                    bestPrio = prio;
                    bestAction = action;
                }
            }
        }

        return bestAction;
    }

    protected List<Pair<Alliance, Float>> getEligibleAlliances(String enemyFactionId) {
        List<Pair<Alliance, Float>> results = new ArrayList<>();
        Alliance ours = AllianceManager.getFactionAlliance(ai.getFactionId());

        for (Alliance all : AllianceManager.getAllianceList()) {
            if (all == ours) continue;
            if (all.getMembersCopy().contains(enemyFactionId)) continue;
            if (!AllianceManager.isAlignmentCompatible(ai.getFactionId(), all)) continue;
            float score = getAllianceScore(all, enemyFactionId);
            results.add(new Pair<>(all, score));
        }

        Collections.sort(results, new NexUtils.PairWithFloatComparator(true));

        return results;
    }

    protected float getAllianceScore(Alliance all, String enemyFactionId) {
        float score = all.getAverageRelationshipWithFaction(ai.getFactionId());
        if (enemyFactionId != null) {
            // in terms of safety they shouldn't care, but reduction based on average relations with enemy faction might help segregate factions into power blocs
            score -= all.getAverageRelationshipWithFaction(enemyFactionId) * 0.5f;
        }
        if (score <= 0) return 0;
        score *= all.getAllianceMarketSizeSum();
        return score;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.modifyFlat("base", 120, StrategicAI.getString("statBase", true));
        if (faction != null) {
            applyPriorityModifierForDisposition(faction.getId() ,priority);
        }
    }

    // modified from SAIUtils.applyPriorityModifierForDisposition
    // Disposition modifier for coalition actions is always positive
    // prioritize this action if we like OR hate the faction, but not if we're neutral to them
    public void applyPriorityModifierForDisposition(String otherFactionId, MutableStat stat) {
        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
        if (brain.getDisposition(ai.getFactionId()) != null) {
            float disposition = brain.getDisposition(otherFactionId).disposition.getModifiedValue();
            boolean isPositive;
            if (disposition <= DiplomacyBrain.DISLIKE_THRESHOLD) isPositive = false;
            else if (disposition >= DiplomacyBrain.LIKE_THRESHOLD) isPositive = true;
            else return;

            String desc = StrategicAI.getString(isPositive ? "statDispositionPositive" : "statDispositionNegative", true);
            String source = isPositive ? "disposition_positive" : "disposition_negative";

            stat.modifyMult(source, SAIConstants.POSITIVE_DISPOSITION_MULT, desc);
        }
    }

    @Override
    public void postGenerate() {
        super.postGenerate();
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        return concern.getDef().hasTag("canCoalition");
    }
}
