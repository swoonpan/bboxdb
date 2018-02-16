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
package org.bboxdb.storage.sstable.compact;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bboxdb.commons.RejectedException;
import org.bboxdb.commons.concurrent.ExceptionSafeThread;
import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.DistributionRegionHelper;
import org.bboxdb.distribution.partitioner.SpacePartitioner;
import org.bboxdb.distribution.partitioner.SpacePartitionerCache;
import org.bboxdb.distribution.partitioner.regionsplit.RegionSplitHelper;
import org.bboxdb.distribution.partitioner.regionsplit.RegionSplitter;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.network.client.BBoxDBException;
import org.bboxdb.storage.StorageManagerException;
import org.bboxdb.storage.entity.TupleStoreName;
import org.bboxdb.storage.sstable.SSTableWriter;
import org.bboxdb.storage.sstable.reader.SSTableFacade;
import org.bboxdb.storage.sstable.reader.SSTableKeyIndexReader;
import org.bboxdb.storage.tuplestore.DiskStorage;
import org.bboxdb.storage.tuplestore.manager.TupleStoreManager;
import org.bboxdb.storage.tuplestore.manager.TupleStoreManagerRegistry;
import org.bboxdb.storage.tuplestore.manager.TupleStoreManagerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSTableCompactorThread extends ExceptionSafeThread {
	
	/**
	 * The merge strategy
	 */
	protected final MergeStrategy mergeStragegy;

	/**
	 * The storage
	 */
	protected DiskStorage storage;
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SSTableCompactorThread.class);

	public SSTableCompactorThread(final DiskStorage storage) {
		this.storage = storage;
		this.mergeStragegy = new SimpleMergeStrategy();
	}

	/**
	 * Execute the compactor thread
	 */
	protected void runThread() {
		
		logger.info("Compact thread has started");
			
		while(! Thread.currentThread().isInterrupted()) {
			try {	
				Thread.sleep(mergeStragegy.getCompactorDelay());
				logger.debug("Executing compact thread");
				execute(); 
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} 
		}
		
		logger.info("Compact thread is DONE");
	}

	/**
	 * Execute a new compactation
	 * @throws InterruptedException 
	 */
	public synchronized void execute() throws InterruptedException {
		
		final TupleStoreManagerRegistry storageRegistry = storage.getTupleStoreManagerRegistry();
		final String location = storage.getBasedir().getAbsolutePath();
		final List<TupleStoreName> tupleStores = storageRegistry.getTupleStoresForLocation(location);
		
		if(tupleStores.isEmpty()) {
			logger.debug("Skipping run, tuple stores list is empty");
			return;
		}
				
		for(final TupleStoreName tupleStoreName: tupleStores) {
		
			try {
				logger.debug("Running compact for: {}", tupleStoreName);
				final TupleStoreManager tupleStoreManager = storageRegistry.getTupleStoreManager(tupleStoreName);
				
				if(tupleStoreManager.getSstableManagerState() == TupleStoreManagerState.READ_ONLY) {
					logger.debug("Skipping compact for read only sstable manager: {}" , tupleStoreName);
					continue;
				}
								
				// Create a copy to ensure, that the list of facades don't change
				// during the compact run.
				final List<SSTableFacade> facades = new ArrayList<>();
				facades.addAll(tupleStoreManager.getSstableFacades());
				
				final MergeTask mergeTask = mergeStragegy.getMergeTask(facades);
				mergeTupleStores(mergeTask, tupleStoreManager);
			} catch (StorageManagerException | BBoxDBException e) {
				logger.error("Error while merging tables", e);	
			} 
		}
	}

	/**
	 * Merge multiple facades into a new one
	 * @param sstableManager 
	 *
	 * @throws StorageManagerException
	 * @throws InterruptedException 
	 * @throws ZookeeperException 
	 * @throws BBoxDBException 
	 */
	protected void mergeTupleStores(final MergeTask mergeTask, final TupleStoreManager sstableManager) 
			throws StorageManagerException, BBoxDBException, InterruptedException {
		
		if(mergeTask.getTaskType() == MergeTaskType.UNKNOWN) {
			return;
		}
		
		final List<SSTableFacade> facades = mergeTask.getCompactTables();
		final boolean majorCompaction = mergeTask.getTaskType() == MergeTaskType.MAJOR;
	
		if(facades == null || facades.isEmpty()) {
			return;
		}
		
		final List<SSTableKeyIndexReader> reader = mergeTask.getCompactTables()
				.stream()
				.map(f -> f.getSsTableKeyIndexReader())
				.collect(Collectors.toList());
		
		// Log the compact call
		if(logger.isInfoEnabled()) {
			writeMergeLog(facades, majorCompaction);
		}
		
		// Run the compact process
		final SSTableCompactor ssTableCompactor = new SSTableCompactor(sstableManager, reader);
		ssTableCompactor.setMajorCompaction(majorCompaction);
		ssTableCompactor.executeCompactation();
		final List<SSTableWriter> newTables = ssTableCompactor.getResultList();

		final float mergeFactor = (float) ssTableCompactor.getWrittenTuples() / (float) ssTableCompactor.getReadTuples();
		
		logger.info("Compactation done. Read {} tuples, wrote {} tuples. Factor {}", 
				ssTableCompactor.getReadTuples(), ssTableCompactor.getWrittenTuples(), 
				mergeFactor);
		
		registerNewFacadeAndDeleteOldInstances(sstableManager, facades, newTables);
		
		if(sstableManager.getTupleStoreName().isDistributedTable()) {
			// Read only = table is in splitting mode
			if(sstableManager.getSstableManagerState() == TupleStoreManagerState.READ_WRITE) {
				splitOrMergeRegion(sstableManager);
			}
		}
	}

	/**
	 * Split or merge the given region
	 * @param totalSizeInMb
	 * @param spacePartitioner
	 * @param regionToSplit
	 * @throws BBoxDBException
	 * @throws ZookeeperException 
	 * @throws InterruptedException 
	 */
	private void splitOrMergeRegion(final TupleStoreManager sstableManager) 
			throws BBoxDBException, InterruptedException {
		
		try {
			final TupleStoreName ssTableName = sstableManager.getTupleStoreName();
			
			final long regionId = ssTableName.getRegionId();
			
			final SpacePartitioner spacePartitioner = SpacePartitionerCache
					.getSpacePartitionerForGroupName(ssTableName.getDistributionGroup());
			
			final DistributionRegion distributionRegion = spacePartitioner.getRootNode();

			final DistributionRegion regionToSplit = DistributionRegionHelper.getDistributionRegionForNamePrefix(
					distributionRegion, regionId);
			
			final RegionSplitHelper regionSplitHelper = new RegionSplitHelper();
			final RegionSplitter regionSplitter = new RegionSplitter(storage.getTupleStoreManagerRegistry());

			if(regionSplitHelper.isRegionOverflow(regionToSplit)) {
				regionSplitter.splitRegion(regionToSplit, spacePartitioner, 
						storage.getTupleStoreManagerRegistry());
			} 
			
			// Don't split the root node
			if(regionToSplit.getParent() != DistributionRegion.ROOT_NODE_ROOT_POINTER) {
				if(regionSplitHelper.isRegionUnderflow(regionToSplit.getParent())) {
					regionSplitter.mergeRegion(regionToSplit.getParent(), spacePartitioner, 
							storage.getTupleStoreManagerRegistry());	
				}
			}
		} catch (ZookeeperException e) {
			throw new BBoxDBException(e);
		}
	}

	/**
	 * Register a new sstable facade and delete the old ones
	 * @param oldFacades
	 * @param directory
	 * @param name
	 * @param tablenumber
	 * @throws StorageManagerException
	 */
	protected void registerNewFacadeAndDeleteOldInstances(final TupleStoreManager sstableManager, 
			final List<SSTableFacade> oldFacades, 
			final List<SSTableWriter> newTableWriter) throws StorageManagerException {
		
		final List<SSTableFacade> newFacedes = new ArrayList<>();
		
		// Open new facedes
		for(final SSTableWriter writer : newTableWriter) {
			
			final int sstableKeyCacheEntries = storage.getTupleStoreManagerRegistry().getConfiguration()
					.getSstableKeyCacheEntries();
			
			final SSTableFacade newFacade = new SSTableFacade(writer.getDirectory(), 
					writer.getName(), writer.getTablenumber(), sstableKeyCacheEntries);
			newFacedes.add(newFacade);
		}
		
		// Manager has switched to read only
		if(sstableManager.getSstableManagerState() == TupleStoreManagerState.READ_ONLY) {
			logger.info("Manager is in read only mode, cancel compact run");
			handleCompactException(newFacedes);
			return;
		}
		
		try {
			for(final SSTableFacade facade : newFacedes) {
				facade.init();
			}
			
			// Switch facades in registry
			sstableManager.replaceCompactedSStables(newFacedes, oldFacades);

			// Schedule facades for deletion
			oldFacades.forEach(f -> f.deleteOnClose());
		} catch (BBoxDBException | RejectedException e) {
			handleCompactException(newFacedes);
			throw new StorageManagerException(e);
		} catch (InterruptedException e) {
			handleCompactException(newFacedes);
			Thread.currentThread().interrupt();
			throw new StorageManagerException(e);
		} 
	}
	
	/** 
	 * Handle exception in compact thread
	 * @param newFacedes
	 */
	protected void handleCompactException(final List<SSTableFacade> newFacedes) {
		logger.info("Exception, schedule delete for {} compacted tables", newFacedes.size());
		for(final SSTableFacade facade : newFacedes) {
			facade.deleteOnClose();
			assert (facade.getUsage().get() == 0) : "Usage counter is not 0 " + facade.getInternalName();
		}
	}

	/***
	 * Write info about the merge run into log
	 * @param facades
	 * @param tablenumber
	 */
	protected void writeMergeLog(final List<SSTableFacade> facades, final boolean majorCompaction) {
		
		final String formatedFacades = facades
				.stream()
				.mapToInt(SSTableFacade::getTablebumber)
				.mapToObj(Integer::toString)
				.collect(Collectors.joining(",", "[", "]"));
		
		logger.info("Merging (major: {}) {}", majorCompaction, formatedFacades);
	}

}
