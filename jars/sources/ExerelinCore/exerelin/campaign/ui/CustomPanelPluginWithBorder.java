package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Panel with solid border. Also accepts a background color.
 */
public class CustomPanelPluginWithBorder implements CustomUIPanelPlugin {
	protected PositionAPI pos;
	protected Color color;
	protected Color bgColor;

	public CustomPanelPluginWithBorder(Color color, Color bgColor) {
		this.color = color;
		this.bgColor = bgColor;
	}

	@Override
	public void positionChanged(PositionAPI pos) {
		this.pos = pos;
	}

	@Override
	public void renderBelow(float alphaMult) {
		if (bgColor == null) return;
		
		float x = pos.getX();
		float y = pos.getY();
		float w = pos.getWidth();
		float h = pos.getHeight();

		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		GL11.glColor4f(bgColor.getRed()/255f, bgColor.getGreen()/255f, bgColor.getBlue()/255f, 
				bgColor.getAlpha()/255f * alphaMult);
		GL11.glRectf(x, y, x + w, y + h);
		GL11.glColor4f(1, 1, 1, 1);

		GL11.glPopMatrix();
	}

	@Override
	public void render(float alphaMult) {
		drawBorder(alphaMult);
	}

	public void drawBorder(float alphaMult) {
		float x = pos.getX();
		float y = pos.getY();
		float w = pos.getWidth();
		float h = pos.getHeight();

		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 0.3f * alphaMult);

		for (int i=0; i<4; i++) {
			GL11.glBegin(GL11.GL_LINE_LOOP);
			{
				GL11.glVertex2f(x, y);
				GL11.glVertex2f(x + w, y);
				GL11.glVertex2f(x + w, y + h);
				GL11.glVertex2f(x, y + h);
			}
		}		

		GL11.glEnd();
		GL11.glPopMatrix();
	}

	@Override
	public void processInput(List<InputEventAPI> events) {}

	@Override
	public void advance(float amount) {}
}
