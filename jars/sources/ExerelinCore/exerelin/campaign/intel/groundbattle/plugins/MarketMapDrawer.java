package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.ui.CustomPanelPluginWithBorder;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.rectanglepacker.Packer;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Draws the tacticool planet map.
 */
public class MarketMapDrawer {
	
	public static final boolean DEBUG_MODE = false;
	
	public static final float INDUSTRY_PANEL_BASE_WIDTH = 330;
	public static final float INDUSTRY_PANEL_BASE_HEIGHT = 171;

	public static LazyFont debugFont;

	public static Logger log = Global.getLogger(MarketMapDrawer.class);
	
	public static final List<Color> DEBUG_COLORS = new ArrayList<>();
	static {
		for (int i=0; i<50; i++) {
			DEBUG_COLORS.add(getRandomColor());
		}

		if (DEBUG_MODE) {
			try {
				debugFont = LazyFont.loadFont("graphics/fonts/insignia16a.fnt");
			} catch (Exception ex) {
				log.info("Failed to load font", ex);
			}
		}
	}
	public static Color getRandomColor() {
		return new Color(
				MathUtils.getRandomNumberInRange(0, 255),
				MathUtils.getRandomNumberInRange(0, 255),
				MathUtils.getRandomNumberInRange(0, 255)
		);
	}
	
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
		return 190 * 0.9f * getIndustryPanelSizeMult();	// TODO non-magic number mult?
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
			if (DEBUG_MODE || ifb.getPosOnMap() == null) {
				rects = new MapLocationGenV2().generateLocs(industries, width, intel.getMarket().getPlanetEntity() == null);
				panelPlugin.debugRects = rects;
				break;
			}
		}
		
		for (IndustryForBattle ifb : industries) {
			IFBPanelPlugin plugin = new IFBPanelPlugin(ifb);
			float x = ifb.getPosOnMap().x;
			float y = ifb.getPosOnMap().y;
			CustomPanelAPI indPanel = ifb.renderPanelNew(panel, panelWidth, panelHeight, plugin);
			panel.addComponent(indPanel).inTL(x, y);
			
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

			if (!DEBUG_MODE) return;

			if (debugRects != null) {
				int num = 0;
				for (Rectangle r : debugRects) {
					Color color = DEBUG_COLORS.get(num);
					drawDebugRect(r, color, num);
					num++;
				}
			}

			/*
			int num = 0;
			for (IndustryForBattle ifb : map.intel.getIndustries()) {
				num++;
				IFBPanelPlugin plugin = new IFBPanelPlugin(ifb);
				Color color = DEBUG_COLORS.get(num);
				drawDebugSpot(ifb.getPosOnMap().x, ifb.getPosOnMap().y, color);
			}
			 */
		}

		public boolean doesRectOverlapEdge(Rectangle rect) {
			float width = map.width;
			float height = map.width/2;

			if (rect.x < 0) return true;
			if (rect.x + rect.width > width) return true;
			if (rect.y < 0) return true;
			if (rect.y + rect.height > height) return true;

			return false;
		}
		
		public void drawArrow(GroundUnit unit, IndustryForBattle from, IndustryForBattle to, boolean prevTurn) 
		{
			try {
				float x = pos.getX();
				float y = pos.getY();
				Color color = unit.getFaction().getBaseUIColor();

				Vector2f vFrom = from.getGraphicalPosOnMap();
				Vector2f vTo = to.getGraphicalPosOnMap();
				float height = map.width/2;

				Vector2f col1 = getCollisionPoint(vFrom, vTo, from.getRectangle());
				Vector2f col2 = getCollisionPoint(vFrom, vTo, to.getRectangle());

				if (col1 == null || col2 == null)
					return;

				// panel coordinates to screen coordinates
				col1.x += x; 
				col1.y = height - col1.y + y;
				col2.x += x; 
				col2.y = height - col2.y + y;

				renderArrow(col1, col2, 10, color, 0.4f);
			} catch (Exception ex) {}
		}
		
		public void drawDebugRect(Rectangle r, Color color, int num) {
			float x = r.x + pos.getX();
			float y = r.y + pos.getY();

			Color fontColor = Color.WHITE;
			if (doesRectOverlapEdge(r)) fontColor = Color.orange;
			if (debugFont != null) {
				LazyFont.DrawableString str = debugFont.createText(num + "", fontColor, 32, 48);
				str.draw(x + 4, y + r.height);
			}
			GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 0.5f);
			GL11.glRectf(x, y, x + r.width, y + r.height);
			GL11.glColor4f(1, 1, 1, 1);
		}
		
		public void drawDebugSpot(float x, float y, Color color) {
			GL11.glColor4f(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 0.5f);
			GL11.glRectf(x - 4, y - 4, x + 4, y + 4);
			GL11.glColor4f(1, 1, 1, 1);
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

			/*
			try {
				Global.getSettings().loadTexture(spriteId);
			} catch (IOException ex) {}
			 */
			SpriteAPI bgSprite = Global.getSettings().getSprite(spriteId);
			float drawW = w-4;
			float drawH = h-2;
			if (bg == null) {
				// draw star system background
				bgSprite.setSize(drawW, drawW);
				
				// I have no idea why this combination of values works, but it does
				// note that tx, ty, tw and th are expressed as fractions of the overall sprite size
				bgSprite.renderRegion(x, y - h/2,
							0f,
							0.25f,
							1, 1f * (drawH/drawW)
						);
				
				GL11.glPushMatrix();
				GL11.glTranslatef(x + w/2, y + h/2, 0);
				//bgSprite.renderRegion(x, y,0, 0, 512, 512);
				
				//draw space station
				SpriteAPI bgSprite2 = Global.getSettings().getSprite("misc", "nex_station_render");
				float hMult = drawH * 0.9f / bgSprite2.getHeight();
				bgSprite2.setSize(bgSprite2.getWidth() * hMult, bgSprite2.getHeight() * hMult);
				GL11.glColor4f(1, 1, 1, 0.5f);
				GL11.glRotatef(10, 0, 0, 1);
				bgSprite2.renderAtCenter(0, 0);
				GL11.glPopMatrix();
			}
			else {
				// draw planet texture
				bgSprite.setSize(drawW, drawH);
				bgSprite.renderAtCenter(x + w/2, y + h/2);
			}
			
			
			for (GroundUnit unit : map.intel.getMovedFromLastTurn().keySet()) {
				if (unit.isPlayer()) continue;
				IndustryForBattle prev = map.intel.getMovedFromLastTurn().get(unit);
				IndustryForBattle curr = unit.getLocation();
				if (curr == null || prev == null) continue;
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
		public static final Color BG_COLOR = new Color(0f, 0f, 0f, 0.5f);
		public static final Color BG_COLOR_HIGHLIGHT = new Color(0.2f, 0.25f, 0.3f, 0.5f);
		
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

		public IndustryForBattle getIFB() {
			return ifb;
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
		
		public int getMaxRects(float mapWidth, float rectWidth, float rectHeight) {
			float mapHeight = mapWidth/2;
			
			int columns = (int)(mapWidth/rectWidth + 0.25f);
			int rows = (int)(mapHeight/rectHeight + 0.25f);
			//log.info("Maximum rectangles for placement: " + rows * columns);
			return rows * columns;
		}

		/**
		 * Generates a bunch of rectangles and tries to pack them into the map; picks random rectangles; assigns the
		 * industry panels to random rectangles; and gives each panel a randomized position in its rectangle.
		 * @param industries
		 * @param mapWidth
		 * @param isStation
		 * @return
		 */
		public List<Rectangle> generateLocs(List<IndustryForBattle> industries, float mapWidth, boolean isStation)
		{
			// work with a copy of the actual arg, since we'll be modifying it
			industries = new LinkedList<>(industries);
			
			float mapHeight = mapWidth/2;
			
			float baseWidth = getIndustryPanelWidth();
			float baseHeight = getIndustryPanelHeight();
			
			boolean useNewAlgo = false;
			
			if (useNewAlgo) {
				rects = generateRectangles((int)mapWidth, (int)mapHeight, 
					Math.round(baseWidth * 1.2f), Math.round(baseHeight * 1.2f));
			}
			else {
				int max = 99;	//industries.size() + Math.max(0, 10 - industries.size()/2);
				max = Math.min(max, getMaxRects(mapWidth, baseWidth * 1.2f, baseHeight * 1.25f));
				if (max < industries.size()) max = industries.size();
				
				for (int i=0; i<max; i++) {
					float width = baseWidth * 1.2f;	//MathUtils.getRandomNumberInRange(1.1f, 1.3f);
					float height = baseHeight * 1.2f;	//MathUtils.getRandomNumberInRange(1.1f, 1.4f);
					rects.add(new Rectangle(0, 0, (int)width, (int)height));
				}

				Packer.pack(rects, Packer.Algorithm.FIRST_FIT_DECREASING_HEIGHT, (int)mapWidth);

				int offsetX = (int)(mapWidth - getRightmostPoint())/2;
				int offsetY = (int)(mapWidth/2 - getLowestPoint())/2;
				for (Rectangle r : rects) {
					r.x += offsetX;
					r.y += offsetY;
				}
			}
			
			List<Pair<Rectangle, Float>> rectsSorted = getRectsSortedByDist(rects);
			
			// Give the rectangle closest to the center to spaceport?
			// center being the average position of selected rects rather than the map center
			IndustryForBattle spaceport = null;
			for (IndustryForBattle ifb : industries) {
				if (ifb.getIndustry().getSpec().hasTag(Industries.TAG_SPACEPORT)) {
					spaceport = ifb;
					break;
				}
			}			
			
			List<Rectangle> picked = new ArrayList<>();
			
			if (spaceport != null) {
				Rectangle best = rectsSorted.remove(0).one;
				assignPosFromRectangle(spaceport, best, baseWidth, baseHeight, mapWidth);
				picked.add(best);
				industries.remove(spaceport);
			}
			
			// assign the other industries
			for (IndustryForBattle ifb : industries) {
				Rectangle r = pickFromListMaybeOffset(rectsSorted).one;
				if (r != null) {
					picked.add(r);
					assignPosFromRectangle(ifb, r, baseWidth, baseHeight, mapWidth);
				}
			}
			
			return rects;
			//return picked;
		}

		/**
		 * Pick a random element from the list, but not the very last ones.
		 * @param list
		 * @param <T>
		 * @return
		 */
		public <T> T pickFromListMaybeOffset(List<T> list) {
			if (list.isEmpty()) return null;
			
			int index = 0;
			int maxOffset = list.size() - list.size()/4 - 1;
			if (maxOffset < 0) maxOffset = 0;
			index += MathUtils.getRandomNumberInRange(0, maxOffset);
			return list.remove(index);
		}
		
		public List<Pair<Rectangle, Float>> getRectsSortedByDist(List<Rectangle> rects) {
			Vector2f avg = getAveragePosition(rects);
			List<Pair<Rectangle, Float>> results = new LinkedList<>();
			
			for (Rectangle r : rects) {
				Vector2f center = new Vector2f((float)r.getCenterX(), (float)r.getCenterY());
				
				// true distance
				//float dist = MathUtils.getDistance(center, avg);
				
				// taxicab distance
				float dist = Math.abs(center.x - avg.x) + Math.abs(center.y - avg.y);
				
				results.add(new Pair<>(r, dist));
			}
			
			Collections.sort(results, new NexUtils.PairWithFloatComparator(false));
			
			return results;
		}
		
		public void assignPosFromRectangle(IndustryForBattle ifb, Rectangle r,
				float baseWidth, float baseHeight, float mapWidth) 
		{
			int x = r.x;
			int y = Math.round(mapWidth/2 - r.y - baseHeight);
			
			// vary the actual position within the available rectangle
			x += Math.random() * (r.width - baseWidth - 4) + 2;
			y -= Math.random() * (r.height - baseHeight - 2) + 1;
			
			// clamp to edges
			if (x < 0) x -= x;
			if (y < 0) y -= y;
			float overshoot = x + getIndustryPanelWidth() - mapWidth + 2;
			if (overshoot > 0) x -= overshoot;
			overshoot = y + getIndustryPanelHeight() - mapWidth/2 + 2;
			if (overshoot > 0) y -= overshoot;

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
	 * Generate rectangles by repeated bisection.
	 * @param outerWidth
	 * @param outerHeight
	 * @param minWidth
	 * @param minHeight
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public static List<Rectangle> generateRectangles(int outerWidth, int outerHeight, int minWidth, int minHeight) 
	{
		// first place initial panel
		int x = Math.round(minWidth * MathUtils.getRandomNumberInRange(1.1f, 1.15f));
		int y = Math.round(minHeight * MathUtils.getRandomNumberInRange(1.1f, 1.15f));
		
		Random random = new Random();
		if (random.nextBoolean()) x = outerWidth - x - minWidth;
		if (random.nextBoolean()) y = outerHeight - y - minHeight;
		
		// generate initial 5 rectangles by extending central rectangle's bounds vertically
		// actually, maybe try 9 rectangles
		List<Rectangle> rects = new LinkedList<>();
		
		rects.add(new Rectangle(x, y, minWidth, minHeight));									// center
		rects.add(new Rectangle(0, 0, x, outerHeight));											// left
		rects.add(new Rectangle(x + minWidth, 0, outerWidth - x - minWidth, outerHeight));	// right
		rects.add(new Rectangle(x, 0, minWidth, y));											// bottom
		rects.add(new Rectangle(x, y + minHeight, minWidth, outerHeight - y - minHeight));	// top
		
		// generate new rectangles by splitting existing ones
		splitRectsLoop(rects, minWidth, minHeight);
		return rects;
	}
	
	@Deprecated
	public static void splitRectsLoop(List<Rectangle> rects, int baseWidth, int baseHeight) 
	{
		boolean allow4Split = true;
		int minWidth = (int)getIndustryPanelWidth();
		int minHeight = (int)getIndustryPanelHeight();
		
		boolean didAnything = false;
		int iter = 0;
		do {
			didAnything = false;
			iter++;
			List<Rectangle> rectsIter = new ArrayList<>(rects);
			for (Rectangle rect : rectsIter) {
				int w = rect.width;
				int h = rect.height;
				int x = rect.x;
				int y = rect.y;
								
				// divide into four lengthwise
				if (allow4Split && w > baseWidth * 4) {
					Integer[] part = splitUnevenly(w, 4, minWidth).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, part[0], h));
					rects.add(new Rectangle(x + part[0], y, part[1], h));
					rects.add(new Rectangle(x + part[0] + part[1], y, part[2], h));
					rects.add(new Rectangle(x + part[0] + part[1] + part[2], y, part[3], h));
					rects.remove(rect);
					didAnything = true;
				}
				// divide into three lengthwise
				else if (w > baseWidth * 3) {
					Integer[] part = splitUnevenly(w, 3, minWidth).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, part[0], h));
					rects.add(new Rectangle(x + part[0], y, part[1], h));
					rects.add(new Rectangle(x + part[0] + part[1], y, part[2], h));
					rects.remove(rect);
					didAnything = true;
				}
				// divide into two lengthwise
				else if (w > baseWidth * 2) {
					Integer[] part = splitUnevenly(w, 2, minWidth).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, part[0], h));
					rects.add(new Rectangle(x + part[0], y, part[1], h));
					rects.remove(rect);
					didAnything = true;
				}
				// divide into four heightwise
				else if (allow4Split && h > baseHeight * 4) {
					Integer[] part = splitUnevenly(h, 4, minHeight).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, w, part[0]));
					rects.add(new Rectangle(x, y + part[0], w, part[1]));
					rects.add(new Rectangle(x, y + part[0] + part[1], w, part[2]));
					rects.add(new Rectangle(x, y + part[0] + part[1] + part[2], w, part[3]));
					rects.remove(rect);
					didAnything = true;
				}
				// divide into three heightwise
				else if (h > baseHeight * 3) {
					Integer[] part = splitUnevenly(h, 3, minHeight).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, w, part[0]));
					rects.add(new Rectangle(x, y + part[0], w, part[1]));
					rects.add(new Rectangle(x, y + part[0] + part[1], w, part[2]));
					rects.remove(rect);
					didAnything = true;
				}
				// divide into two heightwise
				else if (h > baseHeight * 2) {
					Integer[] part = splitUnevenly(h, 2, minHeight).toArray(new Integer[]{});
					rects.add(new Rectangle(x, y, w, part[0]));
					rects.add(new Rectangle(x, y + part[0], w, part[1]));
					rects.remove(rect);
					didAnything = true;
				}
			}
		} while (didAnything && iter < 25);
		//log.info("Splitter iterations: " + iter);
	}
	
	/**
	 * Splits {@code toDivide} into {@code divisor} parts of unequal size.
	 * @param toDivide
	 * @param divisor
	 * @param min
	 * @Deprecated
	 * @return
	 */
	public static List<Integer> splitUnevenly(int toDivide, int divisor, int min) {
		List<Integer> results = new ArrayList<>();
		int remainder = toDivide;
		for (int i=0; i<divisor; i++) {
			int index = i + 1;
			int numLeft = divisor - index;

			if (index == divisor) {
				results.add(remainder);
				break;
			}

			int ourShare = MathUtils.getRandomNumberInRange(min, remainder - (min * numLeft));
			results.add(ourShare);
			remainder -= ourShare;
		}
		Collections.shuffle(results);
		return results;
	}
		
	public static List<Pair<Vector2f, Vector2f>> getRectangleSides(Rectangle rect) {
		Vector2f tl = new Vector2f(rect.x, rect.y);
		Vector2f tr = new Vector2f(rect.x + rect.width, rect.y);
		Vector2f bl = new Vector2f(rect.x, rect.y + rect.height);
		Vector2f br = new Vector2f(rect.x + rect.width, rect.y + rect.height);
		ArrayList<Pair<Vector2f, Vector2f>> result = new ArrayList<>();
		result.add(new Pair<>(tl, tr));
		result.add(new Pair<>(tr, br));
		result.add(new Pair<>(br, bl));
		result.add(new Pair<>(bl, tl));
		
		return result;
	}
	
	// Adapted from LazyLib's CollisionUtils
	public static Vector2f getCollisionPoint(Vector2f lineStart,
                                             Vector2f lineEnd, Rectangle rect)
    {
        Vector2f closestIntersection = null;
		List<Pair<Vector2f, Vector2f>> sides = getRectangleSides(rect);
		
        for (Pair<Vector2f, Vector2f> side : sides)
        {
            Vector2f intersection = CollisionUtils.getCollisionPoint(lineStart, lineEnd, side.one, side.two);
            // Collision = true
            if (intersection != null)
            {
                if (closestIntersection == null)
                {
                    closestIntersection = new Vector2f(intersection);
                }
                else if (MathUtils.getDistanceSquared(lineStart, intersection)
                        < MathUtils.getDistanceSquared(lineStart, closestIntersection))
                {
                    closestIntersection.set(intersection);
                }
            }
        }

        // Null if no segment was hit
        // FIXME: Lines completely within bounds return null (would affect custom fighter weapons)
       return closestIntersection;
    }
	
	// from base game code: https://fractalsoftworks.com/forum/index.php?topic=5061.msg336833#msg336833
	protected static SpriteAPI texture = Global.getSettings().getSprite("graphics/hud/line32x32.png");
	protected static void renderArrow(Vector2f from, Vector2f to, float startWidth, Color color, float maxAlpha) {
		float dist = MathUtils.getDistance(from, to);
		Vector2f dir = Vector2f.sub(from, to, new Vector2f());
		if (dir.x != 0 || dir.y != 0) {
			dir.normalise();
		} else {
			return;
		}
		float arrowHeadLength = Math.min(startWidth * 0.4f * 3f, dist * 0.5f);
		dir.scale(arrowHeadLength);
		Vector2f arrowEnd = Vector2f.add(to, dir, new Vector2f());
		
		// main body of the arrow
		renderFan(from.x, from.y, arrowEnd.x, arrowEnd.y, startWidth, startWidth * 0.4f, color,
				//0f, maxAlpha * 0.5f, maxAlpha);
				maxAlpha * 0.2f, maxAlpha * 0.67f, maxAlpha);
		// arrowhead, tapers to a width of 0
		renderFan(arrowEnd.x, arrowEnd.y, to.x, to.y, startWidth * 0.4f * 3f, 0f, color,
				maxAlpha, maxAlpha, maxAlpha);
	}
	
	
	protected static void renderFan(float x1, float y1, float x2, float y2, float startWidth, float endWidth, Color color, float edge1, float mid, float edge2) {
		//HudRenderUtils.renderFan(texture, x1, y1, x2, y2, startWidth, endWidth, color, edge1, mid, edge2, false);
		edge1 *= 0.4f;
		edge2 *= 0.4f;
		mid *= 0.4f;
		boolean additive = true;
		//additive = false;
		renderFan(texture, x1, y1, x2, y2, startWidth, endWidth, color, edge1, mid, edge2, additive);
		renderFan(texture, x1 + 0.5f, y1 + 0.5f, x2 + 0.5f, y2 + 0.5f, startWidth, endWidth, color, edge1, mid, edge2, additive);
		renderFan(texture, x1 - 0.5f, y1 - 0.5f, x2 - 0.5f, y2 - 0.5f, startWidth, endWidth, color, edge1, mid, edge2, additive);
	}
	
	public static void renderFan(SpriteAPI texture, float x1, float y1, float x2, float y2, 
			float startWidth, float endWidth, Color color, 
			float edge1, float mid, float edge2, boolean additive) {

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		
		texture.bindTexture();
		
		GL11.glEnable(GL11.GL_BLEND);
		if (additive) {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		} else {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		}

		Vector2f v1 = new Vector2f(x2 - x1, y2 - y1);
		v1.normalise();
		v1.set(v1.y, -v1.x);
		Vector2f v2 = new Vector2f(v1);
		v1.scale(startWidth * 0.5f);
		v2.scale(endWidth * 0.5f);

		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		{
			// center
			GL11.glColor4ub((byte)color.getRed(), (byte)color.getGreen(), (byte)color.getBlue(), (byte)((float)color.getAlpha() * mid));
			GL11.glTexCoord2f(0.5f, 0.5f);
			GL11.glVertex2f((x2 + x1) * 0.5f, (y2 + y1) * 0.5f);
			
			// start
			GL11.glColor4ub((byte)color.getRed(), (byte)color.getGreen(), (byte)color.getBlue(), (byte)((float)color.getAlpha() * edge1));
			GL11.glTexCoord2f(0, 0);
			GL11.glVertex2f(x1 - v1.x, y1 - v1.y);
			GL11.glTexCoord2f(0, 1);
			GL11.glVertex2f(x1 + v1.x, y1 + v1.y);
			
			// end
			GL11.glColor4ub((byte)color.getRed(), (byte)color.getGreen(), (byte)color.getBlue(), (byte)((float)color.getAlpha() * edge2));
			GL11.glTexCoord2f(1, 1);
			GL11.glVertex2f(x2 + v2.x, y2 + v2.y);
			GL11.glTexCoord2f(1, 0);
			GL11.glVertex2f(x2 - v2.x, y2 - v2.y);
			
			// wrap back around to start
			GL11.glColor4ub((byte)color.getRed(), (byte)color.getGreen(), (byte)color.getBlue(), (byte)((float)color.getAlpha() * edge1));
			GL11.glTexCoord2f(0, 0);
			GL11.glVertex2f(x1 - v1.x, y1 - v1.y);
		}
		GL11.glEnd();
		GL11.glPopMatrix();
	}
}
