package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.ui.CustomPanelPluginWithBorder;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.rectanglepacker.Packer;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

/**
 * Draws the tacticool planet map.
 */
public class MarketMapDrawer {
	
	/*
	TODO:
	draw arrows for last turn's enemy movements and this turn's player movements
	Implement location distribution
	*/
	
	public static final float INDUSTRY_PANEL_BASE_WIDTH = 340;
	public static final float INDUSTRY_PANEL_BASE_HEIGHT = 190;
	
	protected float width;
	protected GroundBattleIntel intel;
	protected CustomPanelAPI panel;
	protected MarketMapPanelPlugin panelPlugin;
	
	public static float getIndustryPanelSizeMult() {
		//if (true) return 0.6f;
		
		float screenWidth = Global.getSettings().getScreenWidth();
		if (screenWidth > 1920)
			return 0.9f;
		if (screenWidth >= 1600)
			return 0.7f;
		return 0.6f;
	}
	
	public static float getIndustryPanelWidth() {
		return INDUSTRY_PANEL_BASE_WIDTH * getIndustryPanelSizeMult();
	}
	
	public static float getIndustryImageWidth() {
		return 190 * getIndustryPanelSizeMult();
	}
	
	public static float getIndustryPanelHeight() {
		return INDUSTRY_PANEL_BASE_HEIGHT * getIndustryPanelSizeMult();
	}
	
	public MarketMapDrawer(GroundBattleIntel intel, CustomPanelAPI outer, float width) 
	{
		this.intel = intel;
		this.width = width;
		panelPlugin = new MarketMapPanelPlugin(this);
		panel = outer.createCustomPanel(width, width/2, panelPlugin);
	}
	
	public void init() {
		addIndustryPanels(width);
	}
	
	public void addIndustryPanels(float width) {
		int index = 0;
		float panelWidth = getIndustryPanelWidth();
		float panelHeight = getIndustryPanelHeight();
		
		List<IndustryForBattle> industries = intel.getIndustries();
		List<Rectangle> rects = new ArrayList<>();
		for (IndustryForBattle ifb : industries) {
			if (ifb.getPosOnMap() == null) {
				rects = new MapLocationGenV2().generateLocs(industries, width);
				//panelPlugin.debugRects = rects;
				//Global.getLogger(this.getClass()).info("lol " + rects.size());
				break;
			}
		}
		
		for (IndustryForBattle ifb : industries) {
			IFBPanelPlugin plugin = new IFBPanelPlugin(ifb);
			float x = ifb.getPosOnMap().x;
			float y = ifb.getPosOnMap().y;
			CustomPanelAPI indPanel = ifb.renderPanelNew(panel, panelWidth, panelHeight, x, y, plugin);
			
			index++;
		}
	}
	
	public CustomPanelAPI getPanel() {
		return panel;
	}
	
	
	public static class MarketMapPanelPlugin extends FramedCustomPanelPlugin {
	
		protected MarketMapDrawer map;
		protected String bg;
		protected List<Rectangle> debugRects;

		public MarketMapPanelPlugin(MarketMapDrawer map) 
		{			
			super(0.25f, map.intel.getFactionForUIColors().getBaseUIColor(), true);

			//this.faction = faction;
			this.map = map;
			this.bg = null;
			MarketAPI market = map.intel.getMarket();
			if (market.getPlanetEntity() != null)
				bg = market.getPlanetEntity().getSpec().getTexture();
		}
		
		@Override
		public void render(float alphaMult) {
			super.render(alphaMult);
			
			if (debugRects != null) {
				for (Rectangle r : debugRects) {
					drawDebugRect(r);
				}
			}
		}
		
