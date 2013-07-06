package navigators.smart.tom.util;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlackListTest extends TestCase {

	private BlackList testlist;

	private int servers = 10;
	private int f = 3;

	@Before
	public void setUp() throws Exception {
		testlist = new BlackList(servers, f);
	}

	@After
	public void tearDown() throws Exception {
		testlist = null;
	}

	@Test
	public void testContains() {
		for (int i = 0; i < servers; i++) {
			testlist.addFirst(i);
			assertTrue(testlist.contains(i));
			assertFalse(testlist.getCorrect().contains(i));
		}

	}

	@Test
	public void testAddFirst() {
		for (int i = 0; i < 2 * f; i++) {
			testlist.addFirst(i);
			assertTrue(testlist.contains(i));
			assertFalse(testlist.getCorrect().contains(i));
			if (i >= f) {
				assertFalse(testlist.contains(i - f));
				assertTrue(testlist.getCorrect().contains(i-f));
			}
		}
	}

	@Test
	public void testReplaceFirst() {
		for (int i = 0; i < 2 * f; i++) {
			testlist.replaceFirst(i);
			assertTrue(testlist.contains(i));
			assertFalse(testlist.getCorrect().contains(i));
			if(i>0){
				assertFalse(testlist.contains(i - 1));
				assertTrue(testlist.getCorrect().contains(i-1));
			}
		}
	}

}
