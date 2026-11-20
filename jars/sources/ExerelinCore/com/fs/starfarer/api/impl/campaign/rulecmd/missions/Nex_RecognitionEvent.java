package com.fs.starfarer.api.impl.campaign.rulecmd.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_MiscCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.recognition.BaseRecognitionEventFactor;
import exerelin.campaign.intel.recognition.FactionRecognitionIntel;
import exerelin.campaign.intel.recognition.GreatFleetFactor;

import java.util.List;
import java.util.Map;

public class Nex_RecognitionEvent extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		SectorEntityToken target = dialog.getInteractionTarget();
		FactionAPI faction = target.getFaction();
		MarketAPI market = target.getMarket();

		switch (arg) {
			case "canDoGreatFleet":
				if (!Global.getSector().getCharacterData().getMemoryWithoutUpdate().getBoolean(FactionRecognitionIntel.MEMORY_KEY_GREAT_FLEET_ENABLED))
					return false;
				//if (!FactionRecognitionIntel.getInstance().isActive()) return false;
				if (BaseRecognitionEventFactor.isOutlawFaction(faction)) return false;
				if (memoryMap.get(MemKeys.PLAYER).getInt("$maxCombatHullSize") < 4) return false;
				if (market == null || market.getSize() < FactionRecognitionIntel.GREAT_FLEET_MARKET_MIN_SIZE)
					return false;
				if (faction.getMemoryWithoutUpdate().getBoolean(FactionRecognitionIntel.MEMORY_KEY_GREAT_FLEET_VISITED))
					return false;

				return faction.getRelToPlayer().isAtWorst(RepLevel.WELCOMING);

			case "applyGreatFleet":
				applyGreatFleet(market, faction, memoryMap);
		}

		return false;
	}

	public static void applyGreatFleet(MarketAPI market, FactionAPI faction, Map<String, MemoryAPI> memoryMap) {
		faction.getMemoryWithoutUpdate().set(FactionRecognitionIntel.MEMORY_KEY_GREAT_FLEET_VISITED, true);
		FactionRecognitionIntel.getInstance().addFactor(new GreatFleetFactor(faction));

		if (Nex_MiscCMD.hasSierra()) {
			memoryMap.get(MemKeys.PLAYER).set("$nex_didGreatFleetWithSierra", market.getName());
		}
	}
}
