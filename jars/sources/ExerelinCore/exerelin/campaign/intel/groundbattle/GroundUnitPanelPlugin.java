package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class GroundUnitPanelPlugin implements CustomUIPanelPlugin {
	
	protected PositionAPI pos;
	
	protected FactionAPI faction;
	protected String sprite;
	protected String logo;
	
	public GroundUnitPanelPlugin(FactionAPI faction, String sprite, String logo) 
	{
		this.faction = faction;
		this.sprite = sprite;
		this.logo = logo;
	}

	@Override
	public void positionChanged(PositionAPI pos) {
		this.pos = pos;
	}
	
	public void renderBox(float x, float y, float w, float h, float alphaMult) {
		float l = h/4;
		
		float[] points = new float[] {
			// upper left
			0, h - l,
			0, h,
			0 + l, h,
			
			// upper right
			w - l, h,
			w, h,
			w, h - l,
				
			// lower right
			w, l,
			w, 0,
			w - l, 0,
			
			// lower left
			l, 0,
			0, 0,
			0, l
		};
		
		Color fc = faction.getBaseUIColor();
		GL11.glColor4f(fc.getRed()/255f, fc.getGreen()/255f, fc.getBlue()/255f, 0.3f * alphaMult);
		
		for (int i=0; i<4; i++) {
			GL11.glBegin(GL11.GL_LINES);
			{
				int index = i * 6;
				
				GL11.glVertex2f(points[index] + x, points[index + 1] + y);
				GL11.glVertex2f(points[index+2] + x, points[index+3] + y);
				GL11.glVertex2f(points[index+2] + x, points[index+3] + y);
				GL11.glVertex2f(points[index+4] + x, points[index+5] + y);
			}
		}		
		
		GL11.glEnd();
	}

	@Override
	public void renderBelow(float alphaMult) {
		float x = pos.getX();
		float y = pos.getY();
		float w = pos.getWidth();
		float h = pos.getHeight();
		
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		// tactical box
		renderBox(x, y, w, h, alphaMult);
		
		// marine/armored sprite
		SpriteAPI sprite = Global.getSettings().getSprite(this.sprite);
		sprite.setAlphaMult(alphaMult * 0.3f);
		//GL11.glScalef(0.5f, 0.5f, 1);
		sprite.renderAtCenter(x + w/2, y + h*0.65f);
		
		// faction logo
		sprite = Global.getSettings().getSprite(this.logo);
		sprite.setAlphaMult(alphaMult * 0.3f);
		sprite.setSize(32f, 32f);
		sprite.render(x + w - 32, y);
	}

	@Override
	public void render(float alphaMult) {
		
	}

	@Override
	public void advance(float amount) {
		
	}

	@Override
	public void processInput(List<InputEventAPI> arg0) {
		
	}
	
}
