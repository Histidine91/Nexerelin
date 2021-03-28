package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.SpecialItemRendererAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtilsFaction;
import exerelin.world.factionsetup.FactionSetupHandler.FactionSetupItemDef;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;

public abstract class FactionSetupItem {
	
	public static LazyFont font;
	
	static {
		try
		{
			font = LazyFont.loadFont(Global.getSettings().getString("nex_factionSetupNumFont"));
		}
		catch (FontException ex)
		{
			throw new RuntimeException("Failed to load font", ex);
		}
	}
	
	protected String id;
	
	public void init(String id) {
		this.id = id;
	}
	
	public int getCost() {
		return getDef().cost;
	}
	
	public boolean isEnabled() {
		return true;
	}
	
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) {
        tooltip.setTitleOrbitronLarge();
        
		FactionSetupHandler.FactionSetupItemDef def = getDef();
		tooltip.addTitle(def.name);
    }
	
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) 
	{
		FactionSetupItemDef def = getDef();
		if (def.sprite != null) {
			SpriteAPI sprite = Global.getSettings().getSprite(def.sprite);
			float mult = 1f;
			sprite.setAlphaMult(alphaMult * mult);
			sprite.setNormalBlend();
			sprite.renderAtCenter(x + w/2, y + h/2);
		}
	}
		
	public void renderAfter(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) 
	{
		int cost = getCost();
		int fontSize = 21;
		LazyFont.DrawableString str = font.createText(cost + "", Color.LIGHT_GRAY, fontSize, 32);
		float cx = x + w / 2f;
        float cy = y + h / 2f;
		str.draw(-w/2 + 4, h/2 - 2);
	}
	
	public abstract void apply();
		
	public FactionSetupItemDef getDef() {
		return FactionSetupHandler.getDef(id);
	}
	
	protected MarketAPI getPlayerHome() {
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
		if (!markets.isEmpty()) return markets.get(0);
		return null;
	}
}
