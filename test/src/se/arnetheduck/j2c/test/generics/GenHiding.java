package se.arnetheduck.j2c.test.generics;


public class GenHiding {
	public interface Collection<E> {
		boolean addAll(Collection<? extends E> c);

	}

	public static abstract class AbstractCollection<E> implements Collection<E> {
		@Override
		public boolean addAll(Collection<? extends E> c) {
			return true;
		}
	}

	public interface List<E> extends Collection<E> {
		@Override
		boolean addAll(Collection<? extends E> c);

		boolean addAll(int index, Collection<? extends E> c);
	}

	static public abstract class AbstractList<E> extends AbstractCollection<E>
			implements List<E> {
		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return true;
		}
	}

	public static abstract class AbstractSequentialList<E> extends
			AbstractList<E> {
		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return true;
		}
	}
}