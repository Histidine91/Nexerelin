package exerelin.campaign.battle;

public interface NexWarSimScriptListener {

    void reportRoundResolved(NexWarSimScript sim, int roundIndex);

    void reportBattleResolved(NexWarSimScript sim, Boolean result);
}
