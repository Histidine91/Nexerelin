package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import exerelin.campaign.ui.VictoryScreenScript;
import exerelin.utilities.StringHelper;

public class AcademyStoryVictoryScript implements EveryFrameScript {
	
	protected boolean isDone;
	protected float interval;
	
	public void init() {
		if (!SectorManager.getManager().isCorvusMode()) {
			Global.getLogger(this.getClass()).info("Random sector, disabling script");
			isDone = true;
			return;
		}
		Global.getSector().addScript(this);
	}
	
	@Override
	public boolean isDone() {
		return isDone;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		if (Global.getSector().isInNewGameAdvance())
			return;
		
		interval += amount;
		if (interval >= 0.25f) {
			interval = 0;
			if (SectorManager.getManager().hasVictoryOccured()) {
				isDone = true;
				return;
			}
			
			if (Global.getSector().getMemoryWithoutUpdate().getBoolean("$gaATG_missionCompleted")) {
				victory();
				isDone = true;
			}
		}
	}
	
	public void victory() {
		VictoryScreenScript.CustomVictoryParams params = new VictoryScreenScript.CustomVictoryParams();
		
		params.name = StringHelper.getString("nex_victory", "academy_name");
		params.text = StringHelper.getString("nex_victory", "academy_text");
		params.intelText = StringHelper.getString("nex_victory", "academy_intelText");
		params.image = Global.getSettings().getSpriteName("illustrations", "active_gate");
		SectorManager.getManager().customVictory(params);
	}
	
}
