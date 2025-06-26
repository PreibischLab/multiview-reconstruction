package net.preibisch.mvrecon.process.fusion.intensity;

/**
 * Compute the corrected intensity when {@code intensity} was observed in a
 * pixel that has been bleached by previously being imaged.
 */
@FunctionalInterface
public interface UnbleachFunction {

	/**
	 * Compute the corrected intensity when {@code intensity} was observed
	 * in a pixel that has been {@code nBleachIterations} times imaged
	 * already. (When this is the first time the pixel is imaged, {@code
	 * nBleachIterations==0}.
	 *
	 * @param intensity
	 * 		observed intensity
	 * @param nBleachIterations
	 * 		number of times the pixel was bleached
	 *
	 * @return corrected intensity
	 */
	double unbleach(double intensity, int nBleachIterations);
}