		public void drawArrow(GroundUnit unit, IndustryForBattle from, IndustryForBattle to, boolean prevTurn) 
		{
			if (true) return;
			
			float x = pos.getX();
			float y = pos.getY();
			Color color = unit.getFaction().getBaseUIColor();
			if (prevTurn) {
				GL11.glColor4f(color.getRed()/255, color.getGreen()/255, color.getBlue()/255, 0.4f);
				GL11.glLineWidth(2);
			} else {
				GL11.glColor4f(color.getRed()/255, color.getGreen()/255, color.getBlue()/255, 0.6f);
				GL11.glLineWidth(3);
			}
			
			Vector2f vFrom = from.getGraphicalPosOnMap();
			Vector2f vTo = to.getGraphicalPosOnMap();
			float height = map.width/2;
			
			// TODO: add the damn arrowhead
			// also figure out a way to antialias this
			GL11.glBegin(GL11.GL_LINES);
			GL11.glVertex2f(vFrom.x + x, height-vFrom.y + y);
			GL11.glVertex2f(vTo.x + x, height-vTo.y + y);
			GL11.glEnd();
			
			GL11.glColor4f(1, 1, 1, 1);
			GL11.glLineWidth(1);
		}
		
		public void drawDebugRect(Rectangle r) {
			float x = r.x + pos.getX();
			float y = r.y + pos.getY();
			GL11.glRectf(x, y, x + r.width, y + r.height);
		}

		@Override
		public void renderBelow(float alphaMult) {
			super.renderBelow(alphaMult);
			
			//if (bg == null) return;

			float x = pos.getX();
			float y = pos.getY();
			float w = pos.getWidth();
			float h = pos.getHeight();

			GL11.glPushMatrix();
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_LINE_SMOOTH);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			//GL11.glEnable(GL11.GL_LINE_STIPPLE);
			
			String spriteId = this.bg != null? this.bg : map.intel.getMarket().getContainingLocation().getBackgroundTextureFilename();
			
			SpriteAPI bgSprite = Global.getSettings().getSprite(spriteId);
			bgSprite.setSize(w-4, h-4);
			if (this.bg == null) {
				//bgSprite.setTexWidth(w-4);
				//bgSprite.setTexHeight(h-4);
			}
			bgSprite.renderAtCenter(x + w/2, y + h/2);
						
