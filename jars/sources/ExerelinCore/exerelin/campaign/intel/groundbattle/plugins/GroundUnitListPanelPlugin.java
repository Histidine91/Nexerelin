package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.GroundUnitPanelPlugin;
import exerelin.campaign.intel.groundbattle.dialog.UnitOrderDialogPlugin;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.campaign.ui.CustomUIPanelInputListener;
import exerelin.utilities.NexUtilsGUI;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.lwjgl.input.Mouse;

import java.util.LinkedHashMap;
import java.util.Map;

@Log4j
public class GroundUnitListPanelPlugin extends CustomPanelPluginWithInput implements CustomUIPanelInputListener {

	protected transient GroundUnit liftedUnit;
	@Getter	protected transient Map<GroundUnit, CustomPanelAPI> unitPanels = new LinkedHashMap<>();
	@Getter @Setter	protected transient CustomPanelAPI newUnitPanel;

	@Override
	public void render(float alphaMult) {
		super.render(alphaMult);

		if (liftedUnit != null && liftedUnit.getBackgroundIcon() != null) {
			//drawLiftedUnit(alphaMult);    // MarketMapDrawer will already do this (even outside the map)
		}
	}

	protected void drawLiftedUnit(float alphaMult) {
		NexUtilsGUI.drawSpriteAtPoint(liftedUnit.getBackgroundIcon(), Mouse.getX(), Mouse.getY(), alphaMult * 0.5f, 0.5f);
	}

	@Override
	public void notifyInput(CustomUIPanelPlugin plugin, InputEventAPI input, Map<String, Object> params) {
		//log.info(params);
		if (params.get("source") instanceof GroundUnitPanelPlugin gupp) {
			if (input.isLMBDownEvent()) {
				liftedUnit = gupp.getUnit();
				if (!liftedUnit.canMove()) liftedUnit = null;
			}
			else if (input.isLMBUpEvent()) {
				if (newUnitPanel != null && newUnitPanel.getPosition().containsEvent(input)) {
					processUnitSplit(liftedUnit);
				}
				else {
					GroundUnit droppedAt = getGroundUnitDroppedAt(input);
					if (droppedAt != null) {
						processUnitMerge(liftedUnit, droppedAt);
					}
				}
				liftedUnit = null;
			}
		}
	}

	public GroundUnit getGroundUnitDroppedAt(InputEventAPI input) {
		for (GroundUnit unit : unitPanels.keySet()) {
			if (unitPanels.get(unit).getPosition().containsEvent(input)) {
				return unit;
			}
		}
		return null;
	}

	protected void processUnitMerge(GroundUnit from, GroundUnit to) {
		//log.info(String.format("Merging %s into %s", from, to));
		boolean canMergeOrTransfer = UnitOrderDialogPlugin.canConsiderForMerge(from, to);
		if (!canMergeOrTransfer) {
			UnitOrderDialogPlugin.playSound("disabled");
			return;
		}
		int maxMove = UnitOrderDialogPlugin.getMaxTransferable(from, to);
		if (maxMove >= from.getSize()) {
			UnitOrderDialogPlugin.mergeUnit(from, to);
			UnitOrderDialogPlugin.playSound("deploy");
		} else if (maxMove <= 0) {
			UnitOrderDialogPlugin.playSound("disabled");
			return;
		} else {
			UnitOrderDialogPlugin.transfer(from, to, -maxMove);
			UnitOrderDialogPlugin.playSound("deploy");
		}
		from.getIntel().getLastUIAPI().updateUIForItem(from.getIntel());
	}

	protected void processUnitSplit(GroundUnit unit) {
		//log.info(String.format("Splitting %s into new unit", unit));
		GroundBattleIntel intel = unit.getIntel();
		boolean canSplit = (unit.getSize() >= intel.getUnitSize().getMinSizeForType(unit.getUnitDefId()) * 2) && (intel.getPlayerData().getUnits().size() < GroundBattleIntel.MAX_PLAYER_UNITS);
		if (!canSplit) {
			UnitOrderDialogPlugin.playSound("disabled");
			return;
		}
		UnitOrderDialogPlugin.split(unit);
		UnitOrderDialogPlugin.playSound("deploy");
		unit.getIntel().getLastUIAPI().updateUIForItem(unit.getIntel());
	}
}
