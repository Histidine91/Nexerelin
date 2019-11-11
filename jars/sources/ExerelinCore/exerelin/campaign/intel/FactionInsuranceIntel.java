package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.FormatFlagsConversionMismatchException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FactionInsuranceIntel extends BaseIntelPlugin {
	public static Logger log = Global.getLogger(FactionInsuranceIntel.class);
	
	public static final boolean DEBUG_MODE = false;	// self-insurance and stuff
	
	public static final float HARD_MODE_MULT = 0.625f;
	public static final float DMOD_BASE_COST = Global.getSettings().getFloat("baseRestoreCostMult");
	public static final float DMOD_COST_PER_MOD = Global.getSettings().getFloat("baseRestoreCostMultPerDMod");
	public static final float COMPENSATION_PER_DMOD = 0.2f;
	public static final float LIFE_INSURANCE_PER_LEVEL = 2000f;
	
	protected Map<FleetMemberAPI, Integer[]> disabledOrDestroyedMembers;
	protected List<InsuranceItem> items = new ArrayList<>();
	protected boolean paid = true;
	protected float paidAmount = 0f;
	protected FactionAPI faction;

	public FactionInsuranceIntel(Map<FleetMemberAPI, Integer[]> disabledOrDestroyedMembers, 
			List<OfficerDataAPI> deadOfficers) {
		if (!intelValidations())
			return;
		
		this.disabledOrDestroyedMembers = disabledOrDestroyedMembers;
		paidAmount = calculatePayout(deadOfficers, disabledOrDestroyedMembers);

		if (faction.isAtBest("player", RepLevel.SUSPICIOUS))
		{
			paid = false;
		}

		log.debug("Amount: " + paidAmount);
		if (paidAmount > 0) {
			if (paid)
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(paidAmount);
			
			Global.getSector().getIntelManager().addIntel(this);
			Global.getSector().addScript(this);
			this.endAfterDelay();
		}
	}

	/**
	 * Check if we should actually pay insurance.
	 * @return
	 */
	protected boolean intelValidations() {
		//log.info("Validating insurance intel item");
		if (Global.getSector().getMemoryWithoutUpdate().contains("$tutStage"))	{
			return false;
		}

		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		if (!DEBUG_MODE && alignedFactionId.equals(Factions.PLAYER)) {	// no self insurance
			//log.info("Cannot self-insure");
			return false;
		}	
		if (ExerelinUtilsFaction.isExiInCorvus(alignedFactionId)) {
			// assume Exigency is alive on the other side of the wormhole, do nothing
		} else if (!DEBUG_MODE && !SectorManager.isFactionAlive(alignedFactionId))	{
			//log.info("Faction is not alive");
			return false;
		}

		faction = Global.getSector().getFaction(alignedFactionId);
		return true;
	}

	protected float calculatePayout(List<OfficerDataAPI> deadOfficers, Map<FleetMemberAPI, Integer[]> disabledOrDestroyedMembers) {
		float totalPayment = 0f;
		float mult = ExerelinConfig.playerInsuranceMult;
		if (SectorManager.getHardMode())
			mult *= HARD_MODE_MULT;

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		List<FleetMemberAPI> fleetCurrent = playerFleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : playerFleet.getFleetData().getSnapshot()) 
		{
			float amount = 0;
			
			// dead, not recovered
			if (!fleetCurrent.contains(member)) {
				amount = member.getBaseValue();
				if (disabledOrDestroyedMembers.containsKey(member)) {
					Integer[] entry = disabledOrDestroyedMembers.get(member);
					amount = entry[0];
				}
				amount *= mult;
				
				String text = getString("entryDescLost");
				InsuranceItem item = new InsuranceItem(member, amount, text);
				items.add(item);
				totalPayment += amount;
			}
			// dead, recovered
			else if (disabledOrDestroyedMembers.containsKey(member)) {
				Integer[] entry = disabledOrDestroyedMembers.get(member);
				float prevValue = entry[0];
				int dmodsOld = (int)entry[1];
				int dmods = countDMods(member);
				boolean depristined = dmodsOld == 0 && dmods > 0;
				boolean dmodsIncreased = !depristined && dmods > dmodsOld;
				
				String text = String.format(getString("entryDescRecovered"), dmodsOld, dmods);
				List<String> highlights = new ArrayList<>();
				highlights.add(dmodsOld + "");
				highlights.add(dmods + "");
				
				if (depristined) {
					amount = prevValue;
					text += "\n" + getString("entryDescNewDHull");
				}
				else if (dmodsIncreased) {
					int delta = dmods - dmodsOld;
					float dmodMult = Math.min(delta * COMPENSATION_PER_DMOD, 1);
					amount = prevValue * dmodMult;
					String multStr = Math.round(dmodMult * 100) + "%";
					try {
						text += "\n" + String.format(getString("entryDescMoreDMods"), multStr);
						highlights.add(multStr);
					} catch (FormatFlagsConversionMismatchException ex) {
						//log.error("wtf " + ex.getFlags() + ", " + ex.getConversion());
					}
				}
				
				amount *= mult;
				totalPayment += amount;
				
				InsuranceItem item = new InsuranceItem(member, amount, text);
				item.highlights.addAll(highlights);
				items.add(item);
			}
		}
		if (deadOfficers != null) {
			for (OfficerDataAPI deadOfficer : deadOfficers) {
				float amount = deadOfficer.getPerson().getStats().getLevel() * LIFE_INSURANCE_PER_LEVEL;
				log.info("Insuring dead officer " + deadOfficer.getPerson().getName().getFullName() + " for " + amount);
				totalPayment += amount;
			}
		}

		return totalPayment;
	}

	public static int countDMods(FleetMemberAPI member) {
		int dmods = 0;
		for (String mod : member.getVariant().getPermaMods()) {
			if (Global.getSettings().getHullModSpec(mod).hasTag("dmod"))
				dmods++;
		}
		log.info("Fleet member " + member.getShipName() + " has " + dmods + " D-mods");
		return dmods;
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = Misc.getBasePlayerColor();
		info.addPara(getName(), c, 0f);
		bullet(info);

		float pad = 3f;
		Color tc = getBulletColorForMode(mode);
		Color h = Misc.getHighlightColor();
		if (paid)
			info.addPara(getString("bulletPayment"), pad, tc, h, Misc.getDGSCredits(paidAmount));
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	protected String getName() {
		String str = paid ? "title" : "titleUnpaid";
		return getString(str);
	}
	
	protected boolean showDetails() {
		return paid && items != null;
	}
	
	@Override
	public boolean hasSmallDescription() {
		return !showDetails();
	}

	@Override
	public boolean hasLargeDescription() { 
		return showDetails();
	}
	
	public void createBaseDescription(TooltipMakerAPI info, float width, float pad) {
		info.addImage(faction.getCrest(), width, 128f, pad);

		String str = paid ? "desc" : "descUnpaid";

		Map<String, String> map = new HashMap<>();
		String payment = Misc.getDGSCredits(paidAmount);
		map.put("$paid", payment);
		map.put("$theEmployer", faction.getDisplayNameLongWithArticle());
		String para = StringHelper.getStringAndSubstituteTokens("nex_insurance", str, map);

		Color h = Misc.getHighlightColor();
		LabelAPI label = info.addPara(para, pad);
		label.setHighlight(payment, faction.getDisplayNameLongWithArticle());
		label.setHighlightColors(h, faction.getBaseUIColor());
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", pad);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		createBaseDescription(info, width, opad);
	}
	
	public static int ENTRY_HEIGHT = 80;
	public static int IMAGE_WIDTH = 80;
	public static int IMAGE_DESC_GAP = 12;
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		Color h = Misc.getHighlightColor();
		
		TooltipMakerAPI info = panel.createUIElement(width, height, true);
		createBaseDescription(info, width, opad);
		
		info.addSectionHeading(getString("headerBreakdown"), faction.getBaseUIColor(), 
			faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		float mult = ExerelinConfig.playerInsuranceMult;
		if (SectorManager.getHardMode())
			mult *= HARD_MODE_MULT;
		
		info.addPara(getString("descInsuranceMult"), opad, h, mult + "");
		
		// generate list of ships lost
		float heightPerItem = ENTRY_HEIGHT + opad;
		float itemPanelHeight = heightPerItem * items.size();
		CustomPanelAPI itemPanel = panel.createCustomPanel(width, itemPanelHeight, null);
		float yPos = opad;
		
		for (InsuranceItem item : items) {
			TooltipMakerAPI image = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, true);
			List<FleetMemberAPI> ship = new ArrayList<>();
			ship.add(item.member);
			image.addShipList(1, 1, IMAGE_WIDTH, Color.WHITE, ship, 0);
			
			TooltipMakerAPI entry = itemPanel.createUIElement(width - IMAGE_WIDTH - IMAGE_DESC_GAP,
					ENTRY_HEIGHT, true);
			entry.addPara(item.member.getShipName(), h, 0);
			String payout = Misc.getDGSCredits(item.payment);
			entry.addPara(getString("entryDescAmount"), pad, h, payout);

			LabelAPI desc = entry.addPara(item.desc, pad);
			desc.setHighlight(item.highlights.toArray(new String[0]));
			desc.setHighlightColor(h);
			
			itemPanel.addUIElement(image).inTL(4, yPos);
			itemPanel.addUIElement(entry).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP, yPos);
			
			yPos += ENTRY_HEIGHT + opad;
			//break;
		}
		//info.addPara(getString("descBaseValue"), opad);
		info.addCustom(itemPanel, 0);
		panel.addUIElement(info).inTL(0, 0);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_FLEET_LOG);
		tags.add(Tags.INTEL_COMMISSION);
		return tags;
	}

	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "credits");
	}

	@Override
	protected void notifyEnded() {
		Global.getSector().getIntelManager().removeIntel(this);
		Global.getSector().removeScript(this);
	}
	
	protected static String getString(String id) {
		return StringHelper.getString("nex_insurance", id);
	}
	
	public static class InsuranceItem {
		
		public FleetMemberAPI member;
		public float payment;
		public String desc;
		public List<String> highlights = new ArrayList<>();
		
		public InsuranceItem(FleetMemberAPI member, float payment, String desc) {
			this.member = member;
			this.payment = payment;
			this.desc = desc;
		}
	}
}
