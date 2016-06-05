package de.fernunihagen.dna.jkn.scalephant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.fernunihagen.dna.jkn.scalephant.distribution.DistributionGroupName;
import de.fernunihagen.dna.jkn.scalephant.distribution.DistributionRegion;
import de.fernunihagen.dna.jkn.scalephant.distribution.DistributionRegionFactory;
import de.fernunihagen.dna.jkn.scalephant.distribution.DistributionRegionWithZookeeperIntegration;
import de.fernunihagen.dna.jkn.scalephant.distribution.ZookeeperClient;
import de.fernunihagen.dna.jkn.scalephant.distribution.ZookeeperException;

public class TestZookeeperIntegration {

	/**
	 * The zookeeper client
	 */
	protected ZookeeperClient zookeeperClient;
	
	/**
	 * The name of the test region
	 */
	protected static final String TEST_GROUP = "4_abc";
	
	@Before
	public void before() {
		final ScalephantConfiguration scalephantConfiguration 
			= ScalephantConfigurationManager.getConfiguration();
	
		final Collection<String> zookeepernodes = scalephantConfiguration.getZookeepernodes();
		final String clustername = scalephantConfiguration.getClustername();

		System.out.println("Zookeeper nodes are: " + zookeepernodes);
		System.out.println("Zookeeper cluster is: " + clustername);
	
		zookeeperClient = new ZookeeperClient(zookeepernodes, clustername);
		zookeeperClient.init();
	}
	
	@After
	public void after() {
		zookeeperClient.shutdown();
	}

	/**
	 * Test the id generation
	 * @throws ZookeeperException
	 */
	@Test
	public void testTableIdGenerator() throws ZookeeperException {
		final List<Integer> ids = new ArrayList<Integer>();
		
		for(int i = 0; i < 10; i++) {
			int nextId = zookeeperClient.getNextTableIdForDistributionGroup("mygroup1");
			System.out.println("The next id is: " + nextId);
			
			Assert.assertFalse(ids.contains(nextId));
			ids.add(nextId);
		}
	}
	
	/**
	 * Test the creation and the deletion of a distributon group
	 * @throws ZookeeperException
	 */
	@Test
	public void testDistributionGroupCreateDelete() throws ZookeeperException {
		
		// Create new group
		zookeeperClient.deleteDistributionGroup(TEST_GROUP);
		zookeeperClient.createDistributionGroup(TEST_GROUP, (short) 3); 
		final List<DistributionGroupName> groups = zookeeperClient.getDistributionGroups();
		System.out.println(groups);
		boolean found = false;
		for(final DistributionGroupName group : groups) {
			if(group.getFullname().equals(TEST_GROUP)) {
				found = true;
			}
		}
		
		Assert.assertTrue(found);
		
		// Delete group
		zookeeperClient.deleteDistributionGroup(TEST_GROUP);
		final List<DistributionGroupName> groups2 = zookeeperClient.getDistributionGroups();
		found = false;
		for(final DistributionGroupName group : groups2) {
			if(group.getFullname().equals(TEST_GROUP)) {
				found = true;
			}
		}
		
		Assert.assertFalse(found);
	}
	
	/**
	 * Test the replication factor of a distribution group
	 * @throws ZookeeperException 
	 */
	@Test
	public void testDistributionGroupReplicationFactor() throws ZookeeperException {
		zookeeperClient.deleteDistributionGroup(TEST_GROUP);
		zookeeperClient.createDistributionGroup(TEST_GROUP, (short) 3); 
		Assert.assertEquals(3, zookeeperClient.getReplicationFactorForDistributionGroup(TEST_GROUP));
	}
	
	/**
	 * Test the split of a distribution region
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testDistributionRegionSplit() throws ZookeeperException, InterruptedException {
		zookeeperClient.deleteDistributionGroup(TEST_GROUP);
		zookeeperClient.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		// Split and update
		final DistributionRegion distributionGroup = zookeeperClient.readDistributionGroup(TEST_GROUP);
		Assert.assertEquals(TEST_GROUP, distributionGroup.getName());
		distributionGroup.setSplit(10);
		Assert.assertEquals(10.0, distributionGroup.getSplit(), 0.0001);

		// Reread group from zookeeper
		final DistributionRegion newDistributionGroup = zookeeperClient.readDistributionGroup(TEST_GROUP);
		Assert.assertEquals(10.0, newDistributionGroup.getSplit(), 0.0001);
	}
	
	/**
	 * Test the distribution of changes in the zookeeper structure (reading data from the second object)
	 * @throws ZookeeperException
	 * @throws InterruptedException
	 */
	@Ignore
	@Test
	public void testDistributionRegionSplitWithZookeeperPropergate() throws ZookeeperException, InterruptedException {
		zookeeperClient.deleteDistributionGroup(TEST_GROUP);
		zookeeperClient.createDistributionGroup(TEST_GROUP, (short) 3); 
		
		final DistributionRegion distributionGroup1 = zookeeperClient.readDistributionGroup(TEST_GROUP);
		final DistributionRegion distributionGroup2 = zookeeperClient.readDistributionGroup(TEST_GROUP);

		// Update object 1
		distributionGroup1.setSplit(10);
		
		// Sleep 2 seconds to wait for the update
		Thread.sleep(2000);

		// Read update from the second object
		Assert.assertEquals(10.0, distributionGroup2.getSplit(), 0.0001);
	}
	
	/**
	 * Test the distribution group factory
	 */
	@Test
	public void testFactory() {
		DistributionRegionFactory.setZookeeperClient(null);
		final DistributionRegion region1 = DistributionRegionFactory.createRootRegion(TEST_GROUP);
		Assert.assertTrue(region1 instanceof DistributionRegion);
		Assert.assertFalse(region1 instanceof DistributionRegionWithZookeeperIntegration);

		DistributionRegionFactory.setZookeeperClient(zookeeperClient);
		final DistributionRegion region2 = DistributionRegionFactory.createRootRegion(TEST_GROUP);
		Assert.assertTrue(region2 instanceof DistributionRegionWithZookeeperIntegration);
	}

}
