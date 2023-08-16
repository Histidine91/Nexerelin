package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper.FactionListGrouping;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Nex_TransferMarket extends BaseCommandPlugin {
	
	public static final String FACTION_GROUPS_KEY = "$nex_factionDirectoryGroups";
	public static final float GROUPS_CACHE_TIME = 0f;
	public static final String SELECT_FACTION_PREFIX = "nex_transferMarket_";
	public static final int PREFIX_LENGTH = SELECT_FACTION_PREFIX.length();
	public static final List<String> NO_TRANSFER_FACTIONS = new ArrayList<>(Arrays.asList(new String[]{
		Factions.PLAYER, Factions.DERELICT, "nex_derelict"
	}));
		
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		switch (arg)
		{
			case "hasSpaceport":
				//return NexUtilsMarket.hasWorkingSpaceport(market);
				return market.hasSpaceport();
			
			// list faction groupings
			case "listGroups":
				listGroups(dialog, memoryMap.get(MemKeys.LOCAL));
				return true;
				
			// list factions within a faction grouping
			case "listFactions":
				int num = (int)params.get(1).getFloat(memoryMap);
				listFactions(dialog, memoryMap.get(MemKeys.LOCAL), num);
				return true;
			
			// actually transfer the market to the specified faction
			case "transfer":
				String option = memoryMap.get(MemKeys.LOCAL).getString("$option");
				//if (option == null) throw new IllegalStateException("No $option set");
				String factionId = option.substring(PREFIX_LENGTH);
				transferMarket(dialog, market, factionId);
				return true;
				
			case "transferAndGovern":
				transferMarket(dialog, market, Misc.getCommissionFactionId());
				Nex_BuyColony.buy(market, 0, dialog);
				return true;
			
			// prints a string listing the reputation change from performing the transfer
			case "printRepChange":
				TextPanelAPI text = dialog.getTextPanel();
				text.setFontSmallInsignia();
				MutableStat repChange = getRepChange(market);
				String repChangeStr = String.format("%.1f", repChange.getModifiedValue());
				String str = StringHelper.getString("exerelin_markets", "transferMarketRep");
				str = StringHelper.substituteToken(str, "$repChange", repChangeStr);
				str = StringHelper.substituteToken(str, "$market", market.getName() + "");
				text.addParagraph(str);
				text.highlightLastInLastPara(repChangeStr, Misc.getHighlightColor());
				//text.addParagraph("Market value: " + getMarketValue(market));
				
				TooltipMakerAPI info = text.beginTooltip();
				info.addStatModGrid(350, 50, 10, 0, repChange, true, NexUtils.getStatModValueGetter(true, 1));
				text.addTooltip();
				
				String recentOwnerId = getRecentlyCapturedFromId(market);
				
				/*
				if (recentOwnerId != null && !recentOwnerId.equals(Factions.PLAYER)) {
					FactionAPI recentOwner = Global.getSector().getFaction(recentOwnerId);
					str = StringHelper.getString("exerelin_markets", "transferMarketRecentlyCaptured");
					str = StringHelper.substituteToken(str, "$market", market.getName() + "");
					str = StringHelper.substituteToken(str, "$theFaction", recentOwner.getDisplayNameWithArticle());
					String mult = Global.getSettings().getFloat("nex_transferMarket_recentlyCapturedMult") + "×";
					str = StringHelper.substituteToken(str, "$mult", mult);
					LabelAPI para = text.addParagraph(str);
					para.setHighlight(recentOwner.getDisplayNameWithArticleWithoutArticle(), mult);
					para.setHighlightColors(recentOwner.getBaseUIColor(), Misc.getNegativeHighlightColor());
				}
				*/
				
				String origOwnerId = NexUtilsMarket.getOriginalOwner(market);
				if (origOwnerId != null && !origOwnerId.equals(recentOwnerId) && !origOwnerId.equals(Factions.PLAYER)) {
					FactionAPI origOwner = Global.getSector().getFaction(origOwnerId);
					str = StringHelper.getString("exerelin_markets", "transferMarketOriginalOwner");
					str = StringHelper.substituteToken(str, "$market", market.getName() + "");
					str = StringHelper.substituteToken(str, "$theFaction", origOwner.getDisplayNameWithArticle());
					String bonus = Global.getSettings().getFloat("nex_transferMarket_originalOwnerMult") + "×";
					str = StringHelper.substituteToken(str, "$bonus", bonus);
					LabelAPI para = text.addParagraph(str);
					para.setHighlight(origOwner.getDisplayNameWithArticleWithoutArticle(), bonus);
					para.setHighlightColors(origOwner.getBaseUIColor(), Misc.getHighlightColor());
				}
				
				text.setFontInsignia();
				return true;
		}
		return false;
	}
	
	public static MutableStat getRepChange(MarketAPI market)
	{
		MutableStat stat = new MutableStat(0);
		
		// market size base
		float fromSize = market.getSize() * 2;		
		stat.modifyFlat("marketSize", fromSize, StringHelper.getString("exerelin_markets", 
				"transferMarketFactorSize", true));
		
		// industry
		float value = NexUtilsMarket.getMarketIndustryValue(market) 
				/ Global.getSettings().getFloat("nex_transferMarket_valueDivisor");
		//value *= market.getSize() - 2;
		stat.modifyFlat("industry", value, StringHelper.getString("exerelin_markets", 
				"transferMarketFactorIndustry", true));
		
		float income = NexUtilsMarket.getIncomeNetPresentValue(market, 6, 0) 
				/ Global.getSettings().getFloat("nex_transferMarket_incomeDivisor");
		stat.modifyFlat("income", income, StringHelper.getString("exerelin_markets", 
				"transferMarketFactorIncome", true));
		
		float fromStability = (market.getStabilityValue() + 10) / 20;
		stat.modifyMult("stability", fromStability, StringHelper.getString("exerelin_markets", 
				"transferMarketFactorStability", true));
		
		return stat;
	}
	
	public void transferMarket(InteractionDialogAPI dialog, MarketAPI market, String newFactionId)
	{
		String oldFactionId = market.getFactionId();
		FactionAPI newFaction = Global.getSector().getFaction(newFactionId);
		FactionAPI oldFaction = Global.getSector().getFaction(oldFactionId);
		SectorEntityToken ent = dialog.getInteractionTarget();
		
		float repChange = getRepChange(market).getModifiedValue() * 0.01f;
		if (newFactionId.equals(getRecentlyCapturedFromId(market)))
			repChange *= Global.getSettings().getFloat("nex_transferMarket_recentlyCapturedMult");
		else if (newFactionId.equals(NexUtilsMarket.getOriginalOwner(market)))
			repChange *= Global.getSettings().getFloat("nex_transferMarket_originalOwnerMult");
		
		SectorManager.transferMarket(market, newFaction, oldFaction, true, false, 
				new ArrayList<>(Arrays.asList(newFactionId)), repChange);
		DiplomacyManager.getManager().getDiplomacyBrain(newFactionId).reportDiplomacyEvent(oldFactionId, repChange);
		
		// hack for "our interaction target is a station and we just sold it" case
		if (Entities.STATION_BUILT_FROM_INDUSTRY.equals(ent.getCustomEntityType())) {
			ent.setFaction(newFactionId);
		}
		
		//ExerelinUtilsReputation.adjustPlayerReputation(newFaction, null, repChange, null, dialog.getTextPanel());	// done in event
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();
		ent.getMemoryWithoutUpdate().set("$_newFaction", newFaction.getDisplayNameWithArticle(), 0);
		
		boolean hostile = newFaction.isHostileTo(oldFaction);
		if (hostile) {
			market.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET, true, 90);
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
		}
	}
	
	public static String getRecentlyCapturedFromId(MarketAPI market) {
		if (!market.getMemoryWithoutUpdate().contains(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED))
			return null;
		return market.getMemoryWithoutUpdate().getString(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED);
	}
	
	public static void listFactions(InteractionDialogAPI dialog, MemoryAPI memory, int num) 
	{
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();	
		//memoryMap.get(MemKeys.LOCAL).set("$nex_dirFactionGroup", num);
		List<FactionListGrouping> groups = (List<FactionListGrouping>)(memory.get(FACTION_GROUPS_KEY));
		FactionListGrouping group = groups.get(num - 1);
		
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		boolean recentlyCaptured = getRecentlyCapturedFromId(market) != null;
		
		for (FactionAPI faction : group.factions)
		{
			String optKey = SELECT_FACTION_PREFIX + faction.getId();
			opts.addOption(Nex_FactionDirectoryHelper.getFactionDisplayName(faction), optKey, faction.getColor(), null);
			String warningString = StringHelper.getStringAndSubstituteToken("exerelin_markets", "transferMarketWarning", 
					"$market", dialog.getInteractionTarget().getMarket().getName());
			warningString = StringHelper.substituteFactionTokens(warningString, faction);
			
			if (recentlyCaptured && faction.isHostileTo(Factions.PLAYER)) {
				opts.setEnabled(optKey, false);
				opts.setTooltip(optKey, StringHelper.getString("exerelin_markets", "transferMarketRecentlyCapturedDisabled"));
				continue;
			}
			
			opts.addOptionConfirmation(optKey, warningString, 
					Misc.ucFirst(StringHelper.getString("yes")), Misc.ucFirst(StringHelper.getString("no")));
		}

		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), "nex_transferMarketMain");
		opts.setShortcut("nex_transferMarketMain", Keyboard.KEY_ESCAPE, false, false, false, false);

		NexUtils.addDevModeDialogOptions(dialog);
	}
	
	/**
	 * Creates dialog options for the faction list subgroups.
	 * @param dialog
	 * @param memory
	 */
	public static void listGroups(InteractionDialogAPI dialog, MemoryAPI memory)
	{		
		OptionPanelAPI opts = dialog.getOptionPanel();
		opts.clearOptions();
		List<FactionListGrouping> groups;
		
		if (memory.contains(FACTION_GROUPS_KEY))
		{
			groups = (List<FactionListGrouping>)memory.get(FACTION_GROUPS_KEY);
		}
		else
		{
			List<String> factionsForDirectory = Nex_FactionDirectoryHelper.getFactionsForDirectory(NO_TRANSFER_FACTIONS, true);
			if (SectorManager.getManager().isCorvusMode()) {
				factionsForDirectory.remove("al_ars");	// Society shouldn't be given markets in normal sector
			}
			
			groups = Nex_FactionDirectoryHelper.getFactionGroupings(factionsForDirectory);
			memory.set(FACTION_GROUPS_KEY, groups, GROUPS_CACHE_TIME);
		}

		int groupNum = 0;
		for (FactionListGrouping group : groups)
		{
			groupNum++;
			String optionId = "nex_transferMarketList" + groupNum;
			opts.addOption(group.getGroupingRangeString(),
					optionId, group.tooltip);
			opts.setTooltipHighlights(optionId, group.getFactionNames().toArray(new String[0]));
			opts.setTooltipHighlightColors(optionId, group.getTooltipColors().toArray(new Color[0]));
		}
		
		FactionAPI comm = Misc.getCommissionFaction();
		if (comm != null && Factions.PLAYER.equals(NexUtilsMarket.getOriginalOwner(dialog.getInteractionTarget().getMarket())))
		{
			String str = StringHelper.getString("exerelin_markets", "transferMarketAndGovern");
			str = StringHelper.substituteFactionTokens(str, comm);
			
			String tooltip = StringHelper.getStringAndSubstituteToken("exerelin_markets", "transferMarketAndGovernInfo", 
							"$market", dialog.getInteractionTarget().getMarket().getName());
			tooltip = StringHelper.substituteFactionTokens(tooltip, comm);
			
			opts.addOption(Misc.ucFirst(str), "nex_transferMarketAndGovern", comm.getBaseUIColor(), tooltip);
			
			String warningString = StringHelper.getStringAndSubstituteToken("exerelin_markets", "transferMarketWarning", 
							"$market", dialog.getInteractionTarget().getMarket().getName());
					warningString = StringHelper.substituteFactionTokens(warningString, comm);
			
			opts.addOptionConfirmation("nex_transferMarketAndGovern", warningString, 
					Misc.ucFirst(StringHelper.getString("yes")), Misc.ucFirst(StringHelper.getString("no")));
		}
		
		String exitOpt = "exerelinBaseCommanderMenuRepeat";
		opts.addOption(Misc.ucFirst(StringHelper.getString("back")), exitOpt);
		opts.setShortcut(exitOpt, Keyboard.KEY_ESCAPE, false, false, false, false);
		
		NexUtils.addDevModeDialogOptions(dialog);
	}
}