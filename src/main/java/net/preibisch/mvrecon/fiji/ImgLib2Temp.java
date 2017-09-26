package net.preibisch.mvrecon.fiji;


public class ImgLib2Temp
{
	public interface Pair< A, B >
	{
		public A getA();

		public B getB();
	}

	public static class ValuePair< A, B > implements Pair< A, B >
	{
		final public A a;

		final public B b;

		public ValuePair( final A a, final B b )
		{
			this.a = a;
			this.b = b;
		}

		@Override
		public A getA()
		{
			return a;
		}

		@Override
		public B getB()
		{
			return b;
		}
	}

	public interface Triple< A, B, C >
	{
		public A getA();

		public B getB();

		public C getC();
	}

	public static class ValueTriple< A, B, C > implements Triple< A, B, C >
	{
		final public A a;

		final public B b;

		final public C c;

		public ValueTriple( final A a, final B b, final C c )
		{
			this.a = a;
			this.b = b;
			this.c = c;
		}

		@Override
		public A getA()
		{
			return a;
		}

		@Override
		public B getB()
		{
			return b;
		}

		@Override
		public C getC()
		{
			return c;
		}
}
}
