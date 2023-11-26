package exerelin.utilities;

public interface ModPluginEventListener {
	void onGameLoad(boolean newGame);
	void beforeGameSave();
	void afterGameSave();
	void onGameSaveFailed();
	void onNewGameAfterProcGen();
	void onNewGameAfterEconomyLoad();
	void onNewGameAfterTimePass();
}
