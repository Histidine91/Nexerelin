package exerelin.campaign.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
public abstract class DelayedDialogScreenScript implements EveryFrameScript
{
	protected static final float DAYS_TO_WAIT = 0.1f;
	protected boolean isDone = false;
	protected float timer = 0;

	@Override
	public boolean isDone()
	{
		return isDone;
	}

	@Override
	public boolean runWhilePaused()
	{
		return true;
	}
	
	public boolean shouldCancel() {
		return false;
	}
	
	@Override
	public void advance(float amount)
	{
		// Don't do anything while in a menu/dialog
		CampaignUIAPI ui = Global.getSector().getCampaignUI();
		if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog() || Global.getCurrentState() == GameState.TITLE)
		{
			return;
		}
		
		timer += Global.getSector().getClock().convertToDays(amount);
		if (timer < DAYS_TO_WAIT) return;
		
		if (shouldCancel()) {
			isDone = true;
			return;
		}
		
		if (!isDone)
		{
			showDialog();
			isDone = true;
		}
	}
	
	protected abstract void showDialog();
}
