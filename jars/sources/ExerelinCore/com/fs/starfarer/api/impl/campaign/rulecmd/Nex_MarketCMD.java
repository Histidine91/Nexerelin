package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.applyDefenderIncreaseFromRaid;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getDefenderIncreaseValue;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.statPrinter;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import static exerelin.campaign.InvasionRound.getString;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.lwjgl.input.Keyboard;

public class Nex_MarketCMD extends MarketCMD {
	
	public static final String INVADE = "nex_mktInvade";
	public static final String INVADE_CONFIRM = "nex_mktInvadeConfirm";
	public static final String INVADE_ABORT = "nex_mktInvadeAbort";
	public static final String INVADE_RESULT = "nex_mktInvadeResult";
	public static final String INVADE_RESULT_ANDRADA = "nex_mktInvadeResultAndrada";
	public static final String INVADE_GO_BACK = "nex_mktInvadeGoBack";
	public static final float FAIL_THRESHOLD_INVASION = 0.5f;
	
	protected TempDataInvasion tempInvasion = new TempDataInvasion();
	
	public Nex_MarketCMD() {
		
	}
	
	public Nex_MarketCMD(SectorEntityToken entity) {
		super(entity);
		initForInvasion(entity);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		super.execute(ruleId, dialog, params, memoryMap);
		
		String command = params.get(0).getString(memoryMap);
		if (command == null) return false;
		
		if (command.equals("invadeMenu")) {
			invadeMenu();
		} else if (command.equals("invadeConfirm")) {
			invadeRunRound();
		} else if (command.equals("invadeAbort")) {
			invadeFinish();
		} else if (command.equals("invadeResult")) {
			invadeResult(false);
		} else if (command.equals("invadeResultAndrada")) {
			invadeResult(true);
		}
		
		return true;
	}
	
	private void initForInvasion(SectorEntityToken entity) {
		String key = "$nex_MarketCMD_tempInvasion";
		MemoryAPI mem = entity.getMarket().getMemoryWithoutUpdate();
		if (mem.contains(key)) {
			tempInvasion = (TempDataInvasion) mem.get(key);
		} else {
			mem.set(key, tempInvasion, 0f);
		}
	}
	
	@Override
	protected void clearTemp() {
		super.clearTemp();
		if (tempInvasion != null) {
			tempInvasion.invasionLoot = null;
			tempInvasion.invasionValuables = null;
		}
	}
	
	@Override
	protected void showDefenses(boolean withText) {
		super.showDefenses(withText);
		
		Global.getLogger(this.getClass()).info("Checking entity for invasion: " + entity.getName());
		if (InvasionRound.canInvade(entity))
		{
			options.addOption("Invade the market", INVADE);
			
			// attempt to reorder
			List opts = options.getSavedOptionList();
			Object lastOpt = opts.get(opts.size() - 1);
			opts.remove(lastOpt);
			opts.add(opts.size() - 1, lastOpt);
			options.restoreSavedOptions(opts);
			options.setShortcut(GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);	// put back the hotkey
		}
	}
	
	protected void invadeMenu() {
		tempInvasion.invasionValuables = computeInvasionValuables();
		CampaignFleetAPI fleet = playerFleet;
		
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		
//		dialog.getVisualPanel().showPlanetInfo(market.getPrimaryEntity());
//		dialog.getVisualPanel().finishFadeFast();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

		float marines = playerFleet.getCargo().getMarines();
		float support = Misc.getFleetwideTotalMod(playerFleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		if (support > marines) support = marines;
		
		StatBonus attackerBase = new StatBonus(); 
		StatBonus defenderBase = new StatBonus(); 
		
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		
		ExerelinFactionConfig atkConf = ExerelinConfig.getExerelinFactionConfig(PlayerFactionStore.getPlayerFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		StatBonus attacker = playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		ExerelinFactionConfig defConf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "defendBonus", "$Faction", 
				Misc.ucFirst(market.getFaction().getDisplayName()));
		defender.modifyMult("nex_invasionDefBonus", defConf.invasionStrengthBonusDefend + 1, str);
		
		String increasedDefensesKey = "core_addedDefStr";
		float added = getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, getString("defenderPreparedness", true));
		}
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = (int) Math.round(defender.computeEffective(defenderBase.computeEffective(0f)));
		
