package exerelin;

public class PlayerFactionStore {

    private static String factionId = "independent";
    
    public static void setPlayerFaction(String newFactionId)
    {
        factionId = newFactionId;
    }
    
    public static String getPlayerFaction()
    {
        return factionId;
    }
}
