package exerelin.campaign.intel;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.Arrays;
import java.util.HashSet;
import org.lazywizard.lazylib.MathUtils;

// adapted from SystemBountyIntel
public class FactionBountyIntel extends BaseIntelPlugin implements EveryFrameScript, FleetEventListener {
	public static Logger log = Global.getLogger(FactionBountyIntel.class);
	
	public static final Set<String> ALWAYS_PAYOUT_FLEETS = new HashSet<>(Arrays.asList(new String[] {
		FleetTypes.TASK_FORCE, "exerelinInvasionFleet", "exerelinInvasionSupportFleet", "nex_satBombFleet"
	}));

	public static final float BOUNTY_MULT = 0.5f;	// of baseSystemBounty
	public static final float MAX_DURATION = 90;
	
	protected float elapsedDays = 0f;
	protected float duration = MAX_DURATION;
	
	protected float baseBounty = 0;
	
	protected FactionAPI faction = null;
	protected FactionAPI enemyFaction = null;
	protected FactionBountyResult latestResult;

	protected MilitaryResponseScript script;
	
	public FactionBountyIntel(FactionAPI faction) {
		this.faction = faction;
		
		baseBounty = Global.getSettings().getFloat("baseSystemBounty") * BOUNTY_MULT;
		baseBounty = (int) baseBounty;
		
		log.info(String.format("Starting bounty for faction [%s], %d credits per frigate", faction.getDisplayName(), (int) baseBounty));
		
		updateLikelyCauseFaction();
		
		Global.getSector().getIntelManager().queueIntel(this);
		Global.getSector().getListenerManager().addListener(this);
	}
	
	@Override
	public void reportMadeVisibleToPlayer() {
		if (!isEnding() && !isEnded()) {
			duration = Math.max(duration * 0.5f, Math.min(duration * 2f, MAX_DURATION));
		}
	}
	
	public float getElapsedDays() {
		return elapsedDays;
	}

	public void setElapsedDays(float elapsedDays) {
		this.elapsedDays = elapsedDays;
	}
	
	public void reset() {
		elapsedDays = 0f;
		endingTimeRemaining = null;
		ending = null;
		ended = null;
		script.setElapsed(0f);
	}
	
	protected void updateLikelyCauseFaction() {
		FactionAPI highest = null;
		int highestWeight = 0;
		for (String otherId : DiplomacyManager.getFactionsAtWarWithFaction(faction, true, true, false)) {
			FactionAPI other = Global.getSector().getFaction(otherId);
			if (NexUtilsFaction.isPirateFaction(otherId))
				continue;
			
			float weight = NexUtilsFaction.getFactionMarketSizeSum(otherId);
			if (faction.isAtBest(otherId, RepLevel.VENGEFUL))
				weight *= 4f;
			weight *= MathUtils.getRandomNumberInRange(0.8f, 1.2f);
			
			if (weight > highestWeight) {
				highestWeight = (int)weight;
				highest = other;
			}
		}
		
		if (highest != null) {
			enemyFaction = highest;
		} else {
			enemyFaction = null;
		}
		
	}
	
	@Override
	protected void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		
		elapsedDays += days;

