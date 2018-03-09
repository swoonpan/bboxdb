/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.distribution;

import java.util.HashSet;

import org.bboxdb.distribution.partitioner.DistributionGroupZookeeperAdapter;
import org.bboxdb.distribution.partitioner.KDtreeSpacePartitioner;
import org.bboxdb.distribution.region.DistributionRegion;
import org.bboxdb.distribution.region.DistributionRegionIdMapper;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.storage.entity.DistributionGroupConfiguration;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestKDtreeSpacePartitioner {
	
	/**
	 * The name of the test region
	 */
	private static final String TEST_GROUP = "abc";
	
	
	/**
	 * The distribution group adapter
	 */
	private static DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter;
	
	@BeforeClass
	public static void before() {
		distributionGroupZookeeperAdapter = ZookeeperClientFactory.getDistributionGroupAdapter();
	}

	@Test
	public void testDimensionsOfRootNode() throws ZookeeperException, InterruptedException, ZookeeperNotFoundException, BBoxDBException {
		
		for(int i = 1; i < 2 ; i++) {
			distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
			distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, new DistributionGroupConfiguration(i)); 
			
			final KDtreeSpacePartitioner spacepartitionier = (KDtreeSpacePartitioner) 
					distributionGroupZookeeperAdapter.getSpaceparitioner(TEST_GROUP, 
							new HashSet<>(), new DistributionRegionIdMapper());
			
			final DistributionRegion rootNode = spacepartitionier.getRootNode();
			
			Assert.assertEquals(i, rootNode.getConveringBox().getDimension());
		}

	}
}