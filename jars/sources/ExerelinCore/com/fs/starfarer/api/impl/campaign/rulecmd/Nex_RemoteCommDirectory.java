package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

// TODO:
public class Nex_RemoteCommDirectory extends PaginatedOptions {
	
	public static final String OPTION_PREFIX = "nex_remoteCommDirectory_open_";
	public static final int PREFIX_LENGTH = OPTION_PREFIX.length();
	
	protected boolean special;
		
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		setupVars(dialog, memoryMap);
		
		switch (arg)
		{
			case "list":
				setupDelegateDialog(dialog);
				addOptions();
				showOptions();
				break;
			case "connect":
				String marketId = memoryMap.get(MemKeys.LOCAL).getString("$option").substring(PREFIX_LENGTH);
				MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
				dialog.showCommDirectoryDialog(market.getCommDirectory());
				break;
		}
		
		return true;
	}
	
	/**
	 * To be called only when paginated dialog options are required. 
	 * Otherwise we get nested dialogs that take multiple clicks of the exit option to actually exit.
	 * @param dialog
	 */
	protected void setupDelegateDialog(InteractionDialogAPI dialog)
	{
		originalPlugin = dialog.getPlugin();  

		dialog.setPlugin(this);  
		init(dialog);
	}
	
	protected void setupVars(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		this.dialog = dialog;  
		this.memoryMap = memoryMap;
		special = memoryMap.get(MemKeys.LOCAL).getBoolean("$nex_specialDialog");
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		
		String exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = "continueCutComm";	
		dialog.getOptionPanel().setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	protected void addOptions() {
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
		Collections.sort(markets, new Comparator<MarketAPI>() {
			@Override
			public int compare(MarketAPI m1, MarketAPI m2) {
				return m1.getName().compareTo(m2.getName());
			}
		});
		
		for (MarketAPI market : markets) {
			addOption(market.getName(), OPTION_PREFIX + market.getId());
		}
		
		String exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = "continueCutComm";	
		addOptionAllPages(StringHelper.getString("back", true), exitOpt);
	}
}
