package exerelin.campaign.ui;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * Panel with solid border. Also accepts a background color.
 */
public class CustomPanelPluginWithBorder extends CustomPanelPluginWithInput {
	@Getter	@Setter	protected Color color;
	@Getter	@Setter	protected Color bgColor;
	@Getter @Setter protected boolean highlight;

	public CustomPanelPluginWithBorder(Color color, Color bgColor) {
		this.color = color;
		this.bgColor = bgColor;
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

		glColorWithMult(bgColor, highlight ? 1.5f : 1, alphaMult);
		GL11.glRectf(x, y, x + w, y + h);
		GL11.glColor4f(1, 1, 1, 1);

		GL11.glPopMatrix();
	}

	protected float multiplyColor(float base, float mult) {
		return Math.min(base * mult, 255);
	}

	protected void glColorWithMult(Color color, float mult, float alphaMult) {
		float red = multiplyColor(color.getRed(), mult)/255f;
		float green = multiplyColor(color.getGreen(), mult)/255f;
		float blue = multiplyColor(color.getBlue(), mult)/255f;
		float alpha = multiplyColor(color.getAlpha(), alphaMult)/255f;

		GL11.glColor4f(red, green, blue, alpha);
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

		glColorWithMult(color, highlight ? 1.5f : 1, 0.3f * alphaMult);

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
}
