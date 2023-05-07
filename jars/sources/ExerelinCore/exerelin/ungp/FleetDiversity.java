package exerelin.ungp;

import com.fs.starfarer.api.campaign.BuffManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exerelin.utilities.NexUtils;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.api.rules.tags.UNGP_PlayerFleetTag;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings;
import ungp.scripts.utils.UNGP_BaseBuff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// mostly copied from UNGP_NavalTreaty
public class FleetDiversity extends UNGP_BaseRuleEffect implements UNGP_PlayerFleetTag {
	public static final float CR_REDUCTION_PER_DUPLICATE = 0.15f;

	protected class CRDebuff extends UNGP_BaseBuff {

		protected int count;

		public CRDebuff(String id, float dur, int count) {
			super(id, dur);
			this.count = count;
		}

		@Override
		public void apply(FleetMemberAPI member) {
			decreaseMaxCR(member.getStats(), id, (count - 1) * CR_REDUCTION_PER_DUPLICATE, rule.getName());
//			member.getStats().getMaxCombatReadiness().modifyFlat(id, -extraType * MAX_CR_REDUCTION_PER_TYPE, rule.getName());
		}

		public void setCount(int count) {
			this.count = count;
		}
	}

	@Override
	public float getValueByDifficulty(int index, int difficulty) {
		return 0;
	}
	
	
	@Override
	public void applyPlayerFleetStats(CampaignFleetAPI fleet) {
		Map<String, Integer> counts = new HashMap<>(); 
		
		final List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : members) {
			if (member.isMothballed()) continue;
			NexUtils.modifyMapEntry(counts, member.getHullSpec().getBaseHullId(), 1);
		}
		
		boolean needsSync = false;
		for (FleetMemberAPI member : members) {
			String buffId = rule.getBuffID();
			
			Integer count = counts.get(member.getHullSpec().getBaseHullId());
			if (count == null) count = 0;
			if (count <= 1) {
				member.getBuffManager().removeBuff(buffId);
				continue;
			}
			
			float buffDur = 0.1f;
			BuffManagerAPI.Buff test = member.getBuffManager().getBuff(buffId);
			if (test instanceof CRDebuff) {
				CRDebuff buff = (CRDebuff) test;
				buff.setDur(buffDur);
				buff.setCount(count);
			} else {
				member.getBuffManager().addBuff(new CRDebuff(buffId, buffDur, count));
				needsSync = true;
			}
		}
		if (needsSync) {
			fleet.forceSync();
		}
	}

	@Override
	public void unapplyPlayerFleetStats(CampaignFleetAPI fleet) {
	}

	@Override
	public String getDescriptionParams(int index) {
		if (index == 0) return (int) (CR_REDUCTION_PER_DUPLICATE * 100f) + "%";
		return index + "";
	}
	
	@Deprecated @Override
	public String getDescriptionParams(int index, int legacyLevel) {
		return getDescriptionParams(index);
	}
	
	@Override
	public String getDescriptionParams(int index, UNGP_SpecialistSettings.Difficulty difficulty) {
		return getDescriptionParams(index);
	}
}