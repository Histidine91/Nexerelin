package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import org.lwjgl.opengl.GL11;

public class GroundUnitPanelPlugin extends FramedCustomPanelPlugin {

	protected FactionAPI faction;
	protected GroundUnit unit;
	protected String logo;
	
	public GroundUnitPanelPlugin(FactionAPI faction, GroundUnit unit, String logo)
	{
		super(0.25f, faction.getBaseUIColor(), true);

		this.faction = faction;
		this.unit = unit;
		this.logo = logo;
	}

	@Override
	public void positionChanged(PositionAPI pos) {
		this.pos = pos;
	}

	@Override
	public void renderBelow(float alphaMult) {
		
		super.renderBelow(alphaMult);
		
		float x = pos.getX();
		float y = pos.getY();
		float w = pos.getWidth();
		float h = pos.getHeight();
				
		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		// marine/armored sprite
		SpriteAPI sprite;		
		float sizeMult = pos.getHeight()/GroundUnit.PANEL_HEIGHT;
		
		if (unit != null && unit.getBackgroundIcon() != null) {
			sprite = Global.getSettings().getSprite(unit.getBackgroundIcon());
			sprite.setAlphaMult(alphaMult * 0.3f);
			sprite.setSize(sprite.getWidth() * sizeMult, sprite.getHeight() * sizeMult);
			//GL11.glScalef(0.5f, 0.5f, 1);
			sprite.renderAtCenter(x + w/2, y + h*0.6f);
		}
		
		// faction logo
		sprite = Global.getSettings().getSprite(this.logo);
		sprite.setAlphaMult(alphaMult * 0.3f);
		sprite.setSize(32 * sizeMult, 32 * sizeMult);
		sprite.render(x + w - 32 * sizeMult, y);
		
		GL11.glPopMatrix();
	}	
}
