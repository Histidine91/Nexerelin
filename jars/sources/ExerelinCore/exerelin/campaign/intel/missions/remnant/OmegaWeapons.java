package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.StringHelper;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import java.awt.Color;

// Cargo coded from SurplusShipHull
public class OmegaWeapons extends HubMissionWithBarEvent {
	
	public static final float COST_MULT = 2;
	
	protected List<String> weapons = new ArrayList<>();
	protected int price;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		MarketAPI market = person.getMarket();
		if (market == null) return false;
				
		if (!setPersonMissionRef(person, "$nex_omegaWep_ref")) {
			return false;
		}
		
		weapons.clear();
		weapons.addAll(generateWeaponList());
		if (weapons.isEmpty()) return false;
		
		
		return true;
	}
	
	public List<String> generateWeaponList() {
		List<String> results = new ArrayList<>();		
		int count;
		WeaponSize maxSize;
		
		// At cooperative it can pick 1 large, 1 medium and 1 small, or 3 smalls
		// At friendly it can pick 1 medium or 2 smalls
		// At welcoming it can pick 1 small
		
		RepLevel rep = getPerson().getRelToPlayer().getLevel();
		//rep = RepLevel.COOPERATIVE;	// debug
		switch (rep) {
			case COOPERATIVE:
				count = 3;
				maxSize = WeaponSize.LARGE;
				break;
			case FRIENDLY:
				count = 2;
				maxSize = WeaponSize.MEDIUM;
				break;
			case WELCOMING:
				count = 1;
				maxSize = WeaponSize.SMALL;
				break;
			default:
				return results;
		}
		
		WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<>(genRandom);
		for (String weaponId : Global.getSector().getFaction(Factions.OMEGA).getKnownWeapons()) 
		{
			WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
			if (spec.getSize().ordinal() > maxSize.ordinal())
				continue;
			picker.add(spec);
		}
		
		int tries = 0;
		while (count > 0 && tries < 50) {
			if (picker.isEmpty()) break;
			tries++;
			WeaponSpecAPI spec = picker.pickAndRemove();
			int value = spec.getSize().ordinal() + 1;
			if (value > count) continue;
			count -= value;
			
			results.add(spec.getWeaponId());
		}
		
		return results;
	}
	
	protected void updateInteractionDataImpl() {
		// this is weird - in the accept() method, the mission is aborted, which unsets
		// $sShip_ref. So: we use $sShip_ref2 in the ContactPostAccept rule
		// and $sShip_ref2 has an expiration of 0, so it'll get unset on its own later.
		set("$nex_omegaWep_ref2", this);
		
		set("$nex_omegaWep_count", weapons.size());
		set("$nex_omegaWep_manOrWoman", getPerson().getManOrWoman());
		set("$nex_omegaWep_rank", getPerson().getRank().toLowerCase());
		set("$nex_omegaWep_rankAOrAn", getPerson().getRankArticle());
		set("$nex_omegaWep_hisOrHer", getPerson().getHisOrHer());
	}
	
	@Override
	protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Token> params,
							     Map<String, MemoryAPI> memoryMap) {
		if ("showWeapons".equals(action)) {
			selectWeapons(dialog, memoryMap);
			return true;
		} else if ("showPerson".equals(action)) {
			dialog.getVisualPanel().showPersonInfo(getPerson(), true);
			return true;
		}
		return false;
	}
	
	protected float computeValue(CargoAPI cargo) {
		float cost = 0;
		for (CargoStackAPI stack : cargo.getStacksCopy()) {
			WeaponSpecAPI wep = stack.getWeaponSpecIfWeapon();
			if (wep != null) {
				cost += wep.getBaseValue() * stack.getSize();
			}
		}
		cost *= COST_MULT;
		return cost;
	}
	
	protected void selectWeapons(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) 
	{
		CargoAPI copy = Global.getFactory().createCargo(false);
		for (String weapon : weapons) {
			copy.addWeapons(weapon, 1);
		}
		final CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
		final TextPanelAPI text = dialog.getTextPanel();
		
		final float width = 310f;
		dialog.showCargoPickerDialog(getString("omegaWepSelect"), 
				Misc.ucFirst(StringHelper.getString("confirm")), 
				Misc.ucFirst(StringHelper.getString("cancel")),
						true, width, copy, new CargoPickerListener() {
			public void pickedCargo(CargoAPI cargo) {
				cargo.sort();
				float cost = computeValue(cargo);
				
				if (cost > 0 && cost < playerCargo.getCredits().get()) {
					playerCargo.getCredits().subtract(cost);
					AddRemoveCommodity.addCreditsLossText((int)cost, text);
					for (CargoStackAPI stack : cargo.getStacksCopy()) {
						playerCargo.addItems(stack.getType(), stack.getData(), stack.getSize());
						AddRemoveCommodity.addStackGainText(stack, text, false);
					}
					memoryMap.get(MemKeys.LOCAL).set("$option", "contact_accept", 0);
					FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
				}
			}
			
			@Override
			public void cancelledCargoSelection() {
			}
			
			@Override
			public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
			
				float cost = computeValue(combined);
				float credits = playerCargo.getCredits().get();
				
				float pad = 3f;
				float small = 5f;
				float opad = 10f;
				Color h = Misc.getHighlightColor();
				
				FactionAPI faction = getPerson().getFaction();
				panel.setParaOrbitronLarge();
				panel.addPara(Misc.ucFirst(faction.getDisplayName()), faction.getBaseUIColor(), opad);
				panel.setParaFontDefault();
				
				panel.addImage(faction.getLogo(), width * 1f, pad);
				
				String str = getString("omegaWepCost");
				panel.addPara(str, opad, cost <= credits ? h : Misc.getNegativeHighlightColor(), Misc.getDGSCredits(cost));
				str = getString("omegaWepCredits");
				panel.addPara(str, pad, h, Misc.getDGSCredits(credits));
			}
		});
	}

	@Override
	public String getBaseName() {
		return "Omega Weapons"; // not used I don't think
	}
	
	@Override
	public void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		// it's just an transaction immediate transaction handled in rules.csv
		// no intel item etc
		
		currentStage = new Object(); // so that the abort() assumes the mission was successful
		abort();
	}	
}

