package de.fernunihagen.dna.scalephant.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.scalephant.network.client.OperationFuture;
import de.fernunihagen.dna.scalephant.network.client.ScalephantCluster;
import de.fernunihagen.dna.scalephant.network.client.ScalephantException;
import de.fernunihagen.dna.scalephant.storage.entity.BoundingBox;
import de.fernunihagen.dna.scalephant.storage.entity.Tuple;

public class Selftest {

	/**
	 * The name of the distribution group
	 */
	private static final String DISTRIBUTION_GROUP = "2_testgroup";
	
	/**
	 * The table to query
	 */
	private static final String TABLE = DISTRIBUTION_GROUP + "_mytable";
	
	/**
	 * The amount of operations
	 */
	private static final int NUMBER_OF_OPERATIONS = 10000;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(Selftest.class);


	public static void main(final String[] args) throws InterruptedException, ExecutionException, ScalephantException {
		
		if(args.length < 2) {
			logger.error("Usage: Selftest <Cluster-Name> <Cluster-Endpoint1> <Cluster-EndpointN>");
			System.exit(-1);
		}

		logger.info("Running selftest......");
		
		final String clustername = args[0];
		final Collection<String> endpoints = new ArrayList<String>();
		for(int i = 1; i < args.length; i++) {
			endpoints.add(args[i]);
		}
	
		final ScalephantCluster scalephantCluster = new ScalephantCluster(endpoints, clustername); 
		scalephantCluster.connect();
		
		if(! scalephantCluster.isConnected()) {
			logger.error("Connection could not be established");
			System.exit(-1);
		}
		
		logger.info("Connected to cluster: " + clustername);
		logger.info("With endpoint(s): " + endpoints);
		
		logger.info("Delete old distribution group: " + DISTRIBUTION_GROUP);
		final OperationFuture deleteFuture = scalephantCluster.deleteDistributionGroup(DISTRIBUTION_GROUP);
		deleteFuture.waitForAll();
		if(deleteFuture.isFailed()) {
			logger.error("Unable to delete distribution group: " + DISTRIBUTION_GROUP);
			System.exit(-1);
		}
		
		// Wait for distribution group to settle
		Thread.sleep(5000);
		
		logger.info("Create new distribution group: " + DISTRIBUTION_GROUP);
		final OperationFuture createFuture = scalephantCluster.createDistributionGroup(DISTRIBUTION_GROUP, (short) 2);
		createFuture.waitForAll();
		if(createFuture.isFailed()) {
			logger.error("Unable to create distribution group: " + DISTRIBUTION_GROUP);
			System.exit(-1);
		}
		
		// Wait for distribution group to appear
		Thread.sleep(5000);
		
		executeSelftest(scalephantCluster);
	}

	/**
	 * Execute the selftest
	 * @param scalephantClient
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws ScalephantException 
	 */
	private static void executeSelftest(final ScalephantCluster scalephantClient) throws InterruptedException, ExecutionException, ScalephantException {
		final Random random = new Random();
		long iteration = 1;
		
		while(true) {
			logger.info("Starting new iteration: " + iteration);
			insertNewTuples(scalephantClient);
			queryForExistingTuples(scalephantClient, random);
			deleteTuples(scalephantClient);
			queryForNonExistingTuples(scalephantClient);
			
			Thread.sleep(1000);
			
			iteration++;
		}
	}

	/**
	 * Delete the stored tuples
	 * @param scalephantClient
	 * @throws InterruptedException
	 * @throws ScalephantException 
	 * @throws ExecutionException 
	 */
	private static void deleteTuples(final ScalephantCluster scalephantClient) throws InterruptedException, ScalephantException, ExecutionException {
		logger.info("Deleting tuples");
		for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
			final String key = Integer.toString(i);
			final OperationFuture deletionResult = scalephantClient.deleteTuple(TABLE, key);
			final boolean result = deletionResult.waitForAll();
			
			if(! result) {
				logger.error("Got an error when deleting: key");
				System.exit(-1);
			}
		}
	}

	/**
	 * Query for the stored tuples
	 * @param scalephantClient
	 * @param random
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws ScalephantException 
	 */
	private static void queryForExistingTuples(final ScalephantCluster scalephantClient, final Random random) throws InterruptedException, ExecutionException, ScalephantException {
		logger.info("Query for tuples");
		for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
			final String key = Integer.toString(Math.abs(random.nextInt()) % NUMBER_OF_OPERATIONS);
			final OperationFuture queryResult = scalephantClient.queryKey(TABLE, key);
			queryResult.waitForAll();
			
			if(queryResult.isFailed()) {
				logger.error("Query " + i + ": Got failed future, when query for: " + i);
				System.exit(-1);
			}

			boolean tupleFound = false;
			
			for(int result = 0; result < queryResult.getNumberOfResultObjets(); result++) {
				if(queryResult.get(result) instanceof Tuple) {
					final Tuple resultTuple = (Tuple) queryResult.get(result);
					tupleFound = true;
					if(! resultTuple.getKey().equals(key)) {
						logger.error("Query " + i + ": Got tuple with wrong key.");
						logger.error("Expected: " + key + " but got: " + resultTuple.getKey());
						System.exit(-1);
					}
				}
			}
			
			if(tupleFound == false) {
				logger.error("Query " + i + ": Key " + key + " not found");
				logger.error("Number of result futures: " + queryResult.getNumberOfResultObjets());
				System.exit(-1);
			}
		}
	}
	
	/**
	 * Query for non existing tuples and exit, as soon as a tuple is found
	 * @param scalephantClient
	 * @throws ScalephantException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private static void queryForNonExistingTuples(final ScalephantCluster scalephantClient) throws ScalephantException, InterruptedException, ExecutionException {
		logger.info("Query for non existing tuples");
		
		for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
			final String key = Integer.toString(i);
			final OperationFuture queryResult = scalephantClient.queryKey(TABLE, key);
			queryResult.waitForAll();
			
			if(queryResult.isFailed()) {
				logger.error("Query " + i + ": Got failed future, when query for: " + i);
				System.exit(-1);
			}
			
			for(int result = 0; result < queryResult.getNumberOfResultObjets(); result++) {
				if(queryResult.get(result) instanceof Tuple) {
					final Tuple resultTuple = (Tuple) queryResult.get(result);
					logger.error("Found a tuple which should not exist: " + resultTuple);
					System.exit(-1);
				}
			}
		}
	}

	/**
	 * Insert new tuples
	 * @param scalephantClient
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws ScalephantException 
	 */
	private static void insertNewTuples(final ScalephantCluster scalephantClient) throws InterruptedException, ExecutionException, ScalephantException {
		logger.info("Inserting new tuples");
		
		for(int i = 0; i < NUMBER_OF_OPERATIONS; i++) {
			final String key = Integer.toString(i);
			final Tuple myTuple = new Tuple(key, new BoundingBox(1.0f, 2.0f, 1.0f, 2.0f), "test".getBytes());
			final OperationFuture insertResult = scalephantClient.insertTuple(TABLE, myTuple);
			insertResult.waitForAll();
			
			if(insertResult.isFailed()) {
				logger.error("Got an error during tuple insert");
				System.exit(-1);
			}
		}
	}
}
