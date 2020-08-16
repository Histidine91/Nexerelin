package exerelin.campaign.customstart;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ui.VictoryScreenScript.CustomVictoryParams;
import exerelin.utilities.StringHelper;

public class TemplarApostateVictoryScript implements EveryFrameScript {
	
	protected boolean isDone;
	protected float interval;
	
	// runcode new exerelin.campaign.customstart.TemplarApostateVictoryScript().init()
	public void init() {
		if (!SectorManager.getManager().getStartingFactionIdsCopy().contains("templars")) {
			Global.getLogger(this.getClass()).info("Templars not present at start, apostate victory not possible");
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
			
			if (!SectorManager.isFactionAlive("templars")) {
				victory();
				isDone = true;
			}
		}
	}
	
	public void victory() {
		CustomVictoryParams params = new CustomVictoryParams();
		
		params.name = StringHelper.getString("nex_victory", "apostate_name");
		params.text = StringHelper.getString("nex_victory", "apostate_text");
		params.intelText = StringHelper.getString("nex_victory", "apostate_intelText");
		params.image = Global.getSettings().getSpriteName("illustrations", "victory_apostate");
		SectorManager.getManager().customVictory(params);
	}
	
}
