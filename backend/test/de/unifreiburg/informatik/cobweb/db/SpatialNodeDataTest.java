package de.unifreiburg.informatik.cobweb.db;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for the class {@link SpatialNodeData}.
 *
 * @author Daniel Tischner {@literal <zabuza.dev@gmail.com>}
 */
public final class SpatialNodeDataTest {
  /**
   * The spatial node data used for testing.
   */
  private SpatialNodeData mSpatialNodeData;

  /**
   * Setups a spatial node data instance for testing.
   */
  @Before
  public void setUp() {
    mSpatialNodeData = new SpatialNodeData(2, 1L, 10.0F, 5.0F);
  }

  /**
   * Test method for
   * {@link de.unifreiburg.informatik.cobweb.db.SpatialNodeData#getId()}.
   */
  @Test
  public final void testGetId() {
    Assert.assertEquals(2, mSpatialNodeData.getId());
  }

  /**
   * Test method for
   * {@link de.unifreiburg.informatik.cobweb.db.SpatialNodeData#getLatitude()}.
   */
  @Test
  public final void testGetLatitude() {
    Assert.assertEquals(10.0, mSpatialNodeData.getLatitude(), 0.0001);
  }

  /**
   * Test method for
   * {@link de.unifreiburg.informatik.cobweb.db.SpatialNodeData#getLongitude()}.
   */
  @Test
  public final void testGetLongitude() {
    Assert.assertEquals(5.0, mSpatialNodeData.getLongitude(), 0.0001);
  }

  /**
   * Test method for
   * {@link de.unifreiburg.informatik.cobweb.db.SpatialNodeData#getOsmId()}.
   */
  @Test
  public final void testGetOsmId() {
    Assert.assertEquals(1L, mSpatialNodeData.getOsmId());
  }

  /**
   * Test method for
   * {@link de.unifreiburg.informatik.cobweb.db.SpatialNodeData#SpatialNodeData(int, long, float, float)}.
   */
  @SuppressWarnings({ "unused", "static-method" })
  @Test
  public final void testSpatialNodeData() {
    try {
      new SpatialNodeData(2, 1L, 10.0F, 5.0F);
      new SpatialNodeData(-2, -10L, -10.0F, -5.0F);
      new SpatialNodeData(0, 0L, 0.0F, 0.0F);
    } catch (final Exception e) {
      Assert.fail();
    }
  }

}
