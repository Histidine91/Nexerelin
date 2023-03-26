package exerelin.campaign.ai.concern;

import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyBrain;

public class WarWearinessConcern extends DiplomacyConcern {

    protected Object readResolve() {
        faction = null;
        return this;
    }

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;

        float weariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        if (weariness < DiplomacyBrain.MAX_WEARINESS_FOR_WAR * 0.75f) return false;

        updatePriority(weariness);
        return true;
    }

    @Override
    public void update() {
        float weariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        if (weariness < DiplomacyBrain.MAX_WEARINESS_FOR_WAR * 0.75f) {
            end();
            return;
        }

        updatePriority(weariness);
    }

    @Override
    public String getName() {
        return getDef().name;
    }

    protected void updatePriority(float weariness) {
        priority.modifyFlat("value", weariness/100f, StrategicAI.getString("statValue", true));
        reapplyPriorityModifiers();
    }
}
