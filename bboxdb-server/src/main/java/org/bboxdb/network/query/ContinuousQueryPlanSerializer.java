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
package org.bboxdb.network.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bboxdb.commons.InputParseException;
import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.network.query.transformation.BoundingBoxFilterTransformation;
import org.bboxdb.network.query.transformation.EnlargeBoundingBoxByAmountTransformation;
import org.bboxdb.network.query.transformation.EnlargeBoundingBoxByFactorTransformation;
import org.bboxdb.network.query.transformation.EnlargeBoundingBoxByWGS84Transformation;
import org.bboxdb.network.query.transformation.KeyFilterTransformation;
import org.bboxdb.network.query.transformation.TupleTransformation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ContinuousQueryPlanSerializer {
	
	/**
	 * Query plan key and value
	 */
	private static final String TYPE_KEY = "type";
	private static final String TYPE_VALUE = "query-plan";

	/**
	 * Query type key and values
	 */
	private static final String QUERY_TYPE_KEY = "query-type";
	private static final String QUERY_TYPE_TABLE_VALUE = "table-query";
	private static final String QUERY_TYPE_CONST_VALUE = "const-query";
	
	/**
	 * Misc keys
	 */
	private static final String TABLE_TRANSFORMATIONS_KEY = "table-transformations";
	private static final String STREAM_TRANSFORMATIONS_KEY = "stream-transformations";
	private static final String REPORT_KEY = "report-positive";
	private static final String STREAM_TABLE_KEY = "stream-table";
	private static final String JOIN_TABLE_KEY = "join-table";
	private static final String QUERY_RANGE_KEY = "query-range";
	private static final String COMPARE_RECTANGLE_KEY = "compare-rectangle";
	
	/**
	 * Transformation type
	 */
	private static final String TRANSFORMATION_NAME_KEY = "name";
	private static final String TRANSFORMATION_KEY_FILTER_VALUE = "key-filter";
	private static final String TRANSFORMATION_BBOX_ENLARGE_AMOUNT_VALUE = "bbox-enlarge-by-amount";
	private static final String TRANSFORMATION_BBOX_ENLARGE_FACTOR_VALUE = "bbox-enlarge-by-factor";
	private static final String TRANSFORMATION_BBOX_ENLARGE_WGS84_VALUE = "bbox-enlarge-by-wgs84";
	private static final String TRANSFORMATION_BBOX_FILTER_VALUE = "bbox-filter";
	
	/**
	 * Transformation value
	 */
	private static final String TRANSFORMATION_VALUE_KEY = "value";
	
	/**
	 * Serialize to JSON
	 * @param queryPlan
	 * @return
	 */
	public static String toJSON(final ContinuousQueryPlan queryPlan) {
		final JSONObject json = new JSONObject();
		json.put(TYPE_KEY, TYPE_VALUE);
		
		if(queryPlan instanceof ContinuousConstQueryPlan) {
			json.put(QUERY_TYPE_KEY, QUERY_TYPE_CONST_VALUE);
			
			final ContinuousConstQueryPlan constQueryPlan = (ContinuousConstQueryPlan) queryPlan;
			json.put(COMPARE_RECTANGLE_KEY, constQueryPlan.getCompareRectangle().toCompactString());
		} else if(queryPlan instanceof ContinuousTableQueryPlan) {
			json.put(QUERY_TYPE_KEY, QUERY_TYPE_TABLE_VALUE);

			final ContinuousTableQueryPlan tableQueryPlan = (ContinuousTableQueryPlan) queryPlan;
			final List<TupleTransformation> transformations = tableQueryPlan.getTableTransformation();
			final JSONArray tableTransformations = writeTransformationsToJSON(json, transformations);
			json.put(TABLE_TRANSFORMATIONS_KEY, tableTransformations);
			json.put(JOIN_TABLE_KEY, tableQueryPlan.getJoinTable());					
		} else {
			throw new IllegalArgumentException("Unknown query type: " + queryPlan);
		}
		
		json.put(QUERY_RANGE_KEY, queryPlan.getQueryRange().toCompactString());
		json.put(STREAM_TABLE_KEY, queryPlan.getStreamTable());
		json.put(REPORT_KEY, queryPlan.isReportPositive());
		
		final List<TupleTransformation> transformations = queryPlan.getStreamTransformation();
		final JSONArray streamTransformations = writeTransformationsToJSON(json, transformations);
		json.put(STREAM_TRANSFORMATIONS_KEY, streamTransformations);

		return json.toString();
	}

	/**
	 * Write the transformations into a JSON array
	 * @param json
	 * @param transformations
	 * @return 
	 */
	private static JSONArray writeTransformationsToJSON(final JSONObject json,
			final List<TupleTransformation> transformations) {
		
		final JSONArray transfomationArray = new JSONArray();
		
		for(final TupleTransformation transformation : transformations) {
			final JSONObject transformationJSON = new JSONObject();
			
			if(transformation instanceof BoundingBoxFilterTransformation) {
				transformationJSON.put(TRANSFORMATION_NAME_KEY, TRANSFORMATION_BBOX_FILTER_VALUE);
			} else if(transformation instanceof EnlargeBoundingBoxByAmountTransformation) {
				transformationJSON.put(TRANSFORMATION_NAME_KEY, TRANSFORMATION_BBOX_ENLARGE_AMOUNT_VALUE);
			} else if(transformation instanceof EnlargeBoundingBoxByFactorTransformation) {
				transformationJSON.put(TRANSFORMATION_NAME_KEY, TRANSFORMATION_BBOX_ENLARGE_FACTOR_VALUE);
			} else if(transformation instanceof KeyFilterTransformation) {
				transformationJSON.put(TRANSFORMATION_NAME_KEY, TRANSFORMATION_KEY_FILTER_VALUE);
			} else if(transformation instanceof EnlargeBoundingBoxByWGS84Transformation) {
				transformationJSON.put(TRANSFORMATION_NAME_KEY, TRANSFORMATION_BBOX_ENLARGE_WGS84_VALUE);
			} else {
				throw new IllegalArgumentException("Unable to serialize type: " + transformation);
			}
			
			transformationJSON.put(TRANSFORMATION_VALUE_KEY, transformation.getSerializedData());
			
			transfomationArray.put(transformationJSON);
		}
		
		return transfomationArray;
	}
	
	/**
	 * Deserialize query plan
	 * @param json
	 * @return
	 * @throws BBoxDBException 
	 */
	public static ContinuousQueryPlan fromJSON(final String jsonString) throws BBoxDBException {
		
		Objects.requireNonNull(jsonString);
		
		try {
			final JSONObject json = new JSONObject(jsonString);
			
			if(! json.getString(TYPE_KEY).equals(TYPE_VALUE)) {
				throw new BBoxDBException("JSON does not contain a valid query plan: " + jsonString);
			}
			
			final String queryType = json.getString(QUERY_TYPE_KEY);
			final String streamTable = json.getString(STREAM_TABLE_KEY);
			final Hyperrectangle queryRectangle = Hyperrectangle.fromString(json.getString(QUERY_RANGE_KEY));
			final boolean reportPositiveNegative = json.getBoolean(REPORT_KEY);
			
			final List<TupleTransformation> streamTransformation 
				= decodeTransformation(json, STREAM_TRANSFORMATIONS_KEY);
			
			switch(queryType) {
			case QUERY_TYPE_CONST_VALUE:
				final Hyperrectangle compareRectangle = Hyperrectangle.fromString(json.getString(COMPARE_RECTANGLE_KEY));
				
				final ContinuousConstQueryPlan constQuery = new ContinuousConstQueryPlan(streamTable, 
						streamTransformation, queryRectangle, compareRectangle, reportPositiveNegative);
				
				return constQuery;
			case QUERY_TYPE_TABLE_VALUE:
				final List<TupleTransformation> tableTransformation 
					= decodeTransformation(json, TABLE_TRANSFORMATIONS_KEY);
				
				final String joinTable = json.getString(JOIN_TABLE_KEY);
				
				final ContinuousTableQueryPlan tableQuery = new ContinuousTableQueryPlan(streamTable, 
						joinTable, streamTransformation, queryRectangle, 
						tableTransformation, reportPositiveNegative);
		
				return tableQuery;
			default:
				throw new BBoxDBException("Unknown query type: " + queryType);
			}
			
		} catch(JSONException e) {
			throw new BBoxDBException("Unable to handle json: " + jsonString, e);
		}		
	}
	
	/**
	 * Decode the given transformations
	 * @param json
	 * @param key
	 * @return
	 * @throws BBoxDBException 
	 */
	private static List<TupleTransformation> decodeTransformation(final JSONObject json, final String key) 
			throws BBoxDBException {
		
		final List<TupleTransformation> transformations = new ArrayList<>();
		
		final JSONArray transformationArray = json.getJSONArray(key);
		
		for(int i = 0; i < transformationArray.length(); i++) {
			final JSONObject transformationObject = transformationArray.getJSONObject(i);
			final String transformationType = transformationObject.getString(TRANSFORMATION_NAME_KEY);
			final String transformationValue = transformationObject.getString(TRANSFORMATION_VALUE_KEY);
			
			try {
				TupleTransformation transformation;
				
				switch(transformationType) {
					case TRANSFORMATION_BBOX_ENLARGE_AMOUNT_VALUE:
						transformation = new EnlargeBoundingBoxByAmountTransformation(transformationValue);
						break;
					case TRANSFORMATION_BBOX_ENLARGE_FACTOR_VALUE:
						transformation = new EnlargeBoundingBoxByFactorTransformation(transformationValue);
						break;
					case TRANSFORMATION_BBOX_FILTER_VALUE:
						transformation = new BoundingBoxFilterTransformation(transformationValue);
						break;
					case TRANSFORMATION_KEY_FILTER_VALUE:
						transformation = new KeyFilterTransformation(transformationValue);
						break;
					case TRANSFORMATION_BBOX_ENLARGE_WGS84_VALUE:
						transformation = new EnlargeBoundingBoxByWGS84Transformation(transformationValue);
						break;
						
					default:
						throw new BBoxDBException("Unkown transformation type: " + transformationType);
				}
				
				transformations.add(transformation);
			} catch (InputParseException e) {
				throw new BBoxDBException(e);
			}
		}
		
		return transformations;
	}
	
}
