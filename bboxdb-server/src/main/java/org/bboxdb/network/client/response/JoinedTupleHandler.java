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
package org.bboxdb.network.client.response;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bboxdb.network.client.BBoxDBConnection;
import org.bboxdb.network.client.future.NetworkOperationFuture;
import org.bboxdb.network.packages.PackageEncodeException;
import org.bboxdb.network.packages.response.JoinedTupleResponse;
import org.bboxdb.storage.entity.PagedTransferableEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoinedTupleHandler implements ServerResponseHandler {

	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(JoinedTupleHandler.class);

	/**
	 * Handle a single tuple as result
	 * @return 
	 */
	@Override
	public boolean handleServerResult(final BBoxDBConnection bBoxDBConnection, 
			final ByteBuffer encodedPackage, final NetworkOperationFuture future)
			throws PackageEncodeException {
		
		if(logger.isDebugEnabled()) {
			logger.debug("Handle joined tuple package");
		}
		
		final JoinedTupleResponse singleTupleResponse = JoinedTupleResponse.decodePackage(encodedPackage);
		final short sequenceNumber = singleTupleResponse.getSequenceNumber();
		
		// Tuple is part of a multi tuple result
		final Map<Short, List<PagedTransferableEntity>> resultBuffer = bBoxDBConnection.getResultBuffer();
		
		if(resultBuffer.containsKey(sequenceNumber)) {
			resultBuffer.get(sequenceNumber).add(singleTupleResponse.getJoinedTuple());
			
			// The removal of the future depends, if this is a one
			// tuple result or a multiple tuple result
			return false;
		}
		
		// Single tuple is returned
		if(future != null) {
			future.setOperationResult(Arrays.asList(singleTupleResponse.getJoinedTuple()));
			future.fireCompleteEvent();
		}
		
		return true;
	}

}
