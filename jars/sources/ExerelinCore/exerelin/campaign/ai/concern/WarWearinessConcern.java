package exerelin.campaign.ai.concern;

import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexConfig;

public class WarWearinessConcern extends DiplomacyConcern {

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;
        update();
        return !ended;
    }

    @Override
    public void update() {
        float weariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        if (weariness < NexConfig.minWarWearinessForPeace * 0.75f) {
            end();
            return;
        }
        updatePriority(weariness);
    }

    @Override
    public boolean isValid() {
        float weariness = DiplomacyManager.getWarWeariness(ai.getFactionId(), true);
        return weariness >= NexConfig.minWarWearinessForPeace * 0.75f;
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
