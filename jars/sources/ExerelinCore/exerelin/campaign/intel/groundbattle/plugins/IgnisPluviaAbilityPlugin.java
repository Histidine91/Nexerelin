package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.groundbattle.*;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.intel.groundbattle.plugins.FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT;
import static exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry;

@Log4j
public class IgnisPluviaAbilityPlugin extends AbilityPlugin {
	
	public static final boolean ALLOW_ANY_OLYMPUS = true;	// doesn't require Fundae or Apocalypse MIRV mode
	public static final float ENTRY_HEIGHT = 48;
	public static final float BUTTON_WIDTH = 80;
	public static float BASE_DAMAGE = 32;	// for comparison, fire support on a size 6 does 72
	public static int NUM_HITS = 24;
	public static float CR_TO_FIRE = 0.3f;
	public static float DISRUPT_TIME_MULT = 0.5f;
	
	public static final Set<String> OLYMPUS_IDS = new HashSet<>();
	
	static {
		OLYMPUS_IDS.add("ii_olympus");
		OLYMPUS_IDS.add("ii_boss_titanx");
	}
	
	public transient FleetMemberAPI olympus;
	
	@Override
	public void activate(InteractionDialogAPI dialog, PersonAPI user) {
		super.activate(dialog, user);
		
		Set<GroundUnit> enemies = new HashSet<>();
		float totalDamage = 0;
		
		logActivation(user);	// so it displays before the unit destruction messages, if any
		
		List<IndustryForBattle> targets = getTargetIndustries();
		Set<IndustryForBattle> skippedOnce = new HashSet<>();	// ground defenses will get to skip one hit they would otherwise take
		Set<IndustryForBattle> disrupted = new LinkedHashSet<>();
		
		WeightedRandomPicker<IndustryForBattle> targetPicker = new WeightedRandomPicker<>();
		
		for (int i=0; i < NUM_HITS; i++) {
			if (targetPicker.isEmpty()) targetPicker.addAll(targets);
			IndustryForBattle ifb = targetPicker.pickAndRemove();
			
			if (ifb.heldByAttacker != getSide().isAttacker() && !ifb.isIndustryTrueDisrupted() 
					&& ifb.getPlugin().getDef().hasTag("noBombard") && !skippedOnce.contains(ifb)) {
				skippedOnce.add(ifb);
				continue;
			}				
			
			float damage = BASE_DAMAGE;
			float attrition = getSide().getDropAttrition().getModifiedValue()/100;
			damage *= 1-attrition;
			if (ifb.isContested()) damage *= CLOSE_SUPPORT_DAMAGE_MULT;

			for (GroundUnit unit : ifb.getUnits()) {
				if (unit.isAttacker() != side.isAttacker()) {
					enemies.add(unit);
				}
			}

			boolean enemyHeld = ifb.heldByAttacker != side.isAttacker();
			
			GroundBattleRoundResolve resolve = new GroundBattleRoundResolve(getIntel());
			resolve.distributeDamage(ifb, !side.isAttacker(), Math.round(damage));
			for (GroundUnit unit : new ArrayList<>(ifb.getUnits())) {
				if (unit.getSize() <= 0)
					unit.destroyUnit(0);
				else
					resolve.checkReorganize(unit);
			}
			//log.info("Applying " + damage + " damage on " + ifb.getName());
			totalDamage += damage;
			
			boolean canDisrupt = !ifb.getPlugin().getDef().hasTag("resistBombard") 
					&& !ifb.getPlugin().getDef().hasTag("noBombard");
			if (enemyHeld && canDisrupt) 
			{
				Industry ind = ifb.getIndustry();
				float disruptTime = getDisruptionTime(ind) * DISRUPT_TIME_MULT;
				ind.setDisrupted(disruptTime + ind.getDisruptedDays(), true);
				disrupted.add(ifb);
			}
		}
		
		// print results
		if (dialog != null) {
			Color h = Misc.getHighlightColor();
			dialog.getTextPanel().setFontSmallInsignia();
			dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_bombard_result1"), 
					h, Math.round(totalDamage) + "", enemies.size() + "");
			
			List<String> disruptedNames = new ArrayList<>();
			for (IndustryForBattle ifb : disrupted) {
				disruptedNames.add(ifb.getName());
			}
			String str = String.format(GroundBattleIntel.getString("ability_ignisPluvia_resultDisrupt"), 
					StringHelper.writeStringCollection(disruptedNames));
			dialog.getTextPanel().addPara(str, h, disruptedNames.toArray(new String[0]));
		}
		
		olympus.getRepairTracker().applyCREvent(-CR_TO_FIRE, "nex_ignisPluvia", getDef().name);

		getIntel().reapply();

		if (dialog != null)
			dialog.getTextPanel().setFontInsignia();
		