		if (elapsedDays >= duration && !isDone()) {
			endAfterDelay();
			sendUpdateIfPlayerHasIntel(new Object(), false);
			return;
		}
		if (!SectorManager.isFactionAlive(faction.getId())) {
			endAfterDelay();
			sendUpdateIfPlayerHasIntel(new Object(), false);
			return;
		}
	}
	
	@Override
	public float getTimeRemainingFraction() {
		float f = 1f - elapsedDays / duration;
		return f;
	}

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		log.info(String.format("Ending bounty by faction [%s]", faction.getDisplayName()));
		
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	
	public static class FactionBountyResult {
		public int payment;
		public float fraction;
		public ReputationAdjustmentResult rep;
		public FactionBountyResult(int payment, float fraction, ReputationAdjustmentResult rep) {
			this.payment = payment;
			this.fraction = fraction;
			this.rep = rep;
		}
		
	}
	
	protected boolean shouldPayout(CampaignFleetAPI fleet) {
		FactionAPI other = fleet.getFaction();
		if (!faction.isHostileTo(other.getId())) return false;
		
		// no pirates, remnants etc. unless it's a raid fleet or something
		boolean badFaction = NexUtilsFaction.isPirateFaction(other.getId()) || !other.isShowInIntelTab();
		
		if (badFaction) {
			//if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER))
			//	return true;
			if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET))
				return true;
			String type = NexUtilsFleet.getFleetType(fleet);
			if (type != null && ALWAYS_PAYOUT_FLEETS.contains(type))
				return true;
			
			return false;
		}
		return true;
	}

	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (isEnded() || isEnding()) return;
		
		if (!battle.isPlayerInvolved()) return;
		
		int payment = 0;
		float fpDestroyed = 0;
		for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
			if (!shouldPayout(otherFleet))
				continue;
			
			float bounty = 0;
			for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
				float mult = Misc.getSizeNum(loss.getHullSpec().getHullSize());
				bounty += mult * baseBounty;
				fpDestroyed += loss.getFleetPointCost();
			}
			
			payment += (int) (bounty * battle.getPlayerInvolvementFraction());
		}
	
		if (payment > 0) {
			Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);
			
			float repFP = (int)(fpDestroyed * battle.getPlayerInvolvementFraction());
			ReputationAdjustmentResult rep = Global.getSector().adjustPlayerReputation(
							new RepActionEnvelope(RepActions.COMMISSION_BOUNTY_REWARD, repFP, null, null, true, false), 
							faction.getId());
			latestResult = new FactionBountyResult(payment, battle.getPlayerInvolvementFraction(), rep);
			sendUpdateIfPlayerHasIntel(latestResult, false);
		}
	}
	
	@Override
	public boolean runWhilePaused() {
		return false;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		Color h = Misc.getHighlightColor();
				
		if (isEnding() && isUpdate) {
			//info.addPara("Over", initPad);
		} else {
			if (isUpdate && latestResult != null) {
				info.addPara(getString("factionBountyIntel_bulletReceived"), 
						initPad, tc, h, Misc.getDGSCredits(latestResult.payment));
				if (Math.round(latestResult.fraction * 100f) < 100f) {
					info.addPara(getString("factionBountyIntel_bulletShare"), 0f, tc, h, 
							"" + (int) Math.round(latestResult.fraction * 100f) + "%");
				}
				CoreReputationPlugin.addAdjustmentMessage(latestResult.rep.delta, faction, null, 
														  null, null, info, tc, isUpdate, 0f);
			} else if (mode == ListInfoMode.IN_DESC) {
				info.addPara(getString("factionBountyIntel_bulletBaseReward"), 
						initPad, tc, h, Misc.getDGSCredits(baseBounty));
				addDays(info, getString("factionBountyIntel_bulletRemaining"), 
						duration - elapsedDays, tc);
			} else {
				if (!isEnding()) {
					info.addPara(getString("factionBountyIntel_bulletBaseReward"), 
							initPad, tc, h, Misc.getDGSCredits(baseBounty));
					addDays(info, getString("factionBountyIntel_bulletRemaining"), 
							duration - elapsedDays, tc);
				}
			}
		}
	}
	
	@Override
	public String getSortString() {
		return "Faction Bounty";
	}
	
	public String getName() {
		String name = NexUtilsFaction.getFactionShortName(faction);
		if (isEnding()) {
			return getString("factionBountyIntel_titleEnded") + " - " + name;
		}
		return getString("factionBountyIntel_title") + " - " + name;
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
		//return null;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		
		Color h = Misc.getHighlightColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;

		//info.addPara(getName(), c, 0f);
		
		//info.addSectionHeading(getName(), Alignment.MID, 0f);
		
		info.addImage(faction.getLogo(), width, 128, opad);
		
		info.addPara(getString("factionBountyIntel_desc1"), opad, faction.getBaseUIColor(), 
				Misc.ucFirst(faction.getPersonNamePrefix()));

		if (isEnding()) {
			info.addPara(getString("factionBountyIntel_descEnded"), opad);
			return;
		}
		
		addBulletPoints(info, ListInfoMode.IN_DESC);
		
		if (enemyFaction != null) {
			info.addPara(getString("factionBountyIntel_desc2"),
					opad, enemyFaction.getBaseUIColor(), enemyFaction.getPersonNamePrefix());
		}
		
		String str = getString("factionBountyIntel_desc3");
		str = StringHelper.substituteFactionTokens(str, faction);
		str = StringHelper.substituteToken(str, "$isOrAre", faction.getDisplayNameIsOrAre());
		info.addPara(str, opad);
//					 opad, faction.getBaseUIColor(),
//					 faction.getDisplayNameWithArticleWithoutArticle());
		
		
		String isOrAre = faction.getDisplayNameIsOrAre();
		FactionCommissionIntel temp = new FactionCommissionIntel(faction);
		List<FactionAPI> hostile = temp.getHostileFactions();
		if (hostile.isEmpty()) {
			str = getString("factionBountyIntel_desc4NoHostile");
			str = StringHelper.substituteFactionTokens(str, faction);
			str = StringHelper.substituteToken(str, "$isOrAre", isOrAre);
			info.addPara(str, 0f);
		} else {
			str = getString("factionBountyIntel_desc4");
			str = StringHelper.substituteFactionTokens(str, faction);
			str = StringHelper.substituteToken(str, "$isOrAre", isOrAre);
			info.addPara(str, opad);
			
			info.setParaFontDefault();
			
			info.setBulletedListMode(BaseIntelPlugin.INDENT);
			float initPad = pad;
			for (FactionAPI other : hostile) {
				if (NexUtilsFaction.isPirateFaction(other.getId()))
					continue;
				info.addPara(Misc.ucFirst(other.getDisplayName()), other.getBaseUIColor(), initPad);
				initPad = 0f;
			}
			info.setBulletedListMode(null);
		}
		
		if (latestResult != null) {
			//Color color = faction.getBaseUIColor();
			//Color dark = faction.getDarkUIColor();
			//info.addSectionHeading("Most Recent Reward", color, dark, Alignment.MID, opad);
			info.addPara(getString("factionBountyIntel_descResult1"), opad);
			bullet(info);
			info.addPara(getString("factionBountyIntel_descResult2"), pad, tc, h, 
					Misc.getDGSCredits(latestResult.payment));
			if (Math.round(latestResult.fraction * 100f) < 100f) {
				info.addPara(getString("factionBountyIntel_descResult3"), 0f, tc, h, 
						"" + (int) Math.round(latestResult.fraction * 100f) + "%");
			}
			CoreReputationPlugin.addAdjustmentMessage(latestResult.rep.delta, faction, null, 
													  null, null, info, tc, false, 0f);
			unindent(info);
		}
	}
	
	@Override
	public String getIcon() {
		return faction.getCrest();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_BOUNTY);
		tags.add(faction.getId());
		return tags;
	}

	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_bounties", id, ucFirst);
	}
}


