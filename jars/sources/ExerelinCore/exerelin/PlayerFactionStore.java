package exerelin;

public class PlayerFactionStore {

    private static String factionId = "independent";
    
    // NOTE: only use for new games; factionId currently isn't saved yet
    public static void setPlayerFactionId(String newFactionId)
    {
        factionId = newFactionId;
    }
    
    public static String getPlayerFactionId()
    {
        return factionId;
    }
}
