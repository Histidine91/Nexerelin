package exerelin.campaign.graphics;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class MiningCooldownDrawerV2 implements CampaignUIRenderingListener {

    public static final int BAR_WIDTH = 128;
    public static final int BAR_HEIGHT = 16;
    public static final int ICON_WIDTH = 32;
    public static final String ICON_PATH = "graphics/exerelin/icons/hammer_and_pick.png";
    public static final Color BAR_COLOR = new Color(240, 244, 159, 255);
    public static final Color FILL_COLOR = new Color(107, 179, 80, 255);

    public void glSetColor(Color color) {
        GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f,
                color.getBlue()/255f, color.getAlpha()/255f);
    }

    // most of this was copypasta'd from Common Radar, I have virtually no idea how to OpenGL
    public void drawCooldownBar(ViewportAPI view) {
        MemoryAPI mem = Global.getSector().getCharacterData().getMemoryWithoutUpdate();
        if (!mem.contains("$nex_miningCooldown"))
            return;
        float cooldown = mem.getExpire("$nex_miningCooldown");
        float amount = 1 - cooldown;
        if (amount < 0 || amount >= 1) return;

        // Set OpenGL flags
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        // I don't know why the second matrix layer is needed but it fixes the issue with the drawing being grey/black
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw bar
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Vector2f loc = fleet.getLocation();
        float x = view.convertWorldXtoScreenX(loc.x);
        float y = view.convertWorldYtoScreenY(loc.y + fleet.getRadius() + 20) + 8;
        float screenScale = Global.getSettings().getScreenScaleMult();
        //screenScale *= Global.getSector().getCampaignUI().getZoomFactor();

        x *= screenScale;
        y *= screenScale;

        int halfW = BAR_WIDTH/2, halfH = BAR_HEIGHT/2;
        int sw = (int)Global.getSettings().getScreenWidth();
        int sh = (int)Global.getSettings().getScreenHeight();

        GL11.glViewport(0, 0, sw, sh);
        GL11.glOrtho(0.0, sw, 0.0, sh, -1.0, 1.0);
        GL11.glLineWidth(2);
        GL11.glTranslatef(x, y, 0);

        float screenMult = 1/view.getViewMult();
        float diffFrom1 = screenMult - 1;
        screenMult -= diffFrom1/2;  // halve the distance of the multiplier from 1x
        GL11.glScalef(screenMult, screenMult, 1);
        GL11.glTranslatef((-halfW + ICON_WIDTH/2) * screenScale, 0, 0);

        // bar fill
        int length = (int)(BAR_WIDTH * amount);
        glSetColor(FILL_COLOR);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2i(0, halfH);
        GL11.glVertex2i(length, halfH);
        GL11.glVertex2i(length, -halfH);
        GL11.glVertex2i(0, -halfH);
        GL11.glEnd();

        // bar outline
        glSetColor(BAR_COLOR);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2i(0, halfH);
        GL11.glVertex2i(BAR_WIDTH, halfH);
        GL11.glVertex2i(BAR_WIDTH, -halfH);
        GL11.glVertex2i(0, -halfH);
        GL11.glEnd();

        // icon
        GL11.glColor4f(1, 1, 1, 1);
        SpriteAPI sprite = Global.getSettings().getSprite(ICON_PATH);
        float sizeMult = 32/sprite.getWidth();
        GL11.glScalef(sizeMult, sizeMult, 1);
        sprite.render(-ICON_WIDTH*2, -ICON_WIDTH/2 - 2);

        // Finalize drawing
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {}

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        if (Global.getSector().getPlayerFleet() == null)
            return;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.isShowingDialog() || ui.isShowingMenu())
            return;
        drawCooldownBar(viewport);
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {}
}
