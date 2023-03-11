package exerelin.utilities;

import exerelin.campaign.intel.agents.CovertActionIntel;

public interface ModPluginEventListener {
	void onGameLoad(boolean newGame);
	void beforeGameSave();
	void afterGameSave();
	void onGameSaveFailed();
	void onNewGameAfterProcGen();
	void onNewGameAfterEconomyLoad();
	void onNewGameAfterTimePass();
}