		olympus = null;
	}
	
	@Override
	public List<IndustryForBattle> getTargetIndustries() {
		List<IndustryForBattle> targets = new ArrayList<>();
		for (IndustryForBattle ifb : getIntel().getIndustries()) {
			if (!ifb.containsEnemyOf(side.isAttacker())) continue;
			targets.add(ifb);
		}
		return targets;
	}
	
	public float getDisruptionTime(Industry ind) {
		return ind.getSpec().getDisruptDanger().disruptionDays;
	}
	
	@Override
	public void dialogAddIntro(InteractionDialogAPI dialog) {
		dialog.getTextPanel().addPara(GroundBattleIntel.getString("ability_ignisPluvia_tooltip1"));
		TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
		generateTooltip(tooltip);
		dialog.getTextPanel().addTooltip();
		
		addCooldownDialogText(dialog);
	}

	@Override
	public void dialogAddVisualPanel(final InteractionDialogAPI dialog) {
		float pad = 0;
		float width = Nex_VisualCustomPanel.PANEL_WIDTH - 4;
		Color h = Misc.getHighlightColor();
		FactionAPI faction = PlayerFactionStore.getPlayerFaction();
		Color base = faction.getBaseUIColor();
		Color dark = faction.getDarkUIColor();
		Color bright = faction.getBrightUIColor();
		
		Nex_VisualCustomPanel.createPanel(dialog, true);
		CustomPanelAPI panel = Nex_VisualCustomPanel.getPanel();
		TooltipMakerAPI info = Nex_VisualCustomPanel.getTooltip();
		InteractionDialogCustomPanelPlugin plugin = Nex_VisualCustomPanel.getPlugin();
		
		List<FleetMemberAPI> olympi = getOlympi(Global.getSector().getPlayerFleet());
		
		List<RadioButtonEntry> allButtons = new ArrayList<>();
		
		for (final FleetMemberAPI member : olympi) 
		{
			CustomPanelAPI itemPanel = panel.createCustomPanel(width, ENTRY_HEIGHT, null);
			TooltipMakerAPI image = NexUtilsGUI.createFleetMemberImageForPanel(itemPanel, member, ENTRY_HEIGHT, ENTRY_HEIGHT);
			itemPanel.addUIElement(image).inTL(4, 0);
			
			TooltipMakerAPI text = itemPanel.createUIElement(width - PANEL_WIDTH - 4, ENTRY_HEIGHT, false);
			String name = member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass();
			text.addPara(name, 0, h, member.getShipName());
			
			float cr = member.getRepairTracker().getCR();
			boolean enough = haveEnoughCR(member);
			String crStr = StringHelper.toPercent(cr);
			LabelAPI label = text.addPara(crStr + " " + StringHelper.getString("CR"), pad);
			label.setHighlight(crStr);
			label.setHighlightColor(enough ? h : Misc.getNegativeHighlightColor());
			
			if (!isValidOlympusConfig(member)) {
				text.addPara(GroundBattleIntel.getString("ability_ignisPluvia_invalidConfig"), pad);
			}
			else if (enough) {
				ButtonAPI button = text.addAreaCheckbox(StringHelper.getString("select", true), 
					member, base, dark, bright, BUTTON_WIDTH, 16, pad);
				button.setChecked(olympus == member);
				RadioButtonEntry rbe = new RadioButtonEntry(button, "select_" + member.getId()) {
					@Override
					public void onToggleImpl() {
						olympus = member;
						//dialogAddVisualPanel(dialog);
						dialogSetEnabled(dialog);
					}
				};
				plugin.addButton(rbe);
				allButtons.add(rbe);
			}
			
			itemPanel.addUIElement(text).rightOfTop(image, 3);
			
			info.addCustom(itemPanel, 3);
			//dialog.getTextPanel().addPara("Adding Olympus " + member.getShipName());
		}
		for (RadioButtonEntry rbe : allButtons) {
			rbe.buttons = allButtons;
		}
		Nex_VisualCustomPanel.addTooltipToPanel();
	}
	
	@Override
	public void addDialogOptions(InteractionDialogAPI dialog) {
		super.addDialogOptions(dialog);
		dialogSetEnabled(dialog);
	}
	
	protected void dialogSetEnabled(InteractionDialogAPI dialog) {
		dialog.getOptionPanel().setEnabled(AbilityDialogPlugin.OptionId.ACTIVATE, olympus != null);
	}
	
	@Override
	public void dialogOnDismiss(InteractionDialogAPI dialog) {
		olympus = null;
	}

	@Override
	public void generateTooltip(TooltipMakerAPI tooltip) {
		float opad = 10;
		Color h = Misc.getHighlightColor();
		float attrition = getSide().getDropAttrition().getModifiedValue()/100;
		//tooltip.addPara(GroundBattleIntel.getString("ability_ignisPluvia_tooltip1"), 0);
		String str = GroundBattleIntel.getString("ability_ignisPluvia_tooltip2");
		str = StringHelper.substituteToken(str, "$market", side.getIntel().getMarket().getName());
		tooltip.addPara(str, 0, h, NUM_HITS + "", (int)BASE_DAMAGE + "", StringHelper.toPercent(attrition),
				StringHelper.toPercent(FireSupportAbilityPlugin.CLOSE_SUPPORT_DAMAGE_MULT - 1));
		tooltip.addPara(GroundBattleIntel.getString("ability_ignisPluvia_tooltip3"), opad);
		tooltip.addPara(GroundBattleIntel.getString("ability_ignisPluvia_tooltip4"), opad,
				h, StringHelper.toPercent(CR_TO_FIRE));
	}
		
	@Override
	public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
		Map<String, Object> params = new HashMap<>();
		CampaignFleetAPI fleet = null;
		if (user != null) {
			if (user.isPlayer()) fleet = Global.getSector().getPlayerFleet();
			else fleet = user.getFleet();
		}
		
		if (side.getData().containsKey(GBConstants.TAG_PREVENT_BOMBARDMENT_SUPER)) {
			
			String id = "bombardmentPrevented";
			String desc = GroundBattleIntel.getString("ability_ignisPluvia_prevented");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
		if (fleet != null) {
			List<FleetMemberAPI> olympi = getOlympi(fleet);
			//log.info("Number of Olympi: " + olympi.size());
			if (olympi.isEmpty()) {
			
				String id = "noMembers";
				String desc = GroundBattleIntel.getString("ability_ignisPluvia_noMembers");
				params.put("desc", desc);
				return new Pair<>(id, params);
			}
		}
		float[] strengths = getNearbyFleetStrengths();
		{
			float ours = strengths[0];
			float theirs = strengths[1];
			if (ours < theirs * 2) {			
				String id = "enemyPresence";
				String ourStr = String.format("%.0f", ours);
				String theirStr = String.format("%.0f", theirs);
				
				String desc = String.format(GroundBattleIntel.getString("ability_bombard_enemyPresence"), ourStr, theirStr);
				params.put("desc", desc);
				return new Pair<>(id, params);
			}
		}
		if (getTargetIndustries().isEmpty()) {
			String id = "noTargets";
			String desc = GroundBattleIntel.getString("ability_ignisPluvia_noTargets");
			params.put("desc", desc);
			return new Pair<>(id, params);
		}
				
		Pair<String, Map<String, Object>> reason = super.getDisabledReason(user);
		return reason;
	}
	
	@Override
	public boolean showIfDisabled(Pair<String, Map<String, Object>> disableReason) {
		return hasAnyOlympi(Global.getSector().getPlayerFleet());
	}
	
	@Override
	public boolean hasActivateConfirmation() {
		return true;
	}
	
	@Override
	public boolean shouldCloseDialogOnActivate() {
		return false;
	}
	
	@Override
	public float getAIUsePriority(GroundBattleAI ai) {
		return 30;
	}
	
	@Override
	public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
		// find a fleet to execute ability
		// NPCs can't use player fleet
		List<CampaignFleetAPI> fleets = getIntel().getSupportingFleets(side.isAttacker());
		if (fleets.isEmpty()) return false;
		
		CampaignFleetAPI fleet = null;
		olympus = null;
		
		for (CampaignFleetAPI candidate : fleets) {
			if (candidate.isPlayerFleet()) continue;
			if (candidate.getAI() != null) {
				if (candidate.getAI().isFleeing() || candidate.getAI().isMaintainingContact())
					continue;
			}
			for (FleetMemberAPI member : getOlympi(candidate)) {
				if (haveEnoughCR(member)) {
					olympus = member;
					fleet = candidate;
					break;
				}
			}
			if (olympus != null) break;
		}
		
		if (olympus == null) return false;
		user = fleet.getCommander();
		return super.aiExecute(ai, user);
	}
	
	public boolean hasAnyOlympi(CampaignFleetAPI fleet) {
		for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy())
		{
			if (isOlympus(member)) return true;
		}
		return false;
	}
	
	public List<FleetMemberAPI> getOlympi(CampaignFleetAPI fleet) 
	{
		List<FleetMemberAPI> members = new ArrayList<>();
		if (fleet == null) return members;
		for (FleetMemberAPI candidate : fleet.getFleetData().getCombatReadyMembersListCopy())
		{
			if (isOlympus(candidate)) {
				members.add(candidate);
			}
		}
		return members;
	}
	
	public boolean isOlympus(FleetMemberAPI member) {
		String hullId = member.getHullSpec().getBaseHullId();
		if (hullId == null) hullId = member.getHullSpec().getHullId();
		
		if (!OLYMPUS_IDS.contains(hullId)) return false;
		
		return true;
	}
	
	public boolean isValidOlympusConfig(FleetMemberAPI member) {
		if (ALLOW_ANY_OLYMPUS) return true;
		
		String hullId = member.getHullSpec().getBaseHullId();
		if (hullId == null) hullId = member.getHullSpec().getHullId();
		if (hullId.equals("ii_olympus"))
			return member.getVariant().hasHullMod("ii_armor_package") || member.getVariant().hasHullMod("ii_targeting_package");
		
		return true;
	}
	
	public boolean haveEnoughCR(FleetMemberAPI member) {
		String hullId = member.getHullSpec().getBaseHullId();
		if (hullId == null) hullId = member.getHullSpec().getHullId();
		
		if (!OLYMPUS_IDS.contains(hullId)) return false;
		if (true || !hullId.equals("ii_boss_titanx")) {
			return member.getRepairTracker().getCR() >= CR_TO_FIRE;
		}
		
		return true;
	}
}
