package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FactionInsuranceIntel extends BaseIntelPlugin {
	private static Logger log = Global.getLogger(FactionInsuranceIntel.class);

	protected static final float HARD_MODE_MULT = 0.5f;
	protected static final boolean COMPENSATE_DMODS = false;
	protected static final float DMOD_BASE_COST = Global.getSettings().getFloat("baseRestoreCostMult");
	protected static final float DMOD_COST_PER_MOD = Global.getSettings().getFloat("baseRestoreCostMultPerDMod");
	protected static final float LIFE_INSURANCE_PER_LEVEL = 2000f;
	protected static final float BASE_HULL_VALUE_MULT_FOR_DMODS = 0.4f;

	protected boolean paid = true;
	protected float paidAmount = 0f;
	protected FactionAPI faction;

	public FactionInsuranceIntel(Map<FleetMemberAPI, Float[]> disabledOrDestroyedMembers, List<OfficerDataAPI> deadOfficers) {
		if (intelValidations()) {
			paidAmount = calculateAmount(deadOfficers, disabledOrDestroyedMembers);

			if (faction.isAtBest("player", RepLevel.SUSPICIOUS))
			{
				paidAmount = 0;
				paid = false;
			}
			else paidAmount *= ExerelinConfig.playerInsuranceMult;

			if (SectorManager.getHardMode())
				paidAmount *= HARD_MODE_MULT;

			log.debug("Amount: " + paidAmount);
			if(paidAmount > 0) {
				Global.getSector().getPlayerFleet().getCargo().getCredits().add(paidAmount);
				Global.getSector().getIntelManager().addIntel(this);
				Global.getSector().addScript(this);
				this.endAfterDelay();
			}
		}
	}

	/**
	 * Check if we should actually pay insurance.
	 * @return
	 */
	protected boolean intelValidations() {
		//log.info("Validating insurance intel item");
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		BattleAPI battle = playerFleet.getBattle();

		if (!battle.isPlayerInvolved() || Global.getSector().getMemoryWithoutUpdate().contains("$tutStage"))
		{
			//log.info("Player is not involved in battle");
			return false;
		}
			

		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		if (alignedFactionId.equals(Factions.PLAYER)) {	// no self insurance
			//log.info("Cannot self-insure");
			return false;
		}	
		if (ExerelinUtilsFaction.isExiInCorvus(alignedFactionId)) {
			// assume Exigency is alive on the other side of the wormhole, do nothing
		} else if (!SectorManager.isFactionAlive(alignedFactionId))	{
			//log.info("Faction is not alive");
			return false;
		}

		faction = Global.getSector().getFaction(alignedFactionId);
		return true;
	}

	protected float calculateAmount(List<OfficerDataAPI> deadOfficers, Map<FleetMemberAPI, Float[]> disabledOrDestroyedMembers) {
		float value = 0f;

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		List<FleetMemberAPI> fleetCurrent = playerFleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : playerFleet.getFleetData().getSnapshot()) {
			// dead, not recovered
			if (!fleetCurrent.contains(member)) {
				float amount = member.getBaseBuyValue();
				if (disabledOrDestroyedMembers.containsKey(member)) {
					Float[] entry = disabledOrDestroyedMembers.get(member);
					amount = entry[0];
				}
				log.info("Insuring lost ship " + member.getShipName() + " for " + amount);
				value += amount;
			}
			// dead, recovered; compare "before" to "after" in D mod count and base value
			else if (disabledOrDestroyedMembers.containsKey(member)) {
				Float[] entry = disabledOrDestroyedMembers.get(member);
				float prevValue = entry[0];
				int dmodsOld = (int) (float) entry[1];
				int dmods = countDMods(member);

				float dmodCompensation = 0;
				if (dmods > dmodsOld && COMPENSATE_DMODS) {
					dmodCompensation = member.getHullSpec().getBaseValue() * BASE_HULL_VALUE_MULT_FOR_DMODS;
					if (dmodsOld == 0)
						dmodCompensation *= DMOD_BASE_COST;
					dmodCompensation *= Math.pow(DMOD_COST_PER_MOD, dmods - dmodsOld);
					log.info("Compensation for D-mods: " + dmodCompensation);
				}

				float amount = prevValue - member.getBaseBuyValue() + dmodCompensation;
				log.info("Insuring recovered ship " + member.getShipName() + " for " + amount);
				value += amount;
			}
		}
		for (OfficerDataAPI deadOfficer : deadOfficers) {
			float amount = deadOfficer.getPerson().getStats().getLevel() * LIFE_INSURANCE_PER_LEVEL;
			log.info("Insuring dead officer " + deadOfficer.getPerson().getName().getFullName() + " for " + amount);
			value += amount;
		}

		return value;
	}

	public static int countDMods(FleetMemberAPI member) {
		int dmods = 0;
		for (String mod : member.getVariant().getPermaMods()) {
			if (Global.getSettings().getHullModSpec(mod).hasTag("dmod"))
				dmods++;
		}
		log.debug("Fleet member " + member.getShipName() + " has " + dmods + " D-mods");
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
		info.addPara("%s received", pad, tc, h, Misc.getDGSCredits(paidAmount));
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	protected String getName() {
		String str;
		if (paid) str = "exerelinFactionInsurance";
		else str = "exerelinFactionInsuranceUnpaid";
		return StringHelper.getString("exerelin_factions", str);
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		info.addImage(faction.getCrest(), width, 128f, opad);

		String str;
		if (paid) str = "exerelinFactionInsuranceDesc";
		else str = "exerelinFactionInsuranceUnpaidDesc";

		Map<String, String> map = new HashMap<>();
		String paid = Misc.getDGSCredits(paidAmount);
		map.put("$paid", paid);
		map.put("$theEmployer", faction.getDisplayNameLongWithArticle());
		String para = StringHelper.getStringAndSubstituteTokens("exerelin_factions", str, map);

		Color h = Misc.getHighlightColor();
		LabelAPI label = info.addPara(para, opad);
		label.setHighlight(paid, faction.getDisplayNameLongWithArticle());
		label.setHighlightColors(h, faction.getBaseUIColor());
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
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
}
