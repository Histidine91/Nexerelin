package data.scripts.world.exerelin;

public class SectorEventManager
{
    private EventAddObjectToStorage eventAddObjectToStorage;

    private int waitTime = 30; // Wait 1 month before running first events
    private String lastEventType = "";
    private int betweenEventWait = 10;

    public SectorEventManager()
    {
        eventAddObjectToStorage = new EventAddObjectToStorage();
    }

    public void runEvents()
    {
        if(waitTime > 0)
        {
            waitTime--;
            return;
        }

        float extraChance = ExerelinUtilsPlayer.getPlayerDiplomacyObjectCreationBonus();

        if(ExerelinUtils.getRandomInRange(0,(int)(45*(1.0f - extraChance))) == 0
                && !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType))
        {
            eventAddObjectToStorage.addAgentToStorageFacility();
            waitTime = betweenEventWait;
            //lastEventType = eventAddObjectToStorage.getType();
            lastEventType = "";
        }

        if(ExerelinUtils.getRandomInRange(0,(int)(45*(1.0f - extraChance))) == 0
                && !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType))
        {
            eventAddObjectToStorage.addPrisonerToStorageFacility();
            waitTime = betweenEventWait;
            //lastEventType = eventAddObjectToStorage.getType();
            lastEventType = "";
        }

        if(ExerelinUtils.getRandomInRange(0,(int)(45*(1.0f - extraChance))) == 0
                && !eventAddObjectToStorage.getType().equalsIgnoreCase(lastEventType)
                && ExerelinUtilsPlayer.getPlayerSabateurAvailability())
        {
            eventAddObjectToStorage.addSabateurToStorageFacility();
            waitTime = betweenEventWait;
            //lastEventType = eventAddObjectToStorage.getType();
            lastEventType = "";
        }
    }

    public void triggerEvent(String eventType)
    {
        if(eventType.equalsIgnoreCase("saboteur"))
            eventAddObjectToStorage.addSabateurToStorageFacility();
    }
}
