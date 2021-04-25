package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.intel.specialforces.namer.PlanetNamer;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class GroundUnit {
	
	public static final float PANEL_WIDTH = 220, PANEL_HEIGHT = 120;
	public static final float TITLE_HEIGHT = 16, LOCATION_SECTION_HEIGHT = 32;
	public static final float PADDING_X = 4;
	public static final float BUTTON_SECTION_WIDTH = 64;
	
	public static final float HEAVY_COUNT_DIVISOR = 8.5f;	// a marine platoon has 8.5x as many marines as a mech platoon has mechs
	public static final int CREW_PER_MECH = 2;
		
	public final String id = Misc.genUID();
	protected int index;
	protected GroundBattleIntel intel;
	protected String name;
	protected FactionAPI faction;
	protected boolean isPlayer;
	protected boolean isAttacker;
	protected ForceType type;
	protected int men;
	protected int heavyArms;
	protected int lossesLastTurn;
	protected float moraleDeltaLastTurn;
	protected float morale = 0.8f;
	protected String currAction;
	
	public Map<String, Object> data = new HashMap<>();
	
	protected IndustryForBattle location;
	protected IndustryForBattle dest;
	
	public GroundUnit(GroundBattleIntel intel, ForceType type, int num, int index) {
		this.intel = intel;
		this.type = type;
		this.index = index;
		if (type == ForceType.HEAVY) {
			heavyArms = num;
			men = num * CREW_PER_MECH;
		}
		else {
			men = num;
		}
		name = generateName();
	}
	
	public String generateName() {
		String name = Misc.ucFirst(intel.unitSize.getName());
		switch (intel.unitSize) {
			case PLATOON:
			case COMPANY:
				int alphabetIndex = this.index % 26;
				return GBDataManager.NATO_ALPHABET.get(alphabetIndex) + " " + name;
			case BATALLION:
				return index + PlanetNamer.getSuffix(index) + " " + name;
			case REGIMENT:
				return Global.getSettings().getRoman(index) + " " + name;
			default:
				return name + " " + index;
		}
	}
	
	public IndustryForBattle getLocation() {
		return location;
	}
	
	public void setLocation(IndustryForBattle newLoc) {
		if (newLoc == location) return;
		if (location != null) location.removeUnit(this);
		location = newLoc;
		if (newLoc != null) newLoc.addUnit(this);
	}
	
	public void setDestination(IndustryForBattle dest) {
		this.dest = dest;
		currAction = null;
	}
	
	public void orderWithdrawal() {
		currAction = GBConstants.ACTION_WITHDRAW;
		dest = null;
	}
	
	public void executeMove() {
		if (GBConstants.ACTION_WITHDRAW.equals(currAction)) {
			setLocation(null);
			currAction = null;
			return;
		}
		
		if (dest == null) return;
		setLocation(dest);
		dest = null;
	}
	
	public void removeUnit(boolean returnToCargo) 
	{
		if (isPlayer && returnToCargo) {
			Global.getSector().getPlayerFleet().getCargo().addMarines(men);
			Global.getSector().getPlayerFleet().getCargo().addCommodity(Commodities.HAND_WEAPONS, heavyArms);
		}
		setLocation(null);
		intel.getSide(isAttacker).units.remove(this);
		if (isPlayer)
			intel.playerUnits.remove(this);
		
		Global.getLogger(this.getClass()).info(String.format("Removed unit %s (%s)", name, type));
	}
	
	public void destroyUnit(float recoverProportion) {
		if (isPlayer) {
			Global.getSector().getPlayerFleet().getCargo().addMarines(
					(int)(men * recoverProportion));
			Global.getSector().getPlayerFleet().getCargo().addCommodity(
					Commodities.HAND_WEAPONS, (int)(heavyArms * recoverProportion));
		}
		removeUnit(false);
	}
	
	/**
	 * Spend an additional {@code turns} reorganizing. Negative values to progress reorganization.
	 * @param turns
	 */
	public void reorganize(int turns) {
		Integer curr = (Integer)data.get("reorganizing");
		if (curr == null) curr = 0;
		int newVal = curr + turns;
		if (newVal <= 0) data.remove("reorganizing");
		else data.put("reorganizing", newVal);
	}
	
	public void addActionText(TooltipMakerAPI info) {
		Color color = Misc.getTextColor();
		String text = "Waiting";
		if (data.containsKey("reorganizing")) {
			text = "Reorganizing";
			color = Misc.getNegativeHighlightColor();
		}
		else if (GBConstants.ACTION_WITHDRAW.equals(currAction)) {
			text = "Withdrawing";
		}
		else if (dest != null) {
			text = "Moving to " + dest.ind.getCurrentName();
		}
		else if (location == null) {
			text = "Awaiting deployment";
		}
		else if (location.containsEnemyOf(isAttacker)) {
			text = "Engaged at " + location.ind.getCurrentName();
		}
		info.addPara(text, color, 3);
	}
	
	/**
	 * Clamped to range [0, 1].
	 * @param delta
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta) {
		return modifyMorale(delta, 1);
	}
	
	/**
	 * Clamped to range [0, max].
	 * @param delta
	 * @param max
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta, float max) {
		float newMorale = morale + delta;
		if (newMorale > max) newMorale = max;
		if (newMorale < 0) newMorale = 0;
		
		// prevent "going backwards"
		if (delta > 0 && newMorale < morale) {
			newMorale = morale;
		}
		else if (delta < 0 && newMorale > morale) {
			newMorale = morale;
		}
		
		delta = newMorale - morale;
		
		morale = newMorale;
		moraleDeltaLastTurn += delta;
		return delta;
	}
	
	public float getBaseStrength() {
		return getSize() * type.strength;
	}
	
	public int getDeployCost() {
		return Math.round(getBaseStrength() * GBConstants.SUPPLIES_TO_DEPLOY_MULT);
	}
	
	public float getAttackStrength() {
		
		MutableStat stat = new MutableStat(0);
		
		float baseStr = getBaseStrength();
		stat.modifyFlat("base", baseStr, "Base strength");
		
		IndustryForBattle ifb = location;
		
		if (ifb.heldByAttacker == isAttacker) 
		{
			// apply strength modifiers to defense instead, not attack
			/*
			float industryMult = ifb.getPlugin().getStrengthMult();
			if (industryMult != 1) {
				stat.modifyMult("industry", industryMult, ifb.ind.getCurrentName());
			}
			*/
		} 
		else {	// heavy unit bonus on offensive
			if (type == ForceType.HEAVY) {
				stat.modifyMult("heavy_offensive", GBConstants.HEAVY_OFFENSIVE_MULT, "Mechanized assault");
			}
		}
		
		if (intel.market.getPlanetEntity() == null) {
			if (type == ForceType.HEAVY) {
				stat.modifyMult("heavy_cramped", GBConstants.HEAVY_STATION_MULT, "Close quarters");
			}
		}
		
		float moraleMult = NexUtilsMath.lerp(1 - GBConstants.MORALE_ATTACK_MOD, 
				1 + GBConstants.MORALE_ATTACK_MOD, morale);
		stat.modifyMult("morale", moraleMult, "Morale");
		
		// size modifiers for damage
		stat.modifyMult("unitSize", intel.unitSize.damMult, "Unit size: " + intel.unitSize.getName());
		
		float output = stat.getModifiedValue();
		
		PersonAPI commander = intel.getCommander(this);
		if (commander != null) {
			output = commander.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).computeEffective(output);
		}
		output = intel.getSide(isAttacker).damageDealtMod.computeEffective(output);
		
		return output;
	}
	
	public float getAdjustedMoraleDamageTaken(float dam) {
		float mult = 1;
		if (isPlayer) {
			//mult -= GBConstants.MORALE_DAM_XP_REDUCTION_MULT * PlayerFleetPersonnelTracker.getInstance().getMarineData().getXPLevel();
		}
		mult /= type.moraleMult;
		dam = intel.getSide(isAttacker).moraleDamTakenMod.computeEffective(dam);
		dam *= mult;
		return dam;
	}
	
	public float getAdjustedDamageTaken(float dmg) {
		if (location != null && isAttacker == location.heldByAttacker)
			dmg *= 1/location.getPlugin().getStrengthMult();
		
		PersonAPI commander = intel.getCommander(this);
		if (commander != null) {
			dmg *= commander.getStats().getDynamic().getStat(Stats.PLANETARY_OPERATIONS_CASUALTIES_MULT).getModifiedValue();
		}
		
		dmg = intel.getSide(isAttacker).damageTakenMod.computeEffective(dmg);
		return dmg;
	}
	
	public int getSize() {
		int num = type == ForceType.HEAVY ? heavyArms : men;
		return num;
	}
	
	/**
	 * e.g. two half-companies and one full company will return about the same value.
	 * @return
	 */
	public float getNumUnitEquivalents() {
		float num = type == ForceType.HEAVY ? heavyArms : men;
		return num/intel.unitSize.getAverageSizeForType(type);
	}
	
	public static TooltipMakerAPI createBlankCard(CustomPanelAPI parent) {
		FactionAPI faction = Global.getSector().getPlayerFaction();
		TooltipMakerAPI cardHolder = parent.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, null, faction.getCrest()));
				
		TooltipMakerAPI buttonHolder = card.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		buttonHolder.addButton("Create unit", "bla", 120, 24, 0);
		card.addUIElement(buttonHolder).inTL(PANEL_WIDTH/2 - 60, PANEL_HEIGHT/2 - 12);
		cardHolder.addCustom(card, 0);
		
		return cardHolder;
	}
	
	public TooltipMakerAPI createUnitCard(CustomPanelAPI parent)
	{
		String commoditySprite = Global.getSettings().getCommoditySpec(type.commodityId).getIconName();
		String crest = faction.getCrest();
		
		TooltipMakerAPI cardHolder = parent.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, commoditySprite, crest));
		TooltipMakerAPI title = card.createUIElement(PANEL_WIDTH, TITLE_HEIGHT, false);
		title.addPara(name, faction.getBaseUIColor(), 3);
		card.addUIElement(title).inTL(0, 0);
		
		// begin stats section
		TooltipMakerAPI stats = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH/2), 
				PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT, false);
		
		// number of marines
		TooltipMakerAPI line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.MARINES).getIconName(), 16);
		line.addPara(men + "", 0);
		stats.addImageWithText(3);
		stats.addTooltipToPrevious(createTooltip("marines"), TooltipLocation.BELOW);
		
		// number of heavy arms
		if (heavyArms > 0) {
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS).getIconName(), 16);
			line.addPara(heavyArms + "", 0);
			stats.addImageWithText(3);
			stats.addTooltipToPrevious(createTooltip("heavyArms"), TooltipLocation.BELOW);
		}
		else {
			stats.addSpacer(19);
		}
		
		// morale
		line = stats.beginImageWithText("graphics/icons/insignia/16x_star_circle.png", 16);
		Color moraleColor = Misc.getHighlightColor();
		if (morale > .6) moraleColor = Misc.getPositiveHighlightColor();
		else if (morale < .3) moraleColor = Misc.getNegativeHighlightColor();
		String moraleStr = Math.round(this.morale * 100) + "%";
		line.addPara(moraleStr, moraleColor, 0);
		stats.addImageWithText(3);
		stats.addTooltipToPrevious(createTooltip("morale"), TooltipLocation.BELOW);
		
		card.addUIElement(stats).belowLeft(title, 0);
		
		TooltipMakerAPI stats2 = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH/2), 
				PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT, false);
		if (location == null) {
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(Commodities.SUPPLIES).getIconName(), 16);
			line.addPara(getDeployCost() + "", 0);
			stats.addImageWithText(3);
			stats.addTooltipToPrevious(createTooltip("supplies"), TooltipLocation.BELOW);
		}
		
		card.addUIElement(stats2).rightOfTop(stats, 0);
		// end stats section
		
		// location
		TooltipMakerAPI loc = card.createUIElement(PANEL_WIDTH, LOCATION_SECTION_HEIGHT, false);
		
		String img = location.ind != null ? location.ind.getCurrentImage() : "graphics/illustrations/free_orbit.jpg";
		line = loc.beginImageWithText(img, 32);
		addActionText(line);
		loc.addImageWithText(3);
		card.addUIElement(loc).inBL(0, 2);
		
		// button holder
		TooltipMakerAPI buttonHolder = card.createUIElement(BUTTON_SECTION_WIDTH, PANEL_HEIGHT, false);
		buttonHolder.addButton(StringHelper.getString("action"), this, BUTTON_SECTION_WIDTH - 6, 16, 0);
		card.addUIElement(buttonHolder).inTR(1, 2);
		
		cardHolder.addCustom(card, 0);
		return cardHolder;
	}
	
	public TooltipCreator createTooltip(final String id) {
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 280;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				String str = GroundBattleIntel.getString("unitCard_tooltip_" + id);
				tooltip.addPara(str, 0);
				if (id.equals("morale")) {
					Color hl = Misc.getHighlightColor();
					//tooltip.setBulletedListMode("    - ");
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 2);
					tooltip.addPara(str, 3, hl, 
							StringHelper.toPercent(1 - GBConstants.MORALE_ATTACK_MOD),
							StringHelper.toPercent(1 + GBConstants.MORALE_ATTACK_MOD)
					);
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 3);
					tooltip.addPara(str, 0, hl, StringHelper.toPercent(GBConstants.REORGANIZE_AT_MORALE));
					str = " - " + GroundBattleIntel.getString("unitCard_tooltip_" + id + 4);
					tooltip.addPara(str, 0);
				}
			}
		};
	}
	
	
	public static enum ForceType {
		MARINE(Commodities.MARINES, "troopNameMarine", 1, 1), 
		HEAVY(Commodities.HAND_WEAPONS, "troopNameMech", 6, 1),
		MILITIA(Commodities.CREW, "troopNameMilitia", 0.4f, 0.5f), 
		REBEL(Commodities.CREW, "troopNameRebel", 0.4f, 0.7f);
		
		public final String commodityId;
		public final String nameStringId;
		public final float strength;
		public final float moraleMult;
		
		private ForceType(String commodityId, String nameStringId, float strength, float moraleMult) 
		{
			this.commodityId = commodityId;
			this.nameStringId = nameStringId;
			this.strength = strength;
			this.moraleMult = moraleMult;
		}
		
		public String getName() {
			return getString(nameStringId);
		}
	}
	
	public static enum UnitSize {
		PLATOON(40, 60, 1f),
		COMPANY(120, 200, 0.75f),
		BATALLION(500, 800, 0.5f),
		REGIMENT(2000, 3500, 0.25f);
		
		public int avgSize;
		public int maxSize;
		public float damMult;
		
		private UnitSize(int avgSize, int maxSize, float damMult) {
			this.avgSize = avgSize;
			this.maxSize = maxSize;
			this.damMult = damMult;
		}
		
		public int getAverageSizeForType(ForceType type) {
			int count = avgSize;
			if (type == ForceType.HEAVY) count = Math.round(count/GroundUnit.HEAVY_COUNT_DIVISOR);
			return count;
		}
		
		public String getName() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()));
		}
		
		public String getNamePlural() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()) + "Plural");
		}
	}
}
