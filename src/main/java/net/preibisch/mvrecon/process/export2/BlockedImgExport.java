package net.preibisch.mvrecon.process.export2;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public interface BlockedImgExport
{
	public <T extends RealType< T > & NativeType< T >> void export(
			final Supplier<Consumer<RandomAccessibleInterval<T>>> consumerSupplier,
			final Interval fusionInterval,
			final int[] blockSize );
}
