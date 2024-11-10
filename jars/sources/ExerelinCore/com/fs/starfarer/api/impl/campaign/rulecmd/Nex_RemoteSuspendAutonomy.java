package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.intel.PlayerOutpostIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Nex_RemoteSuspendAutonomy extends PaginatedOptionsPlus {
	
	protected static final List<Misc.Token> EMPTY_PARAMS = new ArrayList<>();
	public static final String OPTION_PREFIX = "nex_suspendAutonomyRemote_";
	public static final int PREFIX_LENGTH = OPTION_PREFIX.length();
	
	protected boolean special;	// from special menu (i.e. FieldOptionsScreenScript)
	
	@Override
	public boolean execute(final String ruleId, InteractionDialogAPI dialog, List<Token> params, final Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg)
		{
			case "menu":
				optionsPerPage = 5;
				super.execute(ruleId, dialog, EMPTY_PARAMS, memoryMap);
				special = memoryMap.get(MemKeys.LOCAL).getBoolean("$nex_specialDialog");
				listOptions();
				showOptions();
				return true;
			case "select":
				String marketId = memoryMap.get(MemKeys.LOCAL).getString("$option").substring(PREFIX_LENGTH);
				MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
				Nex_GrantAutonomy.suspendAutonomy(market);
				dialog.getVisualPanel().showCore(CoreUITabId.OUTPOSTS, null, new CoreInteractionListener() {
					@Override
					public void coreUIDismissed() {
						FireBest.fire(ruleId, Global.getSector().getCampaignUI().getCurrentInteractionDialog(), memoryMap, "Nex_RemoteSuspendAutonomy");
					}
				});
				return true;
		}
		
		return false;
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		if (optionData.equals("continueCutComm")) {
			// since we can't exit a paginated option dialog nested in FieldOptionsScreenScript's dialog the normal way
			dialog.dismissAsCancel();
			return;
		}
		
		super.optionSelected(optionText, optionData);
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		String exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = "continueCutComm";	
		dialog.getOptionPanel().setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	public void listOptions() {
		options.clear();
		
		String exitOpt = "exerelinMarketSpecial";
		if (special)
			exitOpt = "continueCutComm";
		addOptionAllPages(StringHelper.getString("back", true), exitOpt);
		
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
		boolean colorize = false;
		
		// allow suspending autonomy of commissioning faction's markets as well, in faction ruler mode
		if (Misc.getCommissionFaction() != null && Nex_IsFactionRuler.isRuler(Misc.getCommissionFactionId()))
		{
			colorize = true;
			markets.addAll(Misc.getFactionMarkets(Misc.getCommissionFaction()));
		}
		
		for (MarketAPI market : markets) {
			if (market.isPlayerOwned()) continue;
			if (market.getContainingLocation() == null) continue;	// FIXME why is this fixme here? I don't remember
			if (market.getMemoryWithoutUpdate().contains(PlayerOutpostIntel.MARKET_MEMORY_FLAG))
				continue;
			
			String name = StringHelper.getString("exerelin_markets", "marketDirectoryEntry");
			name = StringHelper.substituteToken(name, "$market", market.getName());
			name = StringHelper.substituteToken(name, "$size", market.getSize() + "");
			name = StringHelper.substituteToken(name, "$location", market.getContainingLocation().getNameWithTypeIfNebula());
			String id = OPTION_PREFIX + market.getId();
			addOption(name, id);
			if (colorize) addColor(id, market.getFaction().getBaseUIColor());
		}
	}
}
