package exerelin.campaign.intel.groundbattle;

public interface GroundBattleCampaignListener {
	
	void reportBattleStarted(GroundBattleIntel battle);

	/**
	 * Also called for player-initiated battles.
	 * @param battle
	 */
	void reportPlayerJoinedBattle(GroundBattleIntel battle);
	
	void reportBattleBeforeTurn(GroundBattleIntel battle, int turn);
	
	void reportBattleAfterTurn(GroundBattleIntel battle, int turn);
	
	void reportBattleEnded(GroundBattleIntel battle);
}
