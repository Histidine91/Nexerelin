package exerelin.campaign.ai;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.utilities.NexUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class ExecutiveAIModule extends StrategicAIModule {

	/*
        This should make the actual decisions
     */
    public static final String ACTIONS_BONUS_MEM_KEY = "$nex_strategicAI_actionsPerTurnBonus";

    @Getter protected Map<String, Float> recentActionsForAntiRepetition = new HashMap<>();

    public ExecutiveAIModule(StrategicAI ai) {
        super(ai, null);
    }

    public void reportRecentAction(StrategicAction action) {
        StrategicAction forId = action;
        StrategicDefManager.StrategicActionDef def = forId.getDef();
        String id = def.idForAntiRepetition != null ? def.idForAntiRepetition : def.id;
        float antiRepetitionMult = 1;
        if (action.getConcern() != null) antiRepetitionMult = action.getConcern().getDef().antiRepetitionMult;
        NexUtils.modifyMapEntry(recentActionsForAntiRepetition, id, def.antiRepetition * antiRepetitionMult);
    }

    public float getAntiRepetitionValue(String actionDefId) {
        StrategicDefManager.StrategicActionDef def = StrategicDefManager.getActionDef(actionDefId);
        Float val = recentActionsForAntiRepetition.get(def.idForAntiRepetition != null ? def.idForAntiRepetition : actionDefId);
        if (val == null) return 0;
        return val;
    }

    public List<StrategicAction> getRecentActions() {
        List<StrategicAction> list = new ArrayList<>();
        for (StrategicConcern concern : currentConcerns) {
            list.add(concern.getCurrentAction());
        }
        return list;
    }

    @Override
    public void advance(float days) {
        super.advance(days);
        for (String actionDefId : new ArrayList<>(recentActionsForAntiRepetition.keySet())) {
            float val = recentActionsForAntiRepetition.get(actionDefId);
            val -= days * SAIConstants.ANTI_REPETITION_DECAY_PER_DAY;
            if (val < 0) recentActionsForAntiRepetition.remove(actionDefId);
            else recentActionsForAntiRepetition.put(actionDefId, val);
        }

        List<StrategicConcern> concerns = new ArrayList<>(ai.getExistingConcerns());
        int numOngoingActions = 0;
        for (StrategicConcern concern : concerns) {
            if (concern.isEnded()) continue;
            StrategicAction act = concern.getCurrentAction();
            if (act != null && !act.isEnded()) {
                if (!act.isValid()) {
                    act.abort();
                }
            }
        }
    }

    public void actOnConcerns() {
        currentConcerns.clear();

        if (ai.getFaction().isPlayerFaction()) return;

        int actionsTakenThisMeeting = 0;
        List<StrategicConcern> concerns = new ArrayList<>(ai.getExistingConcerns());
        Collections.sort(concerns);

        // count ongoing actions
        int numOngoingActions = 0;
        for (StrategicConcern concern : concerns) {
            if (concern.isEnded()) continue;
            StrategicAction act = concern.getCurrentAction();
            if (act != null && !act.isEnded()) {
                numOngoingActions++;
            }
        }

        int max = getMaxActionsPerMeeting();
        for (StrategicConcern concern : concerns) {
            if (numOngoingActions > SAIConstants.MAX_SIMULTANEOUS_ACTIONS) break;

            if (concern.isEnded()) continue;
            if (concern.getCurrentAction() != null && !concern.getCurrentAction().isEnded()) continue;
            if (concern.getActionCooldown() > 0) continue;
            if (concern.getPriorityFloat() < SAIConstants.MIN_CONCERN_PRIORITY_TO_ACT) continue;

            StrategicAction bestAction = concern.fireBestAction();
            if (bestAction == null) continue;

            log.info("Adding action " + bestAction.getName());

            currentConcerns.add(concern);
            actionsTakenThisMeeting++;
            numOngoingActions++;
            if (actionsTakenThisMeeting >= max) break;
        }
    }

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
        String str = StrategicAI.getString("intelPara_recentActions");
        tooltip.addPara(str, 10);
        super.generateReport(tooltip, holder, width);
    }

    public int getMaxActionsPerMeeting() {
        return Math.round(getMaxActionsBonus().computeEffective(SAIConstants.ACTIONS_PER_MEETING));
    }

    public StatBonus getMaxActionsBonus() {
        MemoryAPI mem = ai.getFaction().getMemoryWithoutUpdate();
        if (!mem.contains(ACTIONS_BONUS_MEM_KEY)) {
            mem.set(ACTIONS_BONUS_MEM_KEY, new StatBonus());
        }
        return (StatBonus)mem.get(ACTIONS_BONUS_MEM_KEY);
    }
}
