package exerelin.campaign;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@Deprecated
public class MiningCooldownDrawer extends BaseCustomEntityPlugin {
	
	public static final String MEMORY_KEY_ENTITY = "$nex_miningCooldownDrawer";
			
	public void glSetColor(Color color) {
		GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f, 
				color.getBlue()/255f, color.getAlpha()/255f);
	}
	
	public static SectorEntityToken create() {
		//Global.getLogger(MiningCooldownDrawer.class).info("Creating entity");
		if (getEntity() != null && getEntity().isAlive()) {
			//Global.getSector().getCampaignUI().addMessage("Warning: Mining cooldown GUI entity already exists", Color.RED);
			remove();
		}
		
		return Global.getSector().getPlayerFleet().getContainingLocation()
				.addCustomEntity("nex_mining_gui_dummy", null, "nex_mining_gui_dummy", null);
	}
	
	public static SectorEntityToken getEntity() {
		return Global.getSector().getMemoryWithoutUpdate().getEntity(MEMORY_KEY_ENTITY);
	}
	
	public static void remove() {
		SectorEntityToken entity = getEntity();
		if (entity != null) {
			entity.getContainingLocation().removeEntity(entity);
			Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_KEY_ENTITY);
		}
	}
	
	@Override
	public void init(SectorEntityToken entity, Object pluginParams) {
		super.init(entity, pluginParams);
		Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY_ENTITY, entity);
	}
	
	@Override
	public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
		//entity.addFloatingText("bla", Color.white, 0.3f);
		if (Global.getCurrentState() == GameState.TITLE)
			return;
		if (Global.getSector().getPlayerFleet() == null)
			return;
		CampaignUIAPI ui = Global.getSector().getCampaignUI();
		if (ui.isShowingDialog() || ui.isShowingMenu())
			return;
		//MiningCooldownDrawerV2.drawCooldownBar(viewport);
	}
	
	@Override
	public float getRenderRange() {
		return 1000000;
	}
	
	@Override
	public void advance(float amount) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (player == null) return;
		
		LocationAPI curr = Global.getSector().getCurrentLocation();
		if (curr != entity.getContainingLocation()) {
			entity.getContainingLocation().removeEntity(entity);
			curr.addEntity(entity);
		}
		else {
			//entity.setLocation(player.getLocation().getX(), player.getLocation().getY());
		}
	}
}
