package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class GroundBattleLog {
	
	public static final float ROW_HEIGHT = 24;
	public static final float TURN_NUM_WIDTH = 24;
	
	public static final String TYPE_UNIT_LOSSES = "unitLosses";
	public static final String TYPE_UNIT_DESTROYED = "unitDestroyed";
	public static final String TYPE_INDUSTRY_CAPTURED = "industryCaptured";
	
	public final GroundBattleIntel intel;
	public final int turn;
	public final String type;
	public Map<String, Object> params = new HashMap<>();
	
	public GroundBattleLog(GroundBattleIntel intel, String type, int turn) {
		this.intel = intel;
		this.turn = turn;
		this.type = type;
	}
	
	public void writeLog(TooltipMakerAPI tooltip) {
		String str, side, locStr;
		GroundUnit unit = (GroundUnit)params.get("unit");
		IndustryForBattle loc;
		Color h = Misc.getHighlightColor();
		Color pc = getPanelColor();
		LabelAPI label;
		
		switch (type) {
			case TYPE_UNIT_LOSSES:
				str = getString("log_unitLosses");
				str = StringHelper.substituteToken(str, "$unitType", unit.type.getName());
				loc = (IndustryForBattle)params.get("location");
				label = tooltip.addPara(str, 0, h, unit.name, 
						loc != null ? loc.ind.getCurrentName() : "<unknown location>",
						(int)params.get("losses") + "", 
						String.format("%.0f", -(float)params.get("morale") * 100) + "%");
				label.setHighlightColors(unit.faction.getBaseUIColor(), h, h, h);
				break;
			
			case TYPE_UNIT_DESTROYED:
				str = getString("log_unitDestroyed");
				str = StringHelper.substituteToken(str, "$unitType", unit.type.getName());
				side = Misc.ucFirst(StringHelper.getString(unit.isAttacker ? "attacker" : "defender"));
				loc = (IndustryForBattle)params.get("location");
				locStr = loc != null ? loc.ind.getCurrentName() : "<unknown location>";
				label = tooltip.addPara(str, 0, h, side, unit.name, locStr);
				//label.setHighlight(side, unit.name, locStr);
				//Global.getLogger(this.getClass()).info("Unit faction: " + unit.faction);
				label.setHighlightColors(pc, unit.faction.getBaseUIColor(), h);
				break;
				
			case TYPE_INDUSTRY_CAPTURED:
				Float morale = (float)params.get("morale");
				boolean heldByAttacker = (boolean)params.get("heldByAttacker");
				side = StringHelper.getString(heldByAttacker ? "attacker" : "defender");
				loc = (IndustryForBattle)params.get("industry");
				locStr = loc != null ? loc.ind.getCurrentName() : "<unknown location>";
				
				if (morale == null) {
					str = getString("log_industryCeded");
					label = tooltip.addPara(str, 0, h, locStr, side);
					label.setHighlight(locStr, side);
					label.setHighlightColors(h, pc);
				} else {
					str = getString("log_industryCaptured");
					String moraleStr = String.format("%.0f", morale * 100) + "%";
					label = tooltip.addPara(str, 0, h, locStr, side, moraleStr);
					label.setHighlight(locStr, side, moraleStr);
					label.setHighlightColors(h, pc, pc);
				}				
				
				break;
		}
	}
	
	public void writeLog(CustomPanelAPI outer, TooltipMakerAPI scroll, float width) {
		CustomPanelAPI panel = outer.createCustomPanel(width - 6, ROW_HEIGHT, 
				new FramedCustomPanelPlugin(0.25f, getPanelColor(), true));
		
		TooltipMakerAPI turnNumHolder = panel.createUIElement(TURN_NUM_WIDTH, ROW_HEIGHT, false);
		turnNumHolder.setParaSmallInsignia();
		turnNumHolder.addPara(turn + "", 0);
		panel.addUIElement(turnNumHolder).inTL(2, 2);
		
		TooltipMakerAPI text = panel.createUIElement(width - TURN_NUM_WIDTH - 6, ROW_HEIGHT, false);
		writeLog(text);
		panel.addUIElement(text).rightOfTop(turnNumHolder, 2);
		
		scroll.addCustom(panel, 3);
	}
	
	public Color getPanelColor() {
		GroundUnit unit = (GroundUnit)params.get("unit");
		IndustryForBattle ind = (IndustryForBattle)params.get("industry");
		switch (type) {
			case TYPE_UNIT_LOSSES:
				return Misc.getBallisticMountColor();
			case TYPE_UNIT_DESTROYED:
				return intel.getHighlightColorForSide(!unit.isAttacker);
			case TYPE_INDUSTRY_CAPTURED:
				return intel.getHighlightColorForSide((boolean)params.get("heldByAttacker"));
			default:
				return Misc.getBasePlayerColor();
		}
	}
}
