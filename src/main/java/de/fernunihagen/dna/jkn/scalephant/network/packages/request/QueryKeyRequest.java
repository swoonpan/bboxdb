package de.fernunihagen.dna.jkn.scalephant.network.packages.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.jkn.scalephant.network.NetworkConst;
import de.fernunihagen.dna.jkn.scalephant.network.NetworkPackageDecoder;
import de.fernunihagen.dna.jkn.scalephant.network.NetworkPackageEncoder;
import de.fernunihagen.dna.jkn.scalephant.network.packages.NetworkQueryRequestPackage;

public class QueryKeyRequest implements NetworkQueryRequestPackage {
	
	/**
	 * The name of the table
	 */
	protected final String table;

	/**
	 * The name of the key
	 */
	protected final String key;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(QueryKeyRequest.class);
	
	public QueryKeyRequest(final String table, final String key) {
		this.table = table;
		this.key = key;
	}

	@Override
	public void writeToOutputStream(final short sequenceNumber,
			final OutputStream outputStream) {
		final NetworkPackageEncoder networkPackageEncoder 
			= new NetworkPackageEncoder();
	
		final ByteArrayOutputStream bos = networkPackageEncoder.getOutputStreamForRequestPackage(sequenceNumber, getPackageType());
		
		try {
			final byte[] tableBytes = table.getBytes();
			final byte[] keyBytes = key.getBytes();
			
			final ByteBuffer bb = ByteBuffer.allocate(5);
			bb.order(NetworkConst.NETWORK_BYTEORDER);
			
			bb.put(getQueryType());
			bb.putShort((short) tableBytes.length);
			bb.putShort((short) keyBytes.length);
			
			// Write body length
			final long bodyLength = bb.capacity() + tableBytes.length + keyBytes.length;
			
			final ByteBuffer bodyLengthBuffer = ByteBuffer.allocate(8);
			bodyLengthBuffer.order(NetworkConst.NETWORK_BYTEORDER);
			bodyLengthBuffer.putLong(bodyLength);
			bos.write(bodyLengthBuffer.array());
			
			// Write body
			bos.write(bb.array());
			bos.write(tableBytes);
			bos.write(keyBytes);
			bos.close();
			
			final byte[] outputData = bos.toByteArray();
			outputStream.write(outputData, 0, outputData.length);
		} catch (IOException e) {
			logger.error("Got exception while converting package into bytes", e);
		}	
	}
	
	/**
	 * Decode the encoded package into a object
	 * 
	 * @param encodedPackage
	 * @return
	 */
	public static QueryKeyRequest decodeTuple(final ByteBuffer encodedPackage) {
		final boolean decodeResult = NetworkPackageDecoder.validateRequestPackageHeader(encodedPackage, NetworkConst.REQUEST_TYPE_QUERY);
		
		if(decodeResult == false) {
			logger.warn("Unable to decode package");
			return null;
		}
		
	    final byte queryType = encodedPackage.get();
	    
	    if(queryType != NetworkConst.REQUEST_QUERY_KEY) {
	    	logger.error("Wrong query type: " + queryType);
	    	return null;
	    }
		
		final short tableLength = encodedPackage.getShort();
		final short keyLength = encodedPackage.getShort();
		
		final byte[] tableBytes = new byte[tableLength];
		encodedPackage.get(tableBytes, 0, tableBytes.length);
		final String table = new String(tableBytes);
		
		final byte[] keyBytes = new byte[keyLength];
		encodedPackage.get(keyBytes, 0, keyBytes.length);
		final String key = new String(keyBytes);
		
		if(encodedPackage.remaining() != 0) {
			logger.error("Some bytes are left after encoding: " + encodedPackage.remaining());
		}
		
		return new QueryKeyRequest(table, key);
	}

	@Override
	public byte getPackageType() {
		return NetworkConst.REQUEST_TYPE_QUERY;
	}

	@Override
	public byte getQueryType() {
		return NetworkConst.REQUEST_QUERY_KEY;
	}
	
	public String getTable() {
		return table;
	}

	public String getKey() {
		return key;
	}

}
