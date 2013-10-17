package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.Global;

import java.awt.*;

public class ExerelinUtilsMessaging
{
    public static void addMessage(String message, Color color)
    {
        Global.getSector().getCampaignUI().addMessage(message, color);

        ExerelinUtilsMessaging.addMessageToManager(message, color);
    }

    public static void addMessage(String message)
    {
        Global.getSector().getCampaignUI().addMessage(message);

        ExerelinUtilsMessaging.addMessageToManager(message, null);
    }

    private static void addMessageToManager(String message, Color color)
    {
        System.out.println("Exerelin UI Message: " + message);

        ExerelinMessageManager exerelinMessageManager = (ExerelinMessageManager)Global.getSector().getPersistentData().get("ExerelinMessageManager");

        if(exerelinMessageManager == null)
        {
            exerelinMessageManager = new ExerelinMessageManager();
            Global.getSector().getPersistentData().put("ExerelinMessageManager", exerelinMessageManager);
        }

        exerelinMessageManager.addMessage(new ExerelinMessage(message, color));
    }

    public static java.util.List<ExerelinMessage> getMessages()
    {
        return ((ExerelinMessageManager)Global.getSector().getPersistentData().get("ExerelinMessageManager")).getMessages();
    }
}
