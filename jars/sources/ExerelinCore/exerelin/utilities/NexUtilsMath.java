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
		
	public static float lerp(float x, float y, float alpha) {
		return (1f - alpha) * x + alpha * y;
	}
}
