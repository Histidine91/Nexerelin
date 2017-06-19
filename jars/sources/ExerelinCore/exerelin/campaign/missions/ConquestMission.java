package exerelin.campaign.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventManagerAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.missions.BaseCampaignMission;
import exerelin.utilities.StringHelper;
import java.util.UUID;

public class ConquestMission extends BaseCampaignMission {
	public static final float MIN_DURATION_REMAINING_TO_OFFER = 30;
	public static final float MIN_BONUS_DUR = 5f;
	
	protected MarketAPI target;
	protected FactionAPI issuer;
	protected float baseDuration = 30;
	protected float bonusDuration = 30;
	protected float baseReward = 0;
	protected float bonusReward = 0;
	
	public ConquestMission(MarketAPI target, FactionAPI issuer, float baseDuration, float bonusDuration, float baseReward, float bonusReward) 
	{	
		this.target = target;
		this.issuer = issuer;
		this.baseDuration = baseDuration;
		this.bonusDuration = bonusDuration;
		this.baseReward = baseReward;
		this.bonusReward = bonusReward;
		
		CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
		
		CampaignEventTarget eventTarget = new CampaignEventTarget(target);
		eventTarget.setExtra(UUID.randomUUID().toString() + issuer.getId());
		
		event = eventManager.primeEvent(eventTarget, "exerelin_conquest_mission", this);
	}
	
	public MarketAPI getTarget() {
		return target;
	}
	
	public FactionAPI getIssuer() {
		return issuer;
	}
	
	@Override
	public void advance(float amount) {
		if (!target.getFaction().isHostileTo(issuer))
		{
			Global.getSector().getMissionBoard().removeMission(this, true);
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		baseDuration -= days;
		bonusDuration -= days;
		if (baseDuration < MIN_DURATION_REMAINING_TO_OFFER) {
			Global.getSector().getMissionBoard().removeMission(this, true);
		}
		if (bonusDuration <= MIN_BONUS_DUR) {
			bonusDuration = 0f;
		}
	}
	
	@Override
	public void playerAccept(SectorEntityToken entity) {
		super.playerAccept(entity);
		CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
		eventManager.startEvent(event);
	}
	
	public float getBaseDuration() {
		return baseDuration;
	}
	
	public float getBonusDuration() {
		return bonusDuration;
	}
	
	public float getBaseReward() {
		return baseReward;
	}
	
	public float getBonusReward() {
		return bonusReward;
	}

	public boolean hasBonus(float eventElapsed) {
		return (bonusDuration - eventElapsed) > 0;
	}
	
	public MissionCompletionRep getRepChange(boolean hasBonus) {
		float rep = target.getSize() * 0.01f;
		if (hasBonus) rep *= 1.5f;
		
		return new MissionCompletionRep(rep, RepLevel.COOPERATIVE, -0.05f, RepLevel.INHOSPITABLE);
	}
	
	// TODO externalise
	@Override
	public String getName() {
		return StringHelper.getString("exerelin_missions", "conquestMission") + " - " + target.getName();
	}
	
	@Override
	public String getFactionId() {
		return issuer.getId();
	}
	
	@Override
	public CampaignEventPlugin getPrimedEvent() {
		return event;
	}
	
	@Override
	public String getPostingStage() {
		if (hasBonus(0f)) return "posting_bonus";
		return super.getPostingStage();
	}
}
