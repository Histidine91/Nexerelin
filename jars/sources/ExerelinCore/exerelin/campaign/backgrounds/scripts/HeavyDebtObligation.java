package exerelin.campaign.backgrounds.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.tutorial.SpacerObligation;

public class HeavyDebtObligation extends SpacerObligation {

    public static int DEBT_BASE = 10000;
    public static int DEBT_PER_LEVEL = 5000;

    public static int getCalculatedDebt() {
        return (int) ((DEBT_BASE + (Global.getSector().getPlayerStats().getLevel() - 1) * DEBT_PER_LEVEL) * Global.getSettings().getFloat("nex_spacerDebtMult"));
    }

    @Override
    protected int getDebt() {
        return getCalculatedDebt();
    }
}
