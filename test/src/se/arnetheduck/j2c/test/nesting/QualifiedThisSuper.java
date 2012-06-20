package se.arnetheduck.j2c.test.nesting;

public class QualifiedThisSuper {
	public static class S {
		int n;

		int m() {
			int ret = n;
			ret += n;
			ret += n;
			ret += S.this.n;
			return ret;
		}

		public class Inner {
			int n;

			int m() {
				int ret = n;
				ret += n;
				ret += S.this.n;
				ret += S.this.m();
				return ret;
			}
		}
	}

	public static class T extends S {
		int n;

		@Override
		int m() {
			int ret = n;
			ret += n;
			ret += T.this.n;
			ret += super.n;
			ret += T.super.n;
			ret += super.m();
			ret += T.super.m();
			return ret;
		}

		public class Inner extends S.Inner {
			int n;

			@Override
			int m() {
				int ret = n;
				ret += n;
				ret += T.this.n;
				ret += super.n;
				ret += T.super.n;
				ret += super.m();
				ret += T.this.m();
				ret += T.super.m();
				return ret;
			}
		}
	}
}
