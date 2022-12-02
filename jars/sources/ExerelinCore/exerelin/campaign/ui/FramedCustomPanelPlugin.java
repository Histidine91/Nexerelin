package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class FramedCustomPanelPlugin extends CustomPanelPluginWithInput {

	public float sideRatio = 0.5f;
	public Color color;
	public boolean square = false;
	
	public FramedCustomPanelPlugin(float sideRatio, Color color, boolean square) 
	{
		this.sideRatio = sideRatio;
		this.color = color;
		this.square = square;
	}
	
	public void renderBox(float x, float y, float w, float h, float alphaMult) {
		float lh = h * sideRatio;
		float lw = w * sideRatio;
		
		if (square) {
			if (lh > lw) lh = lw;
			else lw = lh;
		}
		
		float[] points = new float[] {
			// upper left
			0, h - lh,
			0, h,
			0 + lw, h,
			
			// upper right
			w - lw, h,
			w, h,
			w, h - lh,
				
			// lower right
			w, lh,
			w, 0,
			w - lw, 0,
			
			// lower left
			lw, 0,
			0, 0,
			0, lh
		};
				
		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 0.3f * alphaMult);
		
		for (int i=0; i<4; i++) {
			GL11.glBegin(GL11.GL_LINES);
			{
				int index = i * 6;
				
				GL11.glVertex2f(points[index] + x, points[index + 1] + y);
				GL11.glVertex2f(points[index+2] + x, points[index+3] + y);
				GL11.glVertex2f(points[index+2] + x, points[index+3] + y);
				GL11.glVertex2f(points[index+4] + x, points[index+5] + y);
			}
			GL11.glEnd();
		}		
		
		GL11.glPopMatrix();
	}

	@Override
	public void renderBelow(float alphaMult) {
	}

	@Override
	public void render(float alphaMult) {
		float x = pos.getX();
		float y = pos.getY();
		float w = pos.getWidth();
		float h = pos.getHeight();
		
		renderBox(x, y, w, h, alphaMult);
	}
}