		tempInvasion.attackerStr = attackerStr;
		tempInvasion.defenderStr = defenderStr;
		
		TooltipMakerAPI info = text.beginTooltip();
		
		info.setParaSmallInsignia();
		
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		float initPad = 0f;
		if (!hostile) {
			str = getString("nonHostileWarning");
			str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			str = StringHelper.substituteToken(str, "$isOrAre", is);
			info.addPara(str, initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			initPad = opad;
		}
		
		float sep = small;
		sep = 3f;
		str = Misc.ucFirst(getString("invasionStrength"));
		info.addPara(str + ": %s", initPad, h, "" + (int)attackerStr);
		info.addStatModGrid(width, 50, opad, small, attackerBase, true, statPrinter(false));
		if (!attacker.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, sep, attacker, true, statPrinter(true));
		}
		
		str = Misc.ucFirst(getString("groundDefStrength"));
		info.addPara(str + ": %s", opad, h, "" + (int)defenderStr);
		//info.addStatModGrid(width, 50, opad, small, defenderBase, true, statPrinter());
		//if (!defender.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, small, defender, true, statPrinter(true));
		//}
			
		defender.unmodifyFlat(increasedDefensesKey);
		
		text.addTooltip();

		boolean hasForces = true;
		tempInvasion.invasionMult = attackerStr / Math.max(1f, (attackerStr + defenderStr));
		
