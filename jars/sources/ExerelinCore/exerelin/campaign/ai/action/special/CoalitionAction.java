package exerelin.campaign.ai.action.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.AllianceManager;
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
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;

import java.util.*;

public class CoalitionAction extends BaseStrategicAction implements ShimAction {

    @Override
    public StrategicAction pickShimmedAction() {

        // First let's see if we have an alliance we want to join (while not being already in an alliance)
        String enemyId = null;
        if (faction != null) enemyId = faction.getId();
        String factionId = ai.getFactionId();

        Set<String> potentialFriends = new LinkedHashSet<>();

        if (AllianceManager.getFactionAlliance(ai.getFactionId()) == null) {
            List<Pair<Alliance, Float>> alliances = getEligibleAlliances(enemyId);
            for (Pair<Alliance, Float> entry : alliances) {
                Alliance all = entry.one;
                float allyScore = entry.two;
                if (all.canJoin(ai.getFaction())) {
                    StrategicAction allyAct = joinAlliance(all);
                    if (allyAct != null && concern.canTakeAction(allyAct) && allyAct.canUse(concern)) {
                        allyAct.getPriority().modifyFlat("allyScore", allyScore, StrategicAI.getString("statAllianceScore", true));
                        return allyAct;
                    }
                } else {
                    potentialFriends.addAll(all.getMembersCopy());
                }
            }
        }

        // see if we can form an alliance with anyone not currently allied
        for (String ofid : SectorManager.getLiveFactionIdsCopy()) {
            if (potentialFriends.contains(ofid)) continue;
            if (ofid.equals(ai.getFactionId())) continue;
            if (ofid.equals(enemyId)) continue;

            if (AllianceManager.getManager().canAlly(factionId, ofid)) {
                StrategicAction allyAct = createAlliance(ofid);
                if (allyAct != null && concern.canTakeAction(allyAct) && allyAct.canUse(concern)) return allyAct;
            } else {
                potentialFriends.add(ofid);
            }
        }

        // let's just diplo with one of them
        return pickDiplomacyAction(potentialFriends);
    }

    protected StrategicAction joinAlliance(Alliance all) {
        if (!NexConfig.enableAlliances) return null;
        EnterAllianceAction act = (EnterAllianceAction)StrategicDefManager.instantiateAction(StrategicDefManager.getActionDef("enterAlliance"));
        act.initForConcern(concern);
        act.setAlliance(all);
        act.setDelegate(act);
        return act;
    }

    protected StrategicAction createAlliance(String otherFactionId) {
        if (!NexConfig.enableAlliances) return null;
        EnterAllianceAction act = (EnterAllianceAction)StrategicDefManager.instantiateAction(StrategicDefManager.getActionDef("enterAlliance"));
        act.initForConcern(concern);
        act.setFaction(Global.getSector().getFaction(otherFactionId));
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
                if (SAIConstants.DEBUG_LOGGING && ExerelinModPlugin.isNexDev) Global.getLogger(this.getClass()).info(String.format("  Shimmed action %s has priority %s", action.getName(), NexUtils.mutableStatToString(action.getPriority())));
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

        for (Alliance all : AllianceManager.getAllianceList()) {
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
        //priority.modifyFlat("temp", 400, "lololol");
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
