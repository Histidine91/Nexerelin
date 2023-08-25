package exerelin.utilities;

import java.util.Random;

public class NexUtilsMath {
	/**
	 * Like Random.nextInt() except easier to read
	 * @param rand
	 * @param max
	 * @return
	 */
	public static int randomNextIntInclusive(Random rand, int max)
	{
		return rand.nextInt(max + 1);
	}

	/**
	 * Rounds up or down with closer integer having a proportionally higher chance
	 * @param number
	 * @return
	 */
	public static int getRandomNearestInteger(float number)
	{
		if (number >= 0) {
			return (int)(number + Math.random());
		} else {
			return (int)(number - Math.random());
		}
	}
	
	public static float round(float num)
	{
		return (float)round((double)num);
	}
	
	public static double round(double num)
	{
		return Math.floor(num + 0.5f);
	}
	
	/**
	 * Linear interpolation between x and y.
	 * @param x
	 * @param y
	 * @param alpha How "far along" the line between x and y the value is.
	 * @return
	 */
	public static float lerp(float x, float y, float alpha) {
		return (1f - alpha) * x + alpha * y;
	}

	/**
	 * e.g. Value of 1.5 with bonusMult of 2 becomes 2
	 * @param valueWithBonus
	 * @param bonusMult
	 * @return
	 */
	public static float multiplyBonus(float valueWithBonus, float bonusMult) {
		valueWithBonus--;
		valueWithBonus *= bonusMult;
		valueWithBonus++;
		return valueWithBonus;
	}
}
