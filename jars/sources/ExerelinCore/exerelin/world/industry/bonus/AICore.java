package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.world.ExerelinProcGen;

public abstract class AICore extends BonusGen {
	
	public AICore(String... industryIds) {
		super(industryIds);
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getAICoreId() != null && ind.getAICoreId().equals(Commodities.ALPHA_CORE))
			return false;
		return true;
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		String currentAI = ind.getAICoreId();
		if (currentAI == null || currentAI.equals(Commodities.GAMMA_CORE))
			ind.setAICoreId(Commodities.BETA_CORE);
		else
			ind.setAICoreId(Commodities.ALPHA_CORE);
		super.apply(ind, entity);
	}
}
