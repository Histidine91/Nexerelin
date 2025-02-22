package exerelin.campaign.achievements;

import org.magiclib.achievements.MagicAchievement;
import org.magiclib.achievements.MagicAchievementManager;

public class OneMansTrash extends MagicAchievement {
    public static void complete() {
        MagicAchievementManager.getInstance().completeAchievement("nex_oneMansTrash");
    }
}
