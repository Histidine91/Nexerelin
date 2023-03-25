package exerelin.campaign.diplomacy;

public interface DiplomacyBrainListener {

    void reportDispositionsUpdated(String factionId, DiplomacyBrain brain);
}
