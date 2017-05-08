/*******************************************************************************
 *
 *    Copyright (C) 2015-2017 the BBoxDB project
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
package org.bboxdb.storage;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bboxdb.distribution.DistributionGroupCache;
import org.bboxdb.distribution.DistributionGroupMetadataHelper;
import org.bboxdb.distribution.DistributionGroupName;
import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.DistributionRegionHelper;
import org.bboxdb.distribution.OutdatedDistributionRegion;
import org.bboxdb.distribution.membership.DistributedInstance;
import org.bboxdb.distribution.membership.MembershipConnectionService;
import org.bboxdb.distribution.membership.event.DistributedInstanceState;
import org.bboxdb.distribution.mode.DistributionGroupZookeeperAdapter;
import org.bboxdb.distribution.mode.KDtreeZookeeperAdapter;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.misc.BBoxDBConfiguration;
import org.bboxdb.misc.BBoxDBConfigurationManager;
import org.bboxdb.misc.BBoxDBService;
import org.bboxdb.misc.Const;
import org.bboxdb.network.client.BBoxDBClient;
import org.bboxdb.network.client.future.TupleListFuture;
import org.bboxdb.network.server.NetworkConnectionService;
import org.bboxdb.storage.entity.DistributionGroupMetadata;
import org.bboxdb.storage.entity.SSTableName;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.sstable.SSTableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoveryService implements BBoxDBService {
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(RecoveryService.class);
	
	/**
	 * The connection handler
	 */
	protected final NetworkConnectionService connectionHandler;

	public RecoveryService(final NetworkConnectionService connectionHandler) {
		this.connectionHandler = connectionHandler;
	}

	@Override
	public void init() {
		try {
			final ZookeeperClient zookeeperClient = ZookeeperClientFactory.getZookeeperClientAndInit();
			zookeeperClient.setLocalInstanceState(DistributedInstanceState.OUTDATED);
			logger.info("Running recovery for local stored data");
			
			runRecovery();
			
			logger.info("Running recovery for local stored data DONE");

			zookeeperClient.setLocalInstanceState(DistributedInstanceState.READY);
		} catch (ZookeeperException | ZookeeperNotFoundException e) {
			logger.error("Got an exception during recovery: ", e);
		}
	}

	/**
	 * Run the recovery
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	protected void runRecovery() throws ZookeeperException, ZookeeperNotFoundException {
		
		final DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter 
			= ZookeeperClientFactory.getDistributionGroupAdapter();
		
		final List<DistributionGroupName> distributionGroups 
			= distributionGroupZookeeperAdapter.getDistributionGroups();
		
		for(final DistributionGroupName distributionGroupName : distributionGroups) {
			logger.info("Recovery: running recovery for distribution group: {}", distributionGroupName);
			runRecoveryForDistributionGroup(distributionGroupName);
			logger.info("Recovery: recovery for distribution group done: {}", distributionGroupName);
		}
	}

	/**
	 * Run recovery for distribution group
	 * @param distributionGroupName
	 * @throws ZookeeperException 
	 */
	protected void runRecoveryForDistributionGroup(final DistributionGroupName distributionGroupName) {
		try {
			final ZookeeperClient zookeeperClient = ZookeeperClientFactory.getZookeeperClientAndInit();
			final BBoxDBConfiguration configuration = BBoxDBConfigurationManager.getConfiguration();
			final DistributedInstance localInstance = ZookeeperClientFactory.getLocalInstanceName(configuration);
			
			checkGroupVersion(distributionGroupName, zookeeperClient);
					
			final KDtreeZookeeperAdapter distributionAdapter = DistributionGroupCache.getGroupForGroupName(
					distributionGroupName.getFullname(), zookeeperClient);
			
			final DistributionRegion distributionGroup = distributionAdapter.getRootNode();
		
			final List<OutdatedDistributionRegion> outdatedRegions = DistributionRegionHelper.getOutdatedRegions(distributionGroup, localInstance);
			handleOutdatedRegions(distributionGroupName, outdatedRegions);
		} catch (ZookeeperException e) {
			logger.error("Got exception while running recovery for distribution group: " + distributionGroupName, e);
		}
		
	}

	protected void checkGroupVersion(final DistributionGroupName distributionGroupName,
			final ZookeeperClient zookeeperClient) {
		
		try {
			final DistributionGroupMetadata metaData = DistributionGroupMetadataHelper.getMedatadaForGroup(distributionGroupName);
			final DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter = new DistributionGroupZookeeperAdapter(zookeeperClient);
			final String remoteVersion = distributionGroupZookeeperAdapter.getVersionForDistributionGroup(distributionGroupName.getFullname(), null);
			
			if(metaData == null) {
				logger.debug("Metadata is null, skipping check");
				return;
			}
			
			final String localVersion = metaData.getVersion();
			
			if(! remoteVersion.equals(localVersion)) {
				logger.error("Local version {} of dgroup {} does not match remtote version {}", localVersion, distributionGroupName, remoteVersion);
				System.exit(-1);
			}
			
		} catch (ZookeeperException | ZookeeperNotFoundException e) {
			logger.error("Got an exception while checking group version");
		} 
	}

	/**
	 * Handle the outdated distribution regions
	 * @param distributionGroupName
	 * @param outdatedRegions
	 */
	protected void handleOutdatedRegions(final DistributionGroupName distributionGroupName, 
			final List<OutdatedDistributionRegion> outdatedRegions) {
		
		for(final OutdatedDistributionRegion outdatedDistributionRegion : outdatedRegions) {
			
			final BBoxDBClient connection = MembershipConnectionService.getInstance()
					.getConnectionForInstance(outdatedDistributionRegion.getNewestInstance());
			
			final int regionId = outdatedDistributionRegion.getDistributedRegion().getRegionId();
			
			final List<SSTableName> allTables = StorageRegistry.getInstance()
					.getAllTablesForDistributionGroupAndRegionId(distributionGroupName, regionId);
			
			for(final SSTableName ssTableName : allTables) {
				try {
					runRecoveryForTable(ssTableName, outdatedDistributionRegion, connection);
				} catch (StorageManagerException | ExecutionException e) {
					logger.error("Got an exception while performing recovery for table: " + ssTableName.getFullname());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.error("Got an exception while performing recovery for table: " + ssTableName.getFullname());
				}
			}
		}
	}

	/**
	 * Run the recovery for a given table
	 * @param ssTableName
	 * @param outdatedDistributionRegion
	 * @param connection
	 * @throws StorageManagerException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	protected void runRecoveryForTable(final SSTableName ssTableName,
			final OutdatedDistributionRegion outdatedDistributionRegion,
			final BBoxDBClient connection) throws StorageManagerException,
			InterruptedException, ExecutionException {
		
		final String sstableName = ssTableName.getFullname();
		
		logger.info("Recovery: starting recovery for table {}", sstableName);
		final SSTableManager tableManager = StorageRegistry.getInstance().getSSTableManager(ssTableName);
		
		// Even with NTP, the clock of the nodes can have a delta.
		// We substract this delta from the checkpoint timestamp to ensure
		// that all tuples for the recovery are requested
		final long requestTupleTimestamp = outdatedDistributionRegion.getLocalVersion() 
				- Const.MAX_NODE_CLOCK_DELTA;
		
		final TupleListFuture result = connection.queryInsertedTime
				(sstableName, requestTupleTimestamp);
		
		result.waitForAll();
		
		if(result.isFailed()) {
			logger.warn("Recovery: Failed result for table {} - Some tuples could not be received!", 
					sstableName);
			return;
		}
		
		long insertedTuples = 0;
		for(final Tuple tuple : result) {
			tableManager.put(tuple);
			insertedTuples++;
		}
		
		logger.info("Recovery: successfully inserted {} tuples into table {}", insertedTuples,
				sstableName);
	}

	@Override
	public void shutdown() {
		// Nothing to do
	}

	@Override
	public String getServicename() {
		return "Recovery service";
	}

}
