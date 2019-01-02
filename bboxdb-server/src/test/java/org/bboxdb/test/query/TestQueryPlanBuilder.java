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
package org.bboxdb.test.query;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.network.query.ContinuousConstQueryPlan;
import org.bboxdb.network.query.ContinuousQueryPlan;
import org.bboxdb.network.query.ContinuousTableQueryPlan;
import org.bboxdb.network.query.QueryPlanBuilder;
import org.junit.Assert;
import org.junit.Test;

public class TestQueryPlanBuilder {
	
	@Test(timeout=60_000, expected=IllegalArgumentException.class)
	public void testInvalidPlan() {
		QueryPlanBuilder
			.createQueryOnTable("table")
			.forAllTuplesStoredInRegion(3d, 4d)
			.compareWithStaticRegion(2d, 4d)
			.filterStreamTupleByBoundingBox(4d, 5d)
			.enlargeStreamTupleBoundBoxByAmount(4)
			.enlargeStreamTupleBoundBoxByFactor(2)
			.enlargeStoredTupleBoundBoxByAmount(4)
			.build();	
	}

	@Test(timeout=60_000)
	public void testConstPlan1() {
		final ContinuousQueryPlan queryPlan = QueryPlanBuilder
			.createQueryOnTable("table")
			.forAllTuplesStoredInRegion(3d, 4d)
			.compareWithStaticRegion(2d, 4d)
			.build();
		
		Assert.assertEquals("table", queryPlan.getStreamTable());
		Assert.assertTrue(queryPlan.isReportPositive());
		Assert.assertEquals(new Hyperrectangle(3d, 4d), queryPlan.getQueryRange());
		Assert.assertTrue(queryPlan.getStreamTransformation().isEmpty());
		
		final ContinuousConstQueryPlan cqp = (ContinuousConstQueryPlan) queryPlan;
		Assert.assertEquals(new Hyperrectangle(2d, 4d), cqp.getCompareRectangle());
	}
	
	@Test(timeout=60_000)
	public void testConstPlan2() {
		final ContinuousQueryPlan queryPlan = QueryPlanBuilder
			.createQueryOnTable("table")
			.forAllTuplesStoredInRegion(3d, 4d)
			.compareWithStaticRegion(2d, 4d)
			.filterStreamTupleByBoundingBox(3d, 5d)
			.enlargeStreamTupleBoundBoxByAmount(4)
			.enlargeStreamTupleBoundBoxByFactor(2)
			.build();
		
		Assert.assertEquals("table", queryPlan.getStreamTable());
		Assert.assertTrue(queryPlan.isReportPositive());
		Assert.assertEquals(new Hyperrectangle(3d, 4d), queryPlan.getQueryRange());
		Assert.assertEquals(3, queryPlan.getStreamTransformation().size());
		
		final ContinuousConstQueryPlan cqp = (ContinuousConstQueryPlan) queryPlan;
		Assert.assertEquals(new Hyperrectangle(2d, 4d), cqp.getCompareRectangle());
	}
	
	@Test(timeout=60_000)
	public void testTablePlan1() {
		final ContinuousQueryPlan queryPlan = QueryPlanBuilder
			.createQueryOnTable("table")
			.forAllTuplesStoredInRegion(3d, 4d)
			.filterStreamTupleByBoundingBox(1d, 5d)
			.enlargeStreamTupleBoundBoxByAmount(4)
			.enlargeStreamTupleBoundBoxByFactor(2)
			.reportPositiveMatches(false)
			.build();
		
		Assert.assertEquals("table", queryPlan.getStreamTable());
		Assert.assertFalse(queryPlan.isReportPositive());
		Assert.assertEquals(new Hyperrectangle(3d, 4d), queryPlan.getQueryRange());
		Assert.assertEquals(3, queryPlan.getStreamTransformation().size());
		
		final ContinuousTableQueryPlan cqp = (ContinuousTableQueryPlan) queryPlan;
		Assert.assertTrue(cqp.getTableTransformation().isEmpty());
	}
	
	@Test(timeout=60_000)
	public void testTablePlan2() {
		final ContinuousQueryPlan queryPlan = QueryPlanBuilder
			.createQueryOnTable("table")
			.forAllTuplesStoredInRegion(3d, 4d)
			.filterStreamTupleByBoundingBox(3d, 5d)
			.filterStoredTupleByBoundingBox(3d, 6d)
			.enlargeStreamTupleBoundBoxByAmount(4)
			.enlargeStreamTupleBoundBoxByFactor(2)
			.enlargeStoredTupleBoundBoxByAmount(2)
			.filterStoredTupleByKey("abc")
			.filterStreamTupleByKey("def")
			.build();
		
		Assert.assertEquals("table", queryPlan.getStreamTable());
		Assert.assertEquals(new Hyperrectangle(3d, 4d), queryPlan.getQueryRange());
		Assert.assertEquals(4, queryPlan.getStreamTransformation().size());
		
		final ContinuousTableQueryPlan cqp = (ContinuousTableQueryPlan) queryPlan;
		Assert.assertEquals(3, cqp.getTableTransformation().size());
	}
}