		if (tempInvasion.invasionMult < 0.25f) {
			text.addPara(getString("insufficientForces"));
			hasForces = false;
		} else {
			Color eColor = h;
			if (tempInvasion.invasionMult < FAIL_THRESHOLD_INVASION) {
				eColor = Misc.getNegativeHighlightColor();
				//temp.canFail = true;
			} else if (tempInvasion.invasionMult >= 0.8f) {
				eColor = Misc.getPositiveHighlightColor();
			}
			text.addPara(Misc.ucFirst(getString("forceBalance")) + ": %s",
					eColor,
					"" + (int)Math.round(tempInvasion.invasionMult * 100f) + "%");
		}
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
		}
		
		options.clearOptions();
		
		options.addOption(getString("invasionProceed"), INVADE_CONFIRM);
		
		// FIXME: magic number
		if (!hasForces) {
			String pct = 25 + "%";
			str = StringHelper.substituteToken(getString("insufficientForcesTooltip"), "$percent", pct);
			options.setTooltip(INVADE_CONFIRM, str);
			options.setEnabled(INVADE_CONFIRM, false);
			options.setTooltipHighlightColors(INVADE_CONFIRM, h);
		}
			
		options.addOption(Misc.ucFirst(StringHelper.getString("goBack")), INVADE_GO_BACK);
		options.setShortcut(INVADE_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	// TODO: sound effect
	protected void invadeRunRound() 
	{
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_disrupt_result", 640, 400, 0, 0, 480, 300);
		
		tempInvasion.roundNum += 1;
		if (tempInvasion.roundNum == 1)
		{
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				Misc.increaseMarketHostileTimeout(market, 30f);
			}

			setRaidCooldown(getRaidCooldownMax());
			
			
			Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), "$nex_recentlyInvaded", 
								   Factions.PLAYER, true, 60f);
		}
		
		InvasionRoundResult result = InvasionRound.execute(playerFleet, market, 
				tempInvasion.attackerStr, tempInvasion.defenderStr, getRandom());
		tempInvasion.stabilityPenalty += InvasionRound.INSTABILITY_PER_ROUND;		
		tempInvasion.attackerStr = Math.max(result.atkStr, 0);
		tempInvasion.defenderStr = Math.max(result.defStr, 0);
				
		//losses = random.nextInt(marines / 2);
		String roundResultMsg = "";
		if (tempInvasion.defenderStr <= 0)
		{
			roundResultMsg = getString("roundResult_win");
			tempInvasion.success = true;
		}
		else if (tempInvasion.attackerStr <= 0)
		{
			roundResultMsg = getString("roundResult_lose");
		}
		else if (result.atkDam > result.defDam * 2)
		{
			roundResultMsg = getString("roundResult_good");
		}
		else if (result.atkDam >= result.defDam)
		{
			roundResultMsg = getString("roundResult_ok");
		}
		else
		{
			roundResultMsg = getString("roundResult_bad");
		}
		roundResultMsg = StringHelper.substituteToken(roundResultMsg, "$market", market.getName());
		text.addPara(roundResultMsg);
		
		// print things that happened during this round
		text.setFontSmallInsignia();
		
		if (result.losses <= 0) {
			text.addPara(getString("noLosses"));
		} else {
			playerFleet.getCargo().removeMarines(result.losses);
			tempInvasion.marinesLost = result.losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, result.losses, text);
		}
		
		text.setFontSmallInsignia();
		// disruption
		if (result.disrupted != null)
		{
			String name = result.disrupted.getSpec().getName();
			String durStr = Math.round(result.disruptionLength) + "";
			String str = getString("industryDisruption");
			str = StringHelper.substituteToken(str, "$industry", name);
			str = StringHelper.substituteToken(str, "$days", durStr);
			text.addPara(str, Misc.getHighlightColor(), name, durStr);
		}
		
		Color hl = tempInvasion.attackerStr > tempInvasion.defenderStr ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		Color hl2 = Misc.getHighlightColor();
		String as = (int)tempInvasion.attackerStr + "";
		String ds = (int)tempInvasion.defenderStr + "";
		String ad = (int)-result.atkDam + "";
		String dd = (int)-result.defDam + "";
		
		String str = getString("attackerStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", as);
		str = StringHelper.substituteToken(str, "$delta", dd);
		text.addPara(str);
		Highlights h = new Highlights();
		h.setColors(hl, result.atkDam >= result.defDam ? hl2 : Misc.getNegativeHighlightColor());
		h.setText(as, dd);
		text.setHighlightsInLastPara(h);
		
		str = getString("defenderStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", ds);
		str = StringHelper.substituteToken(str, "$delta", ad);
		text.addPara(str, hl2, ds, ad);
		
		String marinesRemaining = playerFleet.getCargo().getMarines() + "";
		str = Misc.ucFirst(getString("marinesRemaining")) + ": " + marinesRemaining;
		text.addPara(str, hl2, marinesRemaining);
		
		text.setFontInsignia();
		
		if (tempInvasion.success || tempInvasion.attackerStr <= 0)
		{
			invadeFinish();
		}
		else
		{
			if (tempInvasion.roundNum == 1)
				Global.getSoundPlayer().playUISound("nex_sfx_combat", 1f, 1f);
			options.clearOptions();
			// options: continue or leave
			options.addOption(getString("invasionContinue"), INVADE_CONFIRM);
			options.addOption(getString("invasionAbort"), INVADE_ABORT);
		}
	}
	
	/**
	 * Finish the invasion, apply final effects
	 */
	protected void invadeFinish() {
		Random random = getRandom();
		InvasionRound.finishInvasion(playerFleet, market, tempInvasion.roundNum, tempInvasion.success);
		
		if (!tempInvasion.success)
			applyDefenderIncreaseFromRaid(market);
		
		// unrest
		
		//RecentUnrest.get(market).add(3, Misc.ucFirst(reason));
		int stabilityPenalty = tempInvasion.stabilityPenalty;
		if (!tempInvasion.success)
			stabilityPenalty /= 2;
		
		if (stabilityPenalty > 0) {
			text.addPara(StringHelper.substituteToken(getString("stabilityReduced"), 
					"$market", market.getName()), Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		// reputation impact
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		impact.ensureAtBest = RepLevel.HOSTILE;
		Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		// handle loot
		String contText = null;
		
		float targetValue = getBaseInvasionValue();
		CargoAPI result = Global.getFactory().createCargo(true);
		
		// loot
		if (tempInvasion.success)
		{
			WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<CommodityOnMarketAPI>(random);
			for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
				picker.add(com, tempInvasion.invasionValuables.get(com));
			}

			//float chunks = 10f;
			float chunks = tempInvasion.invasionValuables.size();
			if (chunks > 6) chunks = 6;
			for (int i = 0; i < chunks; i++) {
				float chunkValue = targetValue * 1f / chunks;
				float randMult = StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f);
				chunkValue *= randMult;

				CommodityOnMarketAPI pick = picker.pick();
				int quantity = (int) (chunkValue / pick.getCommodity().getBasePrice());
				if (quantity <= 0) continue;
				
				// handled in InvasionRound
				//pick.addTradeModMinus("invasion_" + Misc.genUID(), -quantity, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);

				result.addCommodity(pick.getId(), quantity);
			}

			raidSpecialItemsReduced(result, random);

			result.sort();

			tempInvasion.invasionLoot = result;

			tempInvasion.invasionCredits = (int)(targetValue * 0.1f * StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f));
			if (tempInvasion.invasionCredits < 2) tempInvasion.invasionCredits = 2;

			//result.clear();
			if (result.isEmpty()) {
				text.addPara(getString("endMsgNoLoot"));
			} else {
				text.addPara(getString("endMsgLoot"));
				AddRemoveCommodity.addCreditsGainText(tempInvasion.invasionCredits, text);
				playerFleet.getCargo().getCredits().add(tempInvasion.invasionCredits);
				contText = getString("invasionSpoils");
			}

			ListenerUtil.reportRaidForValuablesFinishedBeforeCargoShown(dialog, market, temp, tempInvasion.invasionLoot);
		}
		else
		{
			text.addPara(getString("endMsgDefeat"));
			contText = StringHelper.substituteToken(getString("invasionFail"), "$market", market.getName());
		}
		
		Global.getSoundPlayer().playUISound("ui_raid_finished", 1f, 1f);
		
		addContinueOptionInvasion(contText);
	}
	
	protected void addContinueOptionInvasion(String text) {
		if (text == null) text = Misc.ucFirst(StringHelper.getString("continue"));
		options.clearOptions();
		options.addOption(text, INVADE_RESULT);
		
		if (tempInvasion.success && playerFaction != PlayerFactionStore.getPlayerFaction())
		{
			String str = getString("invasionTakeForSelf");
			String marketName = market.getName();
			str = StringHelper.substituteToken(str, "$market", marketName);
			options.addOption(str, INVADE_RESULT_ANDRADA);
			
			str = getString("takeForSelfWarning");
			str = StringHelper.substituteToken(str, "$market", marketName);
			options.addOptionConfirmation(INVADE_RESULT_ANDRADA, str, 
					Misc.ucFirst(StringHelper.getString("yes")),
					Misc.ucFirst(StringHelper.getString("no")));
		}
	}
	
	protected Map<CommodityOnMarketAPI, Float> computeInvasionValuables() {
		Map<CommodityOnMarketAPI, Float> result = new HashMap<CommodityOnMarketAPI, Float>();
		float totalDemand = 0f;
		float totalShortage = 0f;
		for (CommodityOnMarketAPI com : market.getAllCommodities()) {
			if (com.isPersonnel()) continue;
			if (com.getCommodity().hasTag(Commodities.TAG_META)) continue;
			
			int a = com.getAvailable();
			if (a > 0) {
				float num = BaseIndustry.getSizeMult(a) * com.getCommodity().getEconUnit() * 0.5f;
				result.put(com, num);
			}
			
			float max = com.getMaxDemand();
			totalDemand += max;
			totalShortage += Math.max(0, max - a);
		}
		
		tempInvasion.shortageMult = 1f;
		if (totalShortage > 0 && totalDemand > 0) {
			tempInvasion.shortageMult = Math.max(0, totalDemand - totalShortage) / totalDemand;
		}
		
		return result;
	}
	
	protected float getBaseInvasionValue() {
		float targetValue = 0f;
		for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
			targetValue += tempInvasion.invasionValuables.get(com) * com.getCommodity().getBasePrice();
		}
		targetValue *= 0.1f;
		targetValue *= tempInvasion.invasionMult;
		targetValue *= tempInvasion.shortageMult;
		return targetValue;
	}
	
	/**
	 * Loot rare items, but not AI cores or stuff like nanoforge/synchrotron
	 * @param cargo
	 * @param random
	 */
	protected void raidSpecialItemsReduced(CargoAPI cargo, Random random) {
		float p = tempInvasion.invasionMult * 0.2f;
		
		boolean withBP = false;
		boolean heavyIndustry = false;
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_USES_BLUEPRINTS)) {
				withBP = true;
			}
			if (curr.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
				heavyIndustry = true;
			}
		}
		market.reapplyIndustries();
		
		boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
		
		String ship =    "MarketCMD_ship____";
		String weapon =  "MarketCMD_weapon__";
		String fighter = "MarketCMD_fighter_";
		
		// blueprints
		if (withBP) {
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
			for (String id : market.getFaction().getKnownShips()) {
				if (playerFaction.knowsShip(id)) continue;
				picker.add(ship + id, 1f);
			}
			for (String id : market.getFaction().getKnownWeapons()) {
				if (playerFaction.knowsWeapon(id)) continue;
				picker.add(weapon + id, 1f);
			}
			for (String id : market.getFaction().getKnownFighters()) {
				if (playerFaction.knowsFighter(id)) continue;
				picker.add(fighter + id, 1f);
			}
			
			int num = getNumPicks(random, tempInvasion.invasionMult * 0.25f, tempInvasion.invasionMult * 0.5f);
			for (int i = 0; i < num && !picker.isEmpty(); i++) {
				String id = picker.pickAndRemove();
				if (id == null) continue;
				
				if (id.startsWith(ship)) {
					String specId = id.substring(ship.length());
					if (Global.getSettings().getHullSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, specId), 1);
				} else if (id.startsWith(weapon)) {
					String specId = id.substring(weapon.length());
					if (Global.getSettings().getWeaponSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, specId), 1);
				} else if (id.startsWith(fighter)) {
					String specId = id.substring(fighter.length());
					if (Global.getSettings().getFighterWingSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, specId), 1);
				}
			}
		}
		
		// modspecs
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
		for (String id : market.getFaction().getKnownHullMods()) {
			if (playerFaction.knowsHullMod(id) && !DebugFlags.ALLOW_KNOWN_HULLMOD_DROPS) continue;
			picker.add(id, 1f);
		}
		
		// more likely to get at least one modspec, but not likely to get many
		int num = getNumPicks(random, tempInvasion.invasionMult * 0.5f, tempInvasion.invasionMult * 0.25f);
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			cargo.addSpecial(new SpecialItemData(Items.MODSPEC, id), 1);
		}
		
		
		// weapons and fighters
		picker = new WeightedRandomPicker<String>();
		for (String id : market.getFaction().getKnownWeapons()) {
			WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id);
			if (w.hasTag("no_drop")) continue;
			if (w.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
			
			if (!military && !heavyIndustry && 
					(w.getTier() > 1 || w.getSize() == WeaponAPI.WeaponSize.LARGE)) continue;
			
			picker.add(weapon + id, w.getRarity());
		}
		for (String id : market.getFaction().getKnownFighters()) {
			FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
			if (f.hasTag(Tags.WING_NO_DROP)) continue;
			
			if (!military && !heavyIndustry && f.getTier() > 0) continue;
			
			picker.add(fighter + id, f.getRarity());
		}
		
		
		num = getNumPicks(random, tempInvasion.invasionMult * 0.5f, tempInvasion.invasionMult * 0.25f);
		if (military || heavyIndustry) {
			num += Math.round(market.getCommodityData(Commodities.SHIPS).getAvailable() * tempInvasion.invasionMult);
		}
		
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			
			if (id.startsWith(weapon)) {
				cargo.addWeapons(id.substring(weapon.length()), 1);
			} else if (id.startsWith(fighter)) {
				cargo.addFighters(id.substring(fighter.length()), 1);
			}
		}
		
	}
	
	/**
	 * Show loot if applicable, cleanup and exit
	 * @param tookForSelf True if we decided to take the market for ourselves, 
	 * instead of turning it over to commissioning faction
	 * Note: this is always false if we have no commission
	 */
	protected void invadeResult(boolean tookForSelf)
	{
		tempInvasion.tookForSelf = tookForSelf;
		
		if (tempInvasion.invasionLoot != null) {
			if (tempInvasion.invasionLoot.isEmpty()) {
				finishedInvade();
			} else {
				invadeShowLoot();
			}
			return;
		} else {
			finishedInvade();
		}
	}
	
	protected void finishedInvade() {
		clearTemp();
		//showDefenses(true);
	
		new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
		
		if (tempInvasion.tookForSelf)
		{
			InvasionRound.conquerMarket(market, playerFaction, true);
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = -0.04f * market.getSize();
			//impact.ensureAtBest = RepLevel.SUSPICIOUS;
			impact.limit = RepLevel.INHOSPITABLE;
			Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
					CoreReputationPlugin.RepActions.CUSTOM, impact, null, text, true), 
					PlayerFactionStore.getPlayerFactionId());
		}
		else 
			InvasionRound.conquerMarket(market, PlayerFactionStore.getPlayerFaction(), true);
		
		//FireAll.fire(null, dialog, memoryMap, "MarketPostOpen");
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$menuState", "main", 0);
		if (market.isPlanetConditionMarketOnly()) {
			dialog.getInteractionTarget().getMemoryWithoutUpdate().unset("$hasMarket");
		}
		else
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
		
		FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
		
		// create player faction setup screein if needed
		// FIXME nothing I do works, work around it for now
		if (!Misc.isPlayerFactionSetUp())
		{
			playerFaction.setDisplayNameOverride(Misc.ucFirst(StringHelper.getString("player")));
			//new Nex_SetupFaction().execute(null, dialog, new ArrayList<Token>(), memoryMap);
		}
	}
	
	protected void invadeShowLoot() {
		dialog.getVisualPanel().showLoot(Misc.ucFirst(StringHelper.getString("spoils")),
				tempInvasion.invasionLoot, false, true, true, new CoreInteractionListener() {
			public void coreUIDismissed() {
				//dialog.dismiss();
				finishedInvade();
			}
		});
	}
	
	// this was just to debug Tiandong
	/*
	@Override
	protected int getNumPicks(Random random, float pAny, float pMore) {
		return 20;
	}
	*/
	
	// TBD
	public static void reportInvasionFinished(InteractionDialogAPI dialog, MarketAPI market, TempDataInvasion actionData) {
		//for (ColonyPlayerHostileActListener x : Global.getSector().getListenerManager().getListeners(ColonyPlayerHostileActListener.class)) {
		//	x.reportSaturationBombardmentFinished(dialog, market, actionData);
		//}
	}
	
	public static class TempDataInvasion {
		public boolean canInvade;
		public int marinesLost = 0;
		public int roundNum = 0;
		public int stabilityPenalty = 0;
		public boolean success = false;
		public boolean tookForSelf = true;
		
		public float invasionMult;
		public float shortageMult;
		
		public float attackerStr;
		public float defenderStr;
		
		public Map<CommodityOnMarketAPI, Float> invasionValuables;
		public CargoAPI invasionLoot;
		public int invasionCredits;
	}
}