			for (GroundUnit unit : map.intel.getMovedFromLastTurn().keySet()) {
				IndustryForBattle prev = map.intel.getMovedFromLastTurn().get(unit);
				IndustryForBattle curr = unit.getLocation();
				if (curr != null) continue;
				drawArrow(unit, prev, curr, true);
			}
			for (GroundUnit unit : map.intel.getPlayerData().getUnits()) {
				IndustryForBattle curr = unit.getLocation();
				IndustryForBattle next = unit.getDestination();
				if (curr == null || next == null) continue;
				drawArrow(unit, curr, next, false);
			}
			GL11.glDisable(GL11.GL_LINE_STIPPLE);
			GL11.glPopMatrix();
		}
	}
	
	public static class IFBPanelPlugin extends CustomPanelPluginWithBorder {
		protected static final Color BG_COLOR = new Color(0f, 0f, 0f, 0.5f);
		
		//protected GroundBattleIntel intel;
		protected IndustryForBattle ifb;
		protected boolean isContested;
		protected int interval;
		protected short stippleIndex = 7;
				
		public IFBPanelPlugin(IndustryForBattle ifb) {
			super(getColor(ifb), BG_COLOR);
			this.ifb = ifb;
			isContested = ifb.isContested();
		}
		
		protected static Color getColor(IndustryForBattle ifb) {
			Color color = Misc.getPositiveHighlightColor();
			if (ifb.heldByAttacker != ifb.getIntel().isPlayerAttackerForGUI())
				color = Misc.getNegativeHighlightColor();
			return color;
		}
				
		@Override
		public void render(float alphaMult) {
			if (isContested) {
				//GL11.glEnable(GL11.GL_LINE_STIPPLE);
				GL11.glLineWidth(2);
				//GL11.glLineStipple(2, (short)stippleIndex);
				alphaMult *= 0.5f + 0.5f * Math.sin(interval/60f * Math.PI);
				drawBorder(alphaMult);
				//GL11.glDisable(GL11.GL_LINE_STIPPLE);
			}
			else {
				GL11.glLineWidth(1);
				drawBorder(alphaMult);
			}
		}

		@Override
		public void advance(float amount) {
			interval++;
			if (interval > 60) {
				interval = 0;
				//stippleIndex++;
			}			
		}
	}
	
	/**
	 * Generates a semi-random location on the map for each of the industries.
	 */
	public static class MapLocationGenV2 {
		public List<Rectangle> rects = new ArrayList<>();
		
		public List<Rectangle> generateLocs(List<IndustryForBattle> industries, float mapWidth) 
		{
			// work with a copy of the actual arg, since we'll be modifying it
			industries = new LinkedList<>(industries);
			
			float baseWidth = getIndustryPanelWidth();
			float baseHeight = getIndustryPanelHeight();
			
			int max = industries.size() + Math.max(0, 6 - industries.size()/2);
			for (int i=0; i<max; i++) {
				float width = baseWidth * MathUtils.getRandomNumberInRange(1.1f, 1.5f);
				float height = baseHeight * MathUtils.getRandomNumberInRange(1.1f, 1.3f);
				rects.add(new Rectangle(0, 0, (int)width, (int)height));
			}
			
			// add some extra unusable rectangles to mess up the tesselation
			List<Rectangle> rectsForPack = new ArrayList<>(rects);
			int maxExtra = Math.max(3, 14 - industries.size());
			for (int i=0; i<maxExtra; i++) {
				float width = baseWidth * MathUtils.getRandomNumberInRange(0.3f, 0.5f);
				float height = baseHeight * MathUtils.getRandomNumberInRange(0.3f, 0.5f);
				rectsForPack.add(new Rectangle(0, 0, (int)width, (int)height));
			}
			
			Packer.pack(rectsForPack, Packer.Algorithm.BEST_FIT_DECREASING_HEIGHT, (int)mapWidth);
			
			int offsetX = (int)(mapWidth - getRightmostPoint())/2;
			int offsetY = (int)(mapWidth/2 - getLowestPoint())/2;
			for (Rectangle r : rects) {
				r.x += offsetX;
				r.y += offsetY;
			}				
			
			// Pick random rectangles for our industries
			WeightedRandomPicker<Rectangle> picker = new WeightedRandomPicker<>();
			picker.addAll(rects);
						
			List<Rectangle> toUse = new LinkedList<>();
			for (int i=0; i<industries.size(); i++) {
				toUse.add(picker.pickAndRemove());
			}
			
			// Give the rectangle closest to the center to spaceport?
			// center being the average position of selected rects rather than the map center
			IndustryForBattle spaceport = null;
			for (IndustryForBattle ifb : industries) {
				if (ifb.getIndustry().getSpec().hasTag(Industries.TAG_SPACEPORT)) {
					spaceport = ifb;
					break;
				}
			}
			
			if (spaceport != null) {
				Rectangle best = getClosestToCenter(toUse);
				toUse.remove(best);
				assignPosFromRectangle(spaceport, best, baseWidth, baseHeight);
				industries.remove(spaceport);
			}
			
			// assign the other industries
			for (IndustryForBattle ifb : industries) {
				Rectangle r = toUse.remove(0);
				assignPosFromRectangle(ifb, r, baseWidth, baseHeight);
			}
			
			return rects;
		}
		
		public void assignPosFromRectangle(IndustryForBattle ifb, Rectangle r,
				float baseWidth, float baseHeight) 
		{
			int x = r.x;
			int y = r.y;
			
			// vary the actual position within the available rectangle
			x += Math.random() * (r.width - baseWidth);
			y += Math.random() * (r.height - baseHeight);

			//Global.getLogger(this.getClass()).info(String.format("%s location on map: %s, %s", ifb.getName(), x, y));
			ifb.setPosOnMap(new Vector2f(x, y));
		}
		
		public int getRightmostPoint() {
			int best = 0;
			for (Rectangle r : rects) {
				int curr = r.x + r.width;
				if (curr > best) best = curr;
			}
			return best;
		}
		
		public int getLowestPoint() {
			int best = 0;
			for (Rectangle r : rects) {
				int curr = r.y + r.height;
				if (curr > best) best = curr;
			}
			return best;
		}
		
		public Rectangle getClosestToCenter(List<Rectangle> rects) {
			Vector2f avg = getAveragePosition(rects);
			
			float bestDistSq = Float.MAX_VALUE;
			Rectangle best = null;
			for (Rectangle r : rects) {
				Vector2f center = new Vector2f((float)r.getCenterX(), (float)r.getCenterY());
				float distSq = MathUtils.getDistanceSquared(center, avg);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					best = r;
				}
			}
			return best;
		}
		
		public Vector2f getAveragePosition(List<Rectangle> rects) {
			float x = 0, y = 0;
			for (Rectangle r : rects) {
				x += r.getCenterX();
				y += r.getCenterY();
			}
			x /= rects.size();
			y /= rects.size();
			return new Vector2f(x, y);
		}
	}
	
	/**
	 * Generates a semi-random location on the map for each of the industries.
	 * This is terrible, get a proper rectangle packing algorithm instead.
	 */
	@Deprecated
	public static class MapLocationGen {
		
		public float rectWidth, rectHeight;
		public float mapWidth, mapHeight;
		public float cellWidth, cellHeight;
		
		public int numColumns, numRows;
		//public int firstRowIndex, firstColIndex;
		public int firstUsedRowIndex, lastUsedRowIndex, firstUsedColIndex, lastUsedColIndex;
		
		public MapLocationGen(float mapWidth, float mapHeight, float rectWidth, float rectHeight) {
			this.mapWidth = mapWidth;
			this.mapHeight = mapHeight;
			this.rectWidth = rectWidth;
			this.rectHeight = rectHeight;
		}
		
		/**
		 * Sets the map positions of each industry on the map.
		 * @param industries
		 */
		public void assign(List<IndustryForBattle> industries) {
			numColumns = Math.max(2, (int)(mapWidth/(rectWidth * 1.1f)));
			numRows = (int)(mapHeight/(rectHeight * 1.5f));
			
			cellWidth = (float)Math.floor(mapWidth/numColumns);
			cellHeight = (float)Math.floor(mapHeight/numRows);
			
			//firstRowIndex = -numRows/2;
			//firstColIndex = -numColumns/2;
			
			int[] numUsedCells = getUsedCellCounts(industries.size());
			
			firstUsedRowIndex = -numUsedCells[1]/2;
			lastUsedRowIndex = numUsedCells[1] + firstUsedRowIndex - 1;
			firstUsedColIndex = -numUsedCells[0]/2;
			lastUsedColIndex = numUsedCells[0] + firstUsedColIndex - 1;
			
			Global.getLogger(this.getClass()).info(String.format("First row: %s, last row: %s", firstUsedRowIndex, lastUsedRowIndex));
			Global.getLogger(this.getClass()).info(String.format("First col: %s, last col: %s", firstUsedColIndex, lastUsedColIndex));
			
			WeightedRandomPicker<Integer[]> picker = new WeightedRandomPicker<>();
			for (int i=firstUsedColIndex; i<=lastUsedColIndex; i++) {
				for (int j=firstUsedRowIndex; j<=lastUsedRowIndex; j++) {
					Global.getLogger(this.getClass()).info("Adding cell: " + i + ", " + j);
					picker.add(new Integer[] {i, j});
				}
			}
			for (IndustryForBattle ifb : industries) {
				Integer[] pos = picker.pickAndRemove();
				Global.getLogger(this.getClass()).info("Picked cell: " + pos[0] + ", " + pos[1]);
				float x = pos[0] * cellWidth;
				float y = pos[1] * cellHeight;
				x += mapWidth/2;
				y += mapHeight/2;
				
				float xVar = cellWidth - rectWidth;
				float yVar = cellHeight - rectHeight;
				x += Math.random() * xVar;
				y += Math.random() * yVar;
				
				x = (int)x;
				y = (int)y;
				
				Global.getLogger(this.getClass()).info(String.format("%s location on map: %s, %s", ifb.getName(), x, y));
				ifb.setPosOnMap(new Vector2f(x, y));
			}
		}
		
		public int[] getUsedCellCounts(int numIndustries) {
			int a = 1, b = 1;
			boolean incrementRows = false;
			while (a * b < numIndustries) {
				if (incrementRows) a++;
				else b++;
				incrementRows = !incrementRows;
			}
			//Global.getLogger(this.getClass()).info(a + ", " + b);
			return new int[]{a, b};
		}
	}
}
