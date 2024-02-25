package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.SurplusShipHull;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.plog.PlaythroughLog;
import com.fs.starfarer.api.impl.campaign.plog.SModRecord;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsBaseOfficial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.missions.BuyShipRule.*;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class BuyShip extends HubMissionWithBarEvent {
	
	public static final List<Class> AVAILABLE_RULES = new ArrayList<>();
	public static final float PRICE_IMPORTANCE_LOW = 50000;
	public static final float PRICE_IMPORTANCE_HIGH = 150000;
	public static final int SET_MIN_SIZE = 2;
	public static final int MIN_RULES = 1;
	
	static {
		AVAILABLE_RULES.add(DesignTypeRule.class);
		AVAILABLE_RULES.add(HullSizeRule.class);
		AVAILABLE_RULES.add(DPRule.class);
		AVAILABLE_RULES.add(DModRule.class);
		AVAILABLE_RULES.add(ShipTypeRule.class);
		AVAILABLE_RULES.add(SModRule.class);
	}

	public static float BASE_PRICE_MULT = 1.6f;

	public enum Variation {
		MILITARY, COLLECTOR
	}
	
	/*
		Proposed implementation:
		There are N criteria for ships: design type, size, DP threshold?, and civilian?		
	
		On mission generation:
			For each ship in player fleet:
				Add its design type and size to picker
			For each criterion:
				Select rule from picker, add all ships meeting this rule to a list of ships
			Start at first criterion, get all eligible ships
			For next criterion, get all available ships and get the intersection of the two lists
			If no ships are shared by the two lists, continue with the smaller one, else continue with intersection
			Repeat for next criterion
			After going through all criteria, display rules and allow ships meeting the requirements to be selected

		Variations: military, collector
	*/

	protected FleetMemberAPI member;
	protected Variation variation;
	
	protected List<BuyShipRule> rules = new ArrayList<>();
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {

		if (barEvent) {
			//setGiverRank(Ranks.CITIZEN);
			setGiverPost(pickOne(Ranks.POST_SMUGGLER, Ranks.POST_FENCE, Ranks.POST_ARISTOCRAT, Ranks.POST_ARMS_DEALER,
					Ranks.POST_BASE_COMMANDER, Ranks.POST_PATROL_COMMANDER, Ranks.POST_SUPPLY_MANAGER, Ranks.POST_ADMINISTRATOR,
					Ranks.POST_ENTREPRENEUR));

			/*
			float avgPrice = getAveragePrice(currEligible);
			if (avgPrice < PRICE_IMPORTANCE_LOW) {
				setGiverImportance(pickLowImportance());
			} else if (avgPrice > PRICE_IMPORTANCE_HIGH) {
				setGiverImportance(pickHighImportance());
			} else {
				setGiverImportance(pickMediumImportance());
			}
			 */
			setGiverImportance(pickImportance());
			findOrCreateGiver(createdAt, false, false);
			PersonAPI barPerson = getPerson();
			if (barPerson != null)
			{
				if (Nex_IsBaseOfficial.isOfficial(barPerson.getPostId(), "military")) {
					barPerson.addTag(Tags.CONTACT_MILITARY);
				}
				else {
					barPerson.addTag(Tags.CONTACT_TRADE);
				}
			}
		}

		PersonAPI person = getPerson();
		if (person == null) return false;
		if (person.getFaction().isPlayerFaction()) return false;
		MarketAPI market = person.getMarket();
		if (market == null) return false;

		if (!setPersonMissionRef(person, "$nex_bShip_ref")) {
			return false;
		}

		if (Global.getSector().getPlayerFleet().getNumShips() <= 1) return false;

		// don't offer this mission if the hub also has a SurplusShipHull mission
		if (getHub() instanceof BaseMissionHub) {
			BaseMissionHub bmh = (BaseMissionHub)getHub();
			for (HubMission otherMission : bmh.getOfferedMissions()) {
				if (otherMission.isMissionCreationAborted()) continue;
				if (otherMission instanceof SurplusShipHull) {
					return false;
				}
			}
		}


		setPostingLocation(market.getPrimaryEntity());

		loadRules();
		if (rules.isEmpty()) {
			log.info("No rules found in initial pass");
			return false;
		}

		List<FleetMemberAPI> currEligible = getEligibleShips(true);
		if (currEligible.isEmpty()) {
			log.info("No eligible ships found with current rules");
			return false;
		}
		if (rules.isEmpty()) {
			log.info("No rules found after testing on fleet");
			return false;
		}

		pickVariation();
		if (barEvent) {
			setGiverIsPotentialContactOnSuccess();
		}

		// will be set after picking ship
		//setRepFactionChangesVeryLow();
		//setRepPersonChangesLow();
		
		return true;
	}

	protected void loadRules() {
		rules.clear();
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		for (Class ruleClass : AVAILABLE_RULES) {
			BuyShipRule rule = BuyShipRule.instantiate(ruleClass, this);
			if (rule == null) continue;
			if (!rule.wantToUseRule(fleet)) continue;

			rule.init(fleet);
			if (!rule.canUseRule(fleet)) continue;

			rules.add(rule);
		}
		Collections.shuffle(rules, genRandom);
		Collections.sort(rules, new Comparator<BuyShipRule>() {
			@Override
			public int compare(BuyShipRule o1, BuyShipRule o2) {
				if (o1.isMandatoryRule() && !o2.isMandatoryRule()) return -1;
				if (!o1.isMandatoryRule() && o2.isMandatoryRule()) return 1;
				return 0;
			}
		});
	}

	protected float getPrice() {
		if (member == null) return 0;
		return member.getBaseBuyValue() * BASE_PRICE_MULT;
	}

	protected void afterSale(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if (member.getCaptain().isAICore() && !Misc.isUnremovable(member.getCaptain())) {
			String aicId = member.getCaptain().getAICoreId();
			Global.getSector().getPlayerFleet().getCargo().addCommodity(aicId, 1);
			AddRemoveCommodity.addCommodityGainText(aicId, 1, dialog.getTextPanel());
		}

		int sMods = this.getSModsInstalledByPlayer(member);
		if (sMods > 0) {
			Global.getSector().getPlayerStats().addStoryPoints(sMods, dialog.getTextPanel(), false);
		}
	}
	
	protected int getSModsInstalledByPlayer(FleetMemberAPI member) {
		int count = 0;
		for (SModRecord record : PlaythroughLog.getInstance().getSModsInstalled()) {
			FleetMemberAPI thisMember = record.getMember();
			if (member != thisMember) continue;
			count += record.getSPSpent();
		}
		return count;
	}

	protected Variation pickVariation() {
		WeightedRandomPicker<Variation> picker = new WeightedRandomPicker<>(genRandom);
		PersonAPI person = getPerson();
		if (person.hasTag(Tags.CONTACT_TRADE)) {
			picker.add(Variation.COLLECTOR, 1);
		}
		if (person.hasTag(Tags.CONTACT_MILITARY)) {
			picker.add(Variation.MILITARY, 1);
		}
		if (person.hasTag(Tags.CONTACT_UNDERWORLD)) {
			picker.add(Variation.COLLECTOR, .5f);
			picker.add(Variation.MILITARY, .5f);
		}
		variation = picker.pick(genRandom);
		if (variation == null) variation = Variation.COLLECTOR;
		return variation;
	}

	protected void showRules(InteractionDialogAPI dialog) {
		TooltipMakerAPI tt = dialog.getTextPanel().beginTooltip();
		tt.addPara(getString("ruleHeader"), 10);
		tt.setBulletedListMode(" - ");
		for (BuyShipRule rule : rules) {
			rule.printRule(tt, 0);
		}
		tt.setBulletedListMode(null);
		dialog.getTextPanel().addTooltip();
	}

	/**
	 * Gets the valid ships for sale in the current player fleet.
	 * @param rulePickerMode If true, remove rules that do not give us a sufficient restriction on saleable ships.
	 * @return
	 */
	protected List<FleetMemberAPI> getEligibleShips(boolean rulePickerMode) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		List<FleetMemberAPI> bestList = new ArrayList<>();

		// initial population from player fleet
		for (FleetMemberAPI member : player.getFleetData().getMembersListWithFightersCopy()) {
			if (BuyShipRule.isShipAllowedStatic(member, this)) {
				bestList.add(member);
			}
		}

		// not trying to pick rules? Just run the ships through all the rules and see who's left
		if (!rulePickerMode) {
			for (BuyShipRule currRule : new ArrayList<>(rules)) {
				List<FleetMemberAPI> fromThisRule = currRule.getShipsMeetingRule(player);
				bestList.retainAll(fromThisRule);
			}
			return bestList;
		}

		int tries = 0;
		do {
			if (tries > 0) loadRules();
			boolean prevRulesIncludeMandatory = false;

			for (BuyShipRule currRule : new ArrayList<>(rules)) {
				List<FleetMemberAPI> fromThisRule = currRule.getShipsMeetingRule(player);
				boolean currIsMandatory = currRule.isMandatoryRule();

				// nothing eligible from this rule, it has failed
				if (fromThisRule.isEmpty()) {
					if (currIsMandatory) {
						// failed a mandatory rule, halt completely
						return new ArrayList<>();
					}
					rules.remove(currRule);
					continue;
				}

				List<FleetMemberAPI> intersect = new ArrayList<>(bestList);
				intersect.retainAll(fromThisRule);
				boolean newIsSmaller = fromThisRule.size() < bestList.size();
				boolean newIsBigEnough = fromThisRule.size() >= SET_MIN_SIZE || currRule.isMandatoryRule();

				// if have a (sufficiently large) intersection, use that
				if (!intersect.isEmpty() && (intersect.size() >= SET_MIN_SIZE || currRule.isMandatoryRule())) {
					bestList = intersect;
				}
				// else use the new list if it's smaller (unless previous list includes a mandatory rule)
				else if (newIsSmaller && newIsBigEnough && !prevRulesIncludeMandatory) {
					bestList = fromThisRule;
					// previous rules have failed, remove them
					if (rulePickerMode && rules.contains(currRule)) {
						rules = rules.subList(rules.indexOf(currRule), rules.size());
					}
				}
				// old list is bigger, use that (unless our current rule is mandatory)
				else if (!currRule.isMandatoryRule()) {
					// new rule has failed, remove it
					if (rulePickerMode) {
						rules.remove(currRule);
					}
				}
				// at this point I have no idea what's going on, restart the whole loop
				else {
					break;
				}

				if (currIsMandatory) {
					prevRulesIncludeMandatory = true;
				}
			}
			tries++;
		} while (rules.size() < MIN_RULES && tries < 15);

		return bestList;
	}

	protected float getAveragePrice(Collection<FleetMemberAPI> members) {
		float sum = 0;
		for (FleetMemberAPI member : members) {
			sum += member.getBaseBuyValue();
		}
		return sum / members.size();
	}

	protected void pickShip(FleetMemberAPI member) {
		this.member = member;

		float price = getPrice();
		if (price > PRICE_IMPORTANCE_HIGH) {
			//log.info("Setting medium rep changes");
			setRepFactionChangesLow();
			setRepPersonChangesMedium();
		} else if (price > PRICE_IMPORTANCE_LOW) {
			//log.info("Setting low rep changes");
			setRepFactionChangesVeryLow();
			setRepPersonChangesLow();
		} else {
			//log.info("Setting tiny rep changes");
			setRepFactionChangesTiny();
			setRepPersonChangesVeryLow();
		}
	}

	protected void showPicker(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) {
		List<FleetMemberAPI> members = getEligibleShips(false);
		int cols = Math.min(members.size(), 7);
		if (cols < 4) cols = 4;
		int rows = (int)Math.ceil(members.size()/(float)cols);
		if (rows == 0) rows = 1;

		dialog.showFleetMemberPickerDialog(StringHelper.getString("exerelin_misc", "selectShipGeneric"),
				StringHelper.getString("confirm", true),
				StringHelper.getString("cancel", true),
				rows, cols, 96,
				true, false, members,
				new FleetMemberPickerListener() {
					public void pickedFleetMembers(List<FleetMemberAPI> members) {
						if (members.isEmpty()) return;
						pickShip(members.get(0));
						FireAll.fire(null, dialog, memoryMap, "Nex_BShip_ShipPicked");
					}
					public void cancelledFleetMemberPicking() {

					}
				});
	}

	@Override
	protected void updateInteractionDataImpl() {
		// this is weird - in the accept() method, the mission is aborted, which unsets
		// $sShip_ref. So: we use $sShip_ref2 in the ContactPostAccept rule
		// and $sShip_ref2 has an expiration of 0, so it'll get unset on its own later.
		set("$nex_bShip_ref2", this);

		set("$nex_bShip_barEvent", isBarEvent());
		set("$nex_bShip_manOrWoman", getPerson().getManOrWoman());
		set("$nex_bShip_hisOrHer", getPerson().getHisOrHer());

		set("$nex_bShip_voice", getPerson().getVoice());
		//set("$nex_bShip_isMilitary", getPerson().hasTag(Tags.CONTACT_MILITARY));
		//set("$nex_bShip_isTrade", getPerson().hasTag(Tags.CONTACT_TRADE));
		//set("$nex_bShip_isUnderworld", getPerson().hasTag(Tags.CONTACT_UNDERWORLD));
		set("$nex_bShip_isBaseOfficial", Nex_IsBaseOfficial.isOfficial(getPerson().getPostId(), "any"));
		set("$nex_bShip_variation", variation);

		if (member != null) {
			set("$nex_bShip_member", member);
			set("$nex_bShip_hull", member.getHullSpec().getHullNameWithDashClass());
			set("$nex_bShip_designation", member.getHullSpec().getDesignation());
			set("$nex_bShip_price", Misc.getWithDGS(getPrice()));    // interesting discovery: AddCommodity credits takes a string'd number
		}
	}
	
	@Override
	protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Token> params,
							     Map<String, MemoryAPI> memoryMap) {

		switch (action) {
			case "showShip":
				dialog.getVisualPanel().showFleetMemberInfo(member, true);
				return true;
			case "showPerson":
				dialog.getVisualPanel().showPersonInfo(getPerson(), true);
				return true;
			case "showRules":
				showRules(dialog);
				return true;
			case "showPicker":
				showPicker(dialog, memoryMap);
				return true;
			// not needed, do in dialog
			case "confirm":
				afterSale(dialog, memoryMap);
				return true;
		}
		return super.callAction(action, ruleId, dialog, params, memoryMap);
	}

	@Override
	public String getBaseName() {
		return "Buy Ship"; // not used since there's no intel item
	}
	
	@Override
	public void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		// it's just an transaction immediate transaction handled in rules.csv
		// no intel item etc
		
		currentStage = new Object(); // so that the abort() assumes the mission was successful
		abort();
	}
	
	public static String getString(String id) {
		return StringHelper.getString("nex_buyShipMission", id);
	}
}